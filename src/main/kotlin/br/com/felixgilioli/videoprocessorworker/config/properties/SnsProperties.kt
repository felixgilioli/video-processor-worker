package br.com.felixgilioli.videoprocessorworker.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "sns")
data class SnsProperties(
    val endpoint: String,
    val region: String,
    val accessKey: String,
    val secretKey: String,
    val videoCompletedTopic: String
)