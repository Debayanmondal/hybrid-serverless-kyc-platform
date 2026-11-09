package com.bank.kyc.onboarding.controller;

import com.bank.kyc.onboarding.dto.*;
import com.bank.kyc.onboarding.service.ApplicantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final ApplicantService applicantService;

    /**
     * Step 1: Register a new KYC applicant.
     */
    @PostMapping("/applicants")
    public ResponseEntity<ApplicantResponse> registerApplicant(
            @Valid @RequestBody ApplicantRegistrationRequest request) {
        log.info("Registering applicant: {} {}", request.getFirstName(), request.getLastName());
        ApplicantResponse response = applicantService.registerApplicant(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Step 2: Get a presigned S3 URL for direct document upload.
     */
    @PostMapping("/applicants/{applicantId}/documents/presigned-url")
    public ResponseEntity<PresignedUrlResponse> getPresignedUploadUrl(
            @PathVariable UUID applicantId,
            @Valid @RequestBody PresignedUrlRequest request) {
        log.info("Presigned URL requested by applicant [{}] for document type [{}]",
                applicantId, request.getDocumentType());
        PresignedUrlResponse response = applicantService.requestDocumentUploadUrl(applicantId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Step 3: Confirm that document upload to S3 completed successfully.
     */
    @PostMapping("/applicants/{applicantId}/documents/confirm")
    public ResponseEntity<ApplicantResponse> confirmDocumentUpload(
            @PathVariable UUID applicantId,
            @RequestBody Map<String, String> body) {
        String s3Key = body.get("s3Key");
        if (s3Key == null || s3Key.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        log.info("Document upload confirmation for applicant [{}], S3 key: {}", applicantId, s3Key);
        ApplicantResponse response = applicantService.confirmDocumentUpload(applicantId, s3Key);
        return ResponseEntity.ok(response);
    }

    /**
     * Step 4: Check current KYC status of an applicant.
     */
    @GetMapping("/applicants/{applicantId}")
    public ResponseEntity<ApplicantResponse> getApplicantStatus(@PathVariable UUID applicantId) {
        ApplicantResponse response = applicantService.getApplicant(applicantId);
        return ResponseEntity.ok(response);
    }

    /**
     * Step 5: List document records and verification status for an applicant.
     */
    @GetMapping("/applicants/{applicantId}/documents")
    public ResponseEntity<java.util.List<DocumentRecordResponse>> getApplicantDocuments(@PathVariable UUID applicantId) {
        java.util.List<DocumentRecordResponse> response = applicantService.getApplicantDocuments(applicantId);
        return ResponseEntity.ok(response);
    }
}
