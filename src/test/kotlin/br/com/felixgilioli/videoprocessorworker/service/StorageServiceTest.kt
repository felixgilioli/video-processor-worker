package br.com.felixgilioli.videoprocessorworker.service

import br.com.felixgilioli.videoprocessorworker.config.properties.S3Properties
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest
import java.net.URI
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class StorageServiceTest {

    private val s3Client: S3Client = mock()
    private val s3Presigner: S3Presigner = mock()
    private val properties = S3Properties(
        endpoint = "http://localhost:9000",
        accessKey = "minioadmin",
        secretKey = "minioadmin",
        bucket = "test-bucket"
    )

    private val storageService = StorageService(s3Client, s3Presigner, properties)

    @Test
    fun `download should return bytes from S3`() {
        val key = "videos/test.mp4"
        val expectedBytes = "video content".toByteArray()
        val responseInputStream: ResponseInputStream<GetObjectResponse> = mock()

        whenever(s3Client.getObject(any<GetObjectRequest>())).thenReturn(responseInputStream)
        whenever(responseInputStream.readAllBytes()).thenReturn(expectedBytes)

        val result = storageService.download(key)

        assertContentEquals(expectedBytes, result)
        verify(s3Client).getObject(any<GetObjectRequest>())
    }

    @Test
    fun `upload should store object in S3 and return presigned URL`() {
        val key = "zips/123/frames.zip"
        val bytes = "zip content".toByteArray()
        val expectedUrl = "http://localhost:9000/test-bucket/zips/123/frames.zip?sig=abc"
        val presignedRequest: PresignedGetObjectRequest = mock()

        whenever(s3Client.putObject(any<PutObjectRequest>(), any<RequestBody>())).thenReturn(mock<PutObjectResponse>())
        whenever(s3Presigner.presignGetObject(any<GetObjectPresignRequest>())).thenReturn(presignedRequest)
        whenever(presignedRequest.url()).thenReturn(URI(expectedUrl).toURL())

        val result = storageService.upload(key, bytes, "application/zip")

        assertEquals(expectedUrl, result)
        verify(s3Client).putObject(any<PutObjectRequest>(), any<RequestBody>())
        verify(s3Presigner).presignGetObject(any<GetObjectPresignRequest>())
    }

    @Test
    fun `generateDownloadUrl should return presigned URL with default 1 hour duration`() {
        val key = "frames/abc/first_frame.jpg"
        val expectedUrl = "http://localhost:9000/test-bucket/frames/abc/first_frame.jpg?sig=xyz"
        val presignedRequest: PresignedGetObjectRequest = mock()

        whenever(s3Presigner.presignGetObject(any<GetObjectPresignRequest>())).thenReturn(presignedRequest)
        whenever(presignedRequest.url()).thenReturn(URI(expectedUrl).toURL())

        val result = storageService.generateDownloadUrl(key)

        assertEquals(expectedUrl, result)
        verify(s3Presigner).presignGetObject(any<GetObjectPresignRequest>())
    }

    @Test
    fun `generateDownloadUrl should accept custom duration`() {
        val key = "frames/abc/first_frame.jpg"
        val expectedUrl = "http://localhost:9000/test-bucket/frames/abc/first_frame.jpg?sig=xyz"
        val presignedRequest: PresignedGetObjectRequest = mock()

        whenever(s3Presigner.presignGetObject(any<GetObjectPresignRequest>())).thenReturn(presignedRequest)
        whenever(presignedRequest.url()).thenReturn(URI(expectedUrl).toURL())

        val result = storageService.generateDownloadUrl(key, Duration.ofMinutes(30))

        assertEquals(expectedUrl, result)
    }
}
