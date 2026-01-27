package br.com.felixgilioli.videoprocessorworker.config

import br.com.felixgilioli.videoprocessorworker.config.properties.SqsProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsClient
import java.net.URI

@Configuration
@EnableConfigurationProperties(SqsProperties::class)
class SqsConfig(private val properties: SqsProperties) {

    @Bean
    fun sqsClient(): SqsClient = SqsClient.builder()
        .endpointOverride(URI.create(properties.endpoint))
        .region(Region.of(properties.region))
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKey, properties.secretKey)
            )
        )
        .build()
}