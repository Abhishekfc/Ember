package com.ember.backend.service

import com.ember.backend.config.R2Properties
import com.ember.backend.exception.ApiException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.net.URI

@Service
class R2StorageService(private val r2Properties: R2Properties) {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val isConfigured: Boolean
        get() = r2Properties.endpoint.isNotBlank() &&
            r2Properties.accessKeyId.isNotBlank() &&
            r2Properties.secretAccessKey.isNotBlank()

    private val s3Client: S3Client? by lazy {
        if (!isConfigured) {
            logger.warn("Cloudflare R2 is not configured (ember.storage.r2.*); photo uploads will fail")
            return@lazy null
        }
        S3Client.builder()
            .region(Region.of(r2Properties.region.ifBlank { "auto" }))
            .endpointOverride(URI.create(r2Properties.endpoint))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(r2Properties.accessKeyId, r2Properties.secretAccessKey)
                )
            )
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .build()
    }

    fun upload(key: String, contentType: String, bytes: ByteArray) {
        val client = s3Client
            ?: throw ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Photo storage (R2) is not configured on this server")
        val request = PutObjectRequest.builder()
            .bucket(r2Properties.bucket)
            .key(key)
            .contentType(contentType)
            .build()
        client.putObject(request, RequestBody.fromBytes(bytes))
    }

    fun publicUrl(key: String): String = "${r2Properties.publicBaseUrl.trimEnd('/')}/$key"

    /** Best-effort, not required to succeed: used by account deletion to clean up storage
     * alongside the DB rows, but a storage-side failure (or R2 simply not being configured on
     * this server) shouldn't be the thing that blocks someone from actually deleting their
     * account — that's a real DB row disappearing regardless, and a leftover unreachable R2
     * object with no account left to point to it is a much smaller problem than a delete that
     * doesn't work. */
    fun delete(key: String) {
        val client = s3Client ?: return
        runCatching {
            client.deleteObject(DeleteObjectRequest.builder().bucket(r2Properties.bucket).key(key).build())
        }.onFailure {
            logger.warn("Failed to delete R2 object during cleanup: key={}", key, it)
        }
    }
}
