package com.bank.kyc.worker.listener;

import com.bank.kyc.event.DocumentUploadedEvent;
import com.bank.kyc.worker.service.KycVerificationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class SqsMessageListener {

    private final SqsClient sqsClient;
    private final KycVerificationService kycVerificationService;
    private final ObjectMapper objectMapper;

    @Value("${app.sqs.queue-url:http://localhost:4566/000000000000/kyc-verification-queue}")
    private String queueUrl;

    @Value("${app.sqs.max-messages:10}")
    private int maxMessages;

    @Value("${app.sqs.wait-time-seconds:10}")
    private int waitTimeSeconds;

    @Value("${app.sqs.visibility-timeout:30}")
    private int visibilityTimeout;

    private final AtomicLong processedCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);

    /**
     * Polls the AWS SQS queue asynchronously with long-polling.
     * Decouples document upload triggers from verification microservice.
     */
    @Scheduled(fixedDelay = 2000)
    public void pollQueue() {
        try {
            ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(maxMessages)
                    .waitTimeSeconds(waitTimeSeconds)
                    .visibilityTimeout(visibilityTimeout)
                    .build();

            ReceiveMessageResponse response = sqsClient.receiveMessage(receiveRequest);
            List<Message> messages = response.messages();

            if (!messages.isEmpty()) {
                log.info("Received {} KYC document event(s) from SQS queue", messages.size());

                for (Message message : messages) {
                    processMessage(message);
                }
            }
        } catch (QueueDoesNotExistException e) {
            log.warn("SQS Queue [{}] does not exist yet. Retrying on next cycle...", queueUrl);
        } catch (Exception e) {
            log.error("Error polling SQS queue: {}", e.getMessage(), e);
            errorCount.incrementAndGet();
        }
    }

    private void processMessage(Message message) {
        try {
            String rawBody = message.body();
            DocumentUploadedEvent event = extractEvent(rawBody);

            if (event == null || event.getApplicantId() == null) {
                log.warn("Discarding malformed or empty message: {}", message.messageId());
                deleteMessage(message.receiptHandle());
                return;
            }

            log.info("Processing SQS message [{}] for applicant [{}]", message.messageId(), event.getApplicantId());
            kycVerificationService.processDocumentVerification(event);

            // Acknowledge and remove from queue
            deleteMessage(message.receiptHandle());
            processedCount.incrementAndGet();
            log.info("Successfully processed and deleted SQS message [{}]", message.messageId());

        } catch (Exception e) {
            errorCount.incrementAndGet();
            log.error("Failed to process message [{}]: {}", message.messageId(), e.getMessage(), e);
            // In AWS SQS, omitting deleteMessage allows the visibility timeout to expire,
            // triggering redelivery or routing to the Dead Letter Queue (DLQ) after maxReceiveCount.
        }
    }

    /**
     * Extracts DocumentUploadedEvent from either a direct SQS JSON payload
     * or an SNS-wrapped notification envelope (Fan-Out pattern).
     */
    private DocumentUploadedEvent extractEvent(String rawBody) throws Exception {
        JsonNode root = objectMapper.readTree(rawBody);

        // If wrapped in SNS envelope (default SNS -> SQS subscription)
        if (root.has("Type") && "Notification".equals(root.get("Type").asText()) && root.has("Message")) {
            String innerMessage = root.get("Message").asText();
            return objectMapper.readValue(innerMessage, DocumentUploadedEvent.class);
        }

        // Direct payload
        return objectMapper.readValue(rawBody, DocumentUploadedEvent.class);
    }

    private void deleteMessage(String receiptHandle) {
        try {
            sqsClient.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(receiptHandle)
                    .build());
        } catch (Exception e) {
            log.error("Failed to delete message from SQS queue: {}", e.getMessage());
        }
    }

    public long getProcessedCount() {
        return processedCount.get();
    }

    public long getErrorCount() {
        return errorCount.get();
    }
}
