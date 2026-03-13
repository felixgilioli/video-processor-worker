package br.com.felixgilioli.videoprocessorworker.service

import br.com.felixgilioli.videoprocessorworker.config.properties.S3Properties
import org.springframework.stereotype.Service
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import java.time.Duration

@Service
class StorageService(
    private val s3Client: S3Client,
    private val s3Presigner: S3Presigner,
    private val properties: S3Properties
) {

    fun download(key: String): ByteArray {
        val response = s3Client.getObject(
            GetObjectRequest.builder()
                .bucket(properties.bucket)
                .key(key)
                .build()
        )
        return response.readAllBytes()
    }

    fun upload(key: String, bytes: ByteArray, contentType: String): String {
        s3Client.putObject(
            PutObjectRequest.builder()
                .bucket(properties.bucket)
                .key(key)
                .contentType(contentType)
                .build(),
            RequestBody.fromBytes(bytes)
        )
        return generateDownloadUrl(key)
    }

    fun generateDownloadUrl(key: String, duration: Duration = Duration.ofHours(1)): String {
        val presignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(duration)
            .getObjectRequest { it.bucket(properties.bucket).key(key) }
            .build()

        val url = s3Presigner.presignGetObject(presignRequest).url()
        val endpointUri = java.net.URI(properties.endpoint)
        val localhostAuthority = "localhost:${endpointUri.port}"
        return url.toString().replace(endpointUri.authority, localhostAuthority)
    }
}