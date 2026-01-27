package br.com.felixgilioli.videoprocessorworker.config

import br.com.felixgilioli.videoprocessorworker.config.properties.S3Properties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import java.net.URI

@Configuration
@EnableConfigurationProperties(S3Properties::class)
class S3Config(private val properties: S3Properties) {

    @Bean
    fun s3Client(): S3Client = S3Client.builder()
        .endpointOverride(URI.create(properties.endpoint))
        .region(Region.US_EAST_1)
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKey, properties.secretKey)
            )
        )
        .forcePathStyle(true)
        .build()
}