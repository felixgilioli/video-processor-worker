package br.com.felixgilioli.videoprocessorworker.service

import br.com.felixgilioli.videoprocessorworker.config.properties.SnsProperties
import br.com.felixgilioli.videoprocessorworker.dto.VideoCompletedEvent
import io.opentelemetry.api.trace.Span
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sns.SnsClient
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import tools.jackson.databind.ObjectMapper

@Service
class NotificationService(
    private val snsClient: SnsClient,
    private val properties: SnsProperties,
    private val objectMapper: ObjectMapper
) {

    fun publishVideoCompleted(event: VideoCompletedEvent) {
        val span = Span.current()
        val traceparent = "00-${span.spanContext.traceId}-${span.spanContext.spanId}-01"

        snsClient.publish {
            it.topicArn(getTopicArn())
                .message(objectMapper.writeValueAsString(event))
                .messageAttributes(
                    mapOf(
                        "traceparent" to MessageAttributeValue.builder()
                            .dataType("String")
                            .stringValue(traceparent)
                            .build()
                    )
                )
        }
    }

    private fun getTopicArn(): String =
        snsClient.listTopics().topics()
            .first { it.topicArn().contains(properties.videoCompletedTopic) }
            .topicArn()
}