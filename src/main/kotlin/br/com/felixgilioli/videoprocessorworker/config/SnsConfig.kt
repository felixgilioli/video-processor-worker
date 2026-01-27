package br.com.felixgilioli.videoprocessorworker.config

import br.com.felixgilioli.videoprocessorworker.config.properties.SnsProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sns.SnsClient
import java.net.URI

@Configuration
@EnableConfigurationProperties(SnsProperties::class)
class SnsConfig(private val properties: SnsProperties) {

    @Bean
    fun snsClient(): SnsClient = SnsClient.builder()
        .endpointOverride(URI.create(properties.endpoint))
        .region(Region.of(properties.region))
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKey, properties.secretKey)
            )
        )
        .build()
}