package com.bank.kyc.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification;
import com.bank.kyc.dto.DocumentType;
import com.bank.kyc.event.DocumentUploadedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.net.URI;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * AWS Lambda handler triggered by S3 ObjectCreated events.
 * Extracts document metadata, calculates SHA-256, and publishes to SNS.
 */
@Slf4j
public class DocumentProcessorLambda implements RequestHandler<S3Event, String> {

    private final S3Client s3Client;
    private final SnsClient snsClient;
    private final ObjectMapper objectMapper;
    private final String snsTopicArn;

    public DocumentProcessorLambda() {
        String awsEndpoint = System.getenv().getOrDefault("AWS_ENDPOINT_URL", "http://localhost:4566");
        String region = System.getenv().getOrDefault("AWS_REGION", "us-east-1");
        snsTopicArn = System.getenv().getOrDefault("SNS_TOPIC_ARN",
                "arn:aws:sns:us-east-1:000000000000:kyc-events-topic");

        this.s3Client = S3Client.builder()
                .endpointOverride(URI.create(awsEndpoint))
                .region(Region.of(region))
                .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
                .forcePathStyle(true)
                .build();

        this.snsClient = SnsClient.builder()
                .endpointOverride(URI.create(awsEndpoint))
                .region(Region.of(region))
                .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
                .build();

        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
    }

    @Override
    public String handleRequest(S3Event s3Event, Context context) {
        log.info("Lambda triggered with {} S3 record(s)", s3Event.getRecords().size());

        for (S3EventNotification.S3EventNotificationRecord record : s3Event.getRecords()) {
            try {
                processRecord(record, context);
            } catch (Exception e) {
                log.error("Failed to process S3 record: {}", record.getS3().getObject().getKey(), e);
                context.getLogger().log("ERROR processing key: " + record.getS3().getObject().getKey()
                        + " - " + e.getMessage());
            }
        }
        return "OK";
    }

    private void processRecord(S3EventNotification.S3EventNotificationRecord record, Context context) throws Exception {
        String bucket = record.getS3().getBucket().getName();
        String key = record.getS3().getObject().getKey();
        long sizeBytes = record.getS3().getObject().getSizeAsLong();

        log.info("Processing S3 object: bucket={}, key={}, size={}", bucket, key, sizeBytes);

        // Fetch S3 metadata via HeadObject (no full download required)
        HeadObjectResponse headResponse = s3Client.headObject(
                HeadObjectRequest.builder().bucket(bucket).key(key).build());
        Map<String, String> metadata = headResponse.metadata();

        // Extract applicant and document info from metadata
        String applicantIdStr = metadata.getOrDefault("applicant-id", UUID.randomUUID().toString());
        String docTypeStr = metadata.getOrDefault("document-type", "NATIONAL_ID");
        String contentType = headResponse.contentType();

        // Compute lightweight checksum from key + size (simulated; real implementation streams object bytes)
        String checksum = computeChecksum(key + sizeBytes);

        DocumentUploadedEvent event = DocumentUploadedEvent.builder()
                .eventId(UUID.randomUUID())
                .applicantId(UUID.fromString(applicantIdStr))
                .documentType(DocumentType.valueOf(docTypeStr))
                .s3Bucket(bucket)
                .s3Key(key)
                .contentType(contentType)
                .sizeBytes(sizeBytes)
                .sha256Checksum(checksum)
                .timestamp(Instant.now())
                .build();

        // Publish event to SNS for fan-out to SQS verification worker
        String payload = objectMapper.writeValueAsString(event);
        snsClient.publish(PublishRequest.builder()
                .topicArn(snsTopicArn)
                .message(payload)
                .subject("KYC_DOCUMENT_UPLOADED")
                .build());

        log.info("Published DocumentUploadedEvent to SNS for applicant [{}]", applicantIdStr);
        context.getLogger().log("Successfully processed and published event for key: " + key);
    }

    private String computeChecksum(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(input.getBytes());
        return HexFormat.of().formatHex(hash);
    }
}
