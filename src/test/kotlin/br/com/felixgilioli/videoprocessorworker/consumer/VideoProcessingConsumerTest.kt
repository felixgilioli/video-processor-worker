package br.com.felixgilioli.videoprocessorworker.consumer

import br.com.felixgilioli.videoprocessorworker.config.properties.SqsProperties
import br.com.felixgilioli.videoprocessorworker.dto.VideoCompletedEvent
import br.com.felixgilioli.videoprocessorworker.dto.VideoProcessingMessage
import br.com.felixgilioli.videoprocessorworker.service.FrameExtractionResult
import br.com.felixgilioli.videoprocessorworker.service.FrameExtractorService
import br.com.felixgilioli.videoprocessorworker.service.NotificationService
import br.com.felixgilioli.videoprocessorworker.service.StorageService
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Scope
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest
import software.amazon.awssdk.services.sqs.model.DeleteMessageResponse
import software.amazon.awssdk.services.sqs.model.Message
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse
import tools.jackson.databind.ObjectMapper
import java.util.UUID
import java.util.function.Consumer
import kotlin.test.Test

class VideoProcessingConsumerTest {

    private val sqsClient: SqsClient = mock()
    private val storageService: StorageService = mock()
    private val frameExtractorService: FrameExtractorService = mock()
    private val notificationService: NotificationService = mock()
    private val objectMapper: ObjectMapper = mock()
    private val tracer: Tracer = mock()

    private val sqsProperties = SqsProperties(
        endpoint = "http://localhost:4566",
        region = "us-east-1",
        accessKey = "test",
        secretKey = "test",
        videoProcessingQueue = "video-processing-queue"
    )

    private val consumer = VideoProcessingConsumer(
        sqsClient, sqsProperties, storageService, frameExtractorService, notificationService, objectMapper, tracer
    )

    private fun stubSpanChain(): Span {
        val spanBuilder: SpanBuilder = mock()
        val span: Span = mock()
        val scope: Scope = mock()

        whenever(tracer.spanBuilder(any())).thenReturn(spanBuilder)
        whenever(spanBuilder.setParent(any())).thenReturn(spanBuilder)
        whenever(spanBuilder.startSpan()).thenReturn(span)
        whenever(span.makeCurrent()).thenReturn(scope)
        whenever(span.setAttribute(any<String>(), any<String>())).thenReturn(span)

        return span
    }

    private fun stubReceiveMessages(vararg messages: Message) {
        val response: ReceiveMessageResponse = mock()
        whenever(sqsClient.receiveMessage(any<Consumer<ReceiveMessageRequest.Builder>>())).thenReturn(response)
        whenever(response.messages()).thenReturn(messages.toList())
    }

    private fun buildMessage(body: String, traceparent: String? = null): Message {
        val message: Message = mock()
        whenever(message.body()).thenReturn(body)
        val attributes = if (traceparent != null) {
            mapOf("traceparent" to MessageAttributeValue.builder().dataType("String").stringValue(traceparent).build())
        } else {
            emptyMap()
        }
        whenever(message.messageAttributes()).thenReturn(attributes)
        whenever(message.receiptHandle()).thenReturn("receipt-handle-123")
        return message
    }

    @Test
    fun `poll should do nothing when no messages received`() {
        stubReceiveMessages()

        consumer.poll()

        verifyNoInteractions(storageService, frameExtractorService, notificationService, tracer)
    }

    @Test
    fun `poll should process video and publish READY event on success`() {
        val videoId = UUID.randomUUID()
        val userId = "user1"
        val videoUrl = "http://localhost:9000/videos/test-folder/video.mp4"
        val payload = VideoProcessingMessage(videoId, userId, videoUrl)
        val videoBytes = "video-bytes".toByteArray()
        val zipBytes = "zip-bytes".toByteArray()
        val firstFrameBytes = "frame-bytes".toByteArray()
        val zipUrl = "http://localhost:9000/videos/zips/$videoId/frames.zip"
        val firstFrameUrl = "http://localhost:9000/videos/frames/$videoId/first_frame.jpg"

        stubSpanChain()
        stubReceiveMessages(buildMessage("""{"videoId":"$videoId","userId":"$userId","videoUrl":"$videoUrl"}"""))

        whenever(objectMapper.readValue(any<String>(), eq(VideoProcessingMessage::class.java))).thenReturn(payload)
        whenever(storageService.download("test-folder/video.mp4")).thenReturn(videoBytes)
        whenever(frameExtractorService.extractFramesAndZip(videoBytes, videoId))
            .thenReturn(FrameExtractionResult(zipBytes, firstFrameBytes))
        whenever(storageService.upload("zips/$videoId/frames.zip", zipBytes, "application/zip")).thenReturn(zipUrl)
        whenever(storageService.upload("frames/$videoId/first_frame.jpg", firstFrameBytes, "image/jpeg")).thenReturn(firstFrameUrl)
        whenever(sqsClient.deleteMessage(any<Consumer<DeleteMessageRequest.Builder>>())).thenReturn(mock<DeleteMessageResponse>())

        consumer.poll()

        verify(storageService).download("test-folder/video.mp4")
        verify(frameExtractorService).extractFramesAndZip(videoBytes, videoId)
        verify(storageService).upload("zips/$videoId/frames.zip", zipBytes, "application/zip")
        verify(storageService).upload("frames/$videoId/first_frame.jpg", firstFrameBytes, "image/jpeg")
        verify(notificationService).publishVideoCompleted(
            VideoCompletedEvent(videoId, userId, "READY", zipUrl, firstFrameUrl)
        )
        verify(sqsClient).deleteMessage(any<Consumer<DeleteMessageRequest.Builder>>())
    }

    @Test
    fun `poll should publish FAILED event and NOT delete message on error`() {
        val span = stubSpanChain()
        val message = buildMessage("""{"videoId":"invalid"}""")
        stubReceiveMessages(message)

        val exception = RuntimeException("FFmpeg failed")
        whenever(objectMapper.readValue(any<String>(), eq(VideoProcessingMessage::class.java))).thenThrow(exception)

        consumer.poll()

        verify(notificationService).publishVideoCompleted(argThat {
            status == "FAILED" && zipUrl == null && firstFrameUrl == null && userId == ""
        })
        verify(sqsClient, never()).deleteMessage(any<Consumer<DeleteMessageRequest.Builder>>())
        verify(span).recordException(exception)
        verify(span).setStatus(StatusCode.ERROR, "FFmpeg failed")
        verify(span).end()
    }

    @Test
    fun `poll should always end span on success`() {
        val videoId = UUID.randomUUID()
        val userId = "user1"
        val videoUrl = "http://localhost:9000/videos/folder/video.mp4"
        val payload = VideoProcessingMessage(videoId, userId, videoUrl)

        val span = stubSpanChain()
        stubReceiveMessages(buildMessage("""{}"""))

        whenever(objectMapper.readValue(any<String>(), eq(VideoProcessingMessage::class.java))).thenReturn(payload)
        whenever(storageService.download(any())).thenReturn(ByteArray(0))
        whenever(frameExtractorService.extractFramesAndZip(any(), any()))
            .thenReturn(FrameExtractionResult(ByteArray(0), ByteArray(0)))
        whenever(storageService.upload(any(), any(), eq("application/zip"))).thenReturn("http://zip")
        whenever(storageService.upload(any(), any(), eq("image/jpeg"))).thenReturn("http://frame")
        whenever(sqsClient.deleteMessage(any<Consumer<DeleteMessageRequest.Builder>>())).thenReturn(mock<DeleteMessageResponse>())

        consumer.poll()

        verify(span).end()
    }

    @Test
    fun `poll should propagate traceparent from message attributes`() {
        val videoId = UUID.randomUUID()
        val userId = "user2"
        val videoUrl = "http://localhost:9000/videos/folder/video.mp4"
        val payload = VideoProcessingMessage(videoId, userId, videoUrl)
        val traceparent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"

        val span = stubSpanChain()
        stubReceiveMessages(buildMessage("""{}""", traceparent = traceparent))

        whenever(objectMapper.readValue(any<String>(), eq(VideoProcessingMessage::class.java))).thenReturn(payload)
        whenever(storageService.download(any())).thenReturn(ByteArray(0))
        whenever(frameExtractorService.extractFramesAndZip(any(), any()))
            .thenReturn(FrameExtractionResult(ByteArray(0), ByteArray(0)))
        whenever(storageService.upload(any(), any(), eq("application/zip"))).thenReturn("http://zip")
        whenever(storageService.upload(any(), any(), eq("image/jpeg"))).thenReturn("http://frame")
        whenever(sqsClient.deleteMessage(any<Consumer<DeleteMessageRequest.Builder>>())).thenReturn(mock<DeleteMessageResponse>())

        consumer.poll()

        verify(tracer).spanBuilder("process-video")
        verify(span).end()
    }
}
