package br.com.felixgilioli.videoprocessorworker.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "storage")
data class S3Properties(
    val endpoint: String,
    val accessKey: String,
    val secretKey: String,
    val bucket: String
)