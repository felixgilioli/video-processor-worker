package br.com.felixgilioli.videoprocessorworker.service

import br.com.felixgilioli.videoprocessorworker.config.properties.SnsProperties
import br.com.felixgilioli.videoprocessorworker.dto.VideoCompletedEvent
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertFailsWith
import software.amazon.awssdk.services.sns.SnsClient
import software.amazon.awssdk.services.sns.model.ListTopicsResponse
import software.amazon.awssdk.services.sns.model.PublishRequest
import software.amazon.awssdk.services.sns.model.PublishResponse
import software.amazon.awssdk.services.sns.model.Topic
import tools.jackson.databind.ObjectMapper
import java.util.UUID
import java.util.function.Consumer
import kotlin.test.Test

class NotificationServiceTest {

    private val snsClient: SnsClient = mock()
    private val objectMapper: ObjectMapper = mock()
    private val properties = SnsProperties(
        endpoint = "http://localhost:4566",
        region = "us-east-1",
        accessKey = "test",
        secretKey = "test",
        videoCompletedTopic = "video-completed-topic"
    )

    private val notificationService = NotificationService(snsClient, properties, objectMapper)

    private val topicArn = "arn:aws:sns:us-east-1:000000000000:video-completed-topic"

    private fun stubSnsListTopics() {
        val topic: Topic = mock()
        val listResponse: ListTopicsResponse = mock()
        whenever(snsClient.listTopics()).thenReturn(listResponse)
        whenever(listResponse.topics()).thenReturn(listOf(topic))
        whenever(topic.topicArn()).thenReturn(topicArn)
    }

    @Test
    fun `publishVideoCompleted should call publish on SNS for READY event`() {
        val event = VideoCompletedEvent(
            videoId = UUID.randomUUID(),
            userId = "user1",
            status = "READY",
            zipUrl = "http://localhost:9000/videos/zips/123/frames.zip",
            firstFrameUrl = "http://localhost:9000/videos/frames/123/first_frame.jpg"
        )
        stubSnsListTopics()
        whenever(objectMapper.writeValueAsString(any())).thenReturn("{}")
        whenever(snsClient.publish(any<Consumer<PublishRequest.Builder>>())).thenReturn(mock<PublishResponse>())

        notificationService.publishVideoCompleted(event)

        verify(snsClient).publish(any<Consumer<PublishRequest.Builder>>())
    }

    @Test
    fun `publishVideoCompleted should call publish on SNS for FAILED event`() {
        val event = VideoCompletedEvent(
            videoId = UUID.randomUUID(),
            userId = "",
            status = "FAILED",
            zipUrl = null,
            firstFrameUrl = null
        )
        stubSnsListTopics()
        whenever(objectMapper.writeValueAsString(any())).thenReturn("{}")
        whenever(snsClient.publish(any<Consumer<PublishRequest.Builder>>())).thenReturn(mock<PublishResponse>())

        notificationService.publishVideoCompleted(event)

        verify(snsClient).publish(any<Consumer<PublishRequest.Builder>>())
    }

    @Test
    fun `publishVideoCompleted should propagate exception when SNS publish fails`() {
        val event = VideoCompletedEvent(
            videoId = UUID.randomUUID(),
            userId = "user1",
            status = "READY",
            zipUrl = "http://example.com/zip",
            firstFrameUrl = "http://example.com/frame"
        )
        stubSnsListTopics()
        whenever(objectMapper.writeValueAsString(any())).thenReturn("{}")
        whenever(snsClient.publish(any<Consumer<PublishRequest.Builder>>())).thenThrow(RuntimeException("SNS unavailable"))

        assertFailsWith<RuntimeException> {
            notificationService.publishVideoCompleted(event)
        }
    }
}
