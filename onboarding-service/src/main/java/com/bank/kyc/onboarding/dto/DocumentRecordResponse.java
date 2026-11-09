package com.bank.kyc.onboarding.dto;

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
public class DocumentRecordResponse {
    private UUID documentId;
    private DocumentType documentType;
    private String s3Bucket;
    private String s3Key;
    private String contentType;
    private Long fileSizeBytes;
    private String sha256Checksum;
    private boolean verified;
    private String verificationNotes;
    private Instant uploadedAt;
}
