package com.bank.kyc.onboarding.service;

import com.bank.kyc.dto.DocumentType;
import com.bank.kyc.onboarding.dto.PresignedUrlResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3PresignedUrlService {

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;

    @Value("${app.s3.bucket-name}")
    private String bucketName;

    @Value("${app.s3.presigned-url-expiry-minutes:15}")
    private int expiryMinutes;

    /**
     * Generates a secure S3 Presigned PUT URL for direct client document upload.
     * The client uploads the file directly to S3 using HTTP PUT — no backend bandwidth used.
     */
    public PresignedUrlResponse generatePresignedPutUrl(UUID applicantId, DocumentType documentType, String contentType) {
        String s3Key = String.format("kyc/%s/%s/%s",
                applicantId, documentType.name().toLowerCase(), UUID.randomUUID());

        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .contentType(contentType)
                .metadata(java.util.Map.of(
                        "applicant-id", applicantId.toString(),
                        "document-type", documentType.name()
                ))
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(expiryMinutes))
                .putObjectRequest(objectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);

        log.info("Generated presigned PUT URL for applicant [{}] document type [{}] with key [{}]",
                applicantId, documentType, s3Key);

        return PresignedUrlResponse.builder()
                .presignedUrl(presignedRequest.url().toString())
                .s3Key(s3Key)
                .bucket(bucketName)
                .httpMethod("PUT")
                .expiresAt(Instant.now().plus(Duration.ofMinutes(expiryMinutes)))
                .instructions("Perform HTTP PUT to the presignedUrl with your file as the raw body. " +
                        "Set Content-Type header to: " + contentType)
                .build();
    }

    /**
     * Verifies that the object was actually uploaded to S3 (via HeadObject request).
     */
    public boolean verifyObjectExists(String s3Key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build());
            log.info("S3 object verified: {}/{}", bucketName, s3Key);
            return true;
        } catch (NoSuchKeyException e) {
            log.warn("S3 object NOT found: {}/{}", bucketName, s3Key);
            return false;
        }
    }
}
