package br.com.felixgilioli.videoprocessorworker.consumer

import br.com.felixgilioli.videoprocessorworker.config.properties.SqsProperties
import br.com.felixgilioli.videoprocessorworker.dto.VideoCompletedEvent
import br.com.felixgilioli.videoprocessorworker.dto.VideoProcessingMessage
import br.com.felixgilioli.videoprocessorworker.service.FrameExtractorService
import br.com.felixgilioli.videoprocessorworker.service.NotificationService
import br.com.felixgilioli.videoprocessorworker.service.StorageService
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.Context
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.Message
import tools.jackson.databind.ObjectMapper
import java.util.*

@Component
class VideoProcessingConsumer(
    private val sqsClient: SqsClient,
    private val sqsProperties: SqsProperties,
    private val storageService: StorageService,
    private val frameExtractorService: FrameExtractorService,
    private val notificationService: NotificationService,
    private val objectMapper: ObjectMapper,
    private val tracer: Tracer
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 5000)
    fun poll() {
        val messages = sqsClient.receiveMessage {
            it.queueUrl(getQueueUrl())
                .maxNumberOfMessages(5)
                .waitTimeSeconds(10)
                .messageAttributeNames("traceparent")
        }.messages()

        messages.forEach { process(it) }
    }

    private fun process(message: Message) {
        val traceparent = message.messageAttributes()["traceparent"]?.stringValue()
        val context = extractContextFromTraceparent(traceparent)

        val span = tracer.spanBuilder("process-video")
            .setParent(context)
            .startSpan()

        try {
            span.makeCurrent().use {
                val payload = objectMapper.readValue(message.body(), VideoProcessingMessage::class.java)
                span.setAttribute("videoId", payload.videoId.toString())
                logger.info("Processando vídeo: ${payload.videoId}")

                val key = extractKeyFromUrl(payload.videoUrl)
                val videoBytes = storageService.download(key)
                val zipBytes = frameExtractorService.extractFramesAndZip(videoBytes, payload.videoId)
                val zipUrl = storageService.upload("zips/${payload.videoId}/frames.zip", zipBytes, "application/zip")

                notificationService.publishVideoCompleted(
                    VideoCompletedEvent(
                        videoId = payload.videoId,
                        userId = payload.userId,
                        status = "READY",
                        zipUrl = zipUrl
                    )
                )

                deleteMessage(message)
                logger.info("Vídeo processado: ${payload.videoId}")
            }
        } catch (e: Exception) {
            span.recordException(e)
            span.setStatus(StatusCode.ERROR, e.message ?: "Erro desconhecido")
            logger.error("Erro ao processar mensagem", e)
            notificationService.publishVideoCompleted(
                VideoCompletedEvent(
                    videoId = UUID.randomUUID(),
                    userId = "",
                    status = "FAILED",
                    zipUrl = null
                )
            )
        } finally {
            span.end()
        }
    }

    private fun extractContextFromTraceparent(traceparent: String?): Context {
        if (traceparent == null) return Context.current()

        val propagator = W3CTraceContextPropagator.getInstance()
        val carrier = mapOf("traceparent" to traceparent)

        val getter = object : io.opentelemetry.context.propagation.TextMapGetter<Map<String, String>> {
            override fun keys(carrier: Map<String, String>): Iterable<String> = carrier.keys
            override fun get(carrier: Map<String, String>?, key: String): String? = carrier?.get(key)
        }

        return propagator.extract(Context.current(), carrier, getter)
    }

    private fun extractKeyFromUrl(url: String): String =
        url.substringAfter("/videos/").let { "videos/$it" }

    private fun getQueueUrl(): String =
        sqsClient.getQueueUrl { it.queueName(sqsProperties.videoProcessingQueue) }.queueUrl()

    private fun deleteMessage(message: Message) {
        sqsClient.deleteMessage { it.queueUrl(getQueueUrl()).receiptHandle(message.receiptHandle()) }
    }
}