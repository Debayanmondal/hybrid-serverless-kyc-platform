package com.bank.kyc.onboarding.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class PresignedUrlResponse {
    private String presignedUrl;
    private String s3Key;
    private String bucket;
    private String httpMethod;
    private Instant expiresAt;
    private String instructions;
}
