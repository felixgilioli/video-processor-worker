package br.com.felixgilioli.videoprocessorworker.service

import br.com.felixgilioli.videoprocessorworker.config.properties.SnsProperties
import br.com.felixgilioli.videoprocessorworker.dto.VideoCompletedEvent
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sns.SnsClient
import tools.jackson.databind.ObjectMapper

@Service
class NotificationService(
    private val snsClient: SnsClient,
    private val properties: SnsProperties,
    private val objectMapper: ObjectMapper
) {

    fun publishVideoCompleted(event: VideoCompletedEvent) {
        snsClient.publish {
            it.topicArn(getTopicArn())
                .message(objectMapper.writeValueAsString(event))
        }
    }

    private fun getTopicArn(): String =
        snsClient.listTopics().topics()
            .first { it.topicArn().contains(properties.videoCompletedTopic) }
            .topicArn()
}