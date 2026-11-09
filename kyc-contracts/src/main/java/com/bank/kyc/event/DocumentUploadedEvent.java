package com.bank.kyc.event;

import com.bank.kyc.dto.DocumentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentUploadedEvent {
    private UUID eventId;
    private UUID applicantId;
    private DocumentType documentType;
    private String s3Bucket;
    private String s3Key;
    private String contentType;
    private long sizeBytes;
    private String sha256Checksum;
    private Instant timestamp;
}
