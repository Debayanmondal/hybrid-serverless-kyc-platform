package com.bank.kyc.onboarding.dto;

import com.bank.kyc.dto.DocumentType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PresignedUrlRequest {
    @NotNull(message = "documentType is required")
    private DocumentType documentType;

    private String contentType = "application/octet-stream";
}
