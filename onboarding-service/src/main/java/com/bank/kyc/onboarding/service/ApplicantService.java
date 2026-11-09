package com.bank.kyc.onboarding.service;

import com.bank.kyc.dto.ApplicantStatus;
import com.bank.kyc.dto.DocumentType;
import com.bank.kyc.onboarding.dto.*;
import com.bank.kyc.onboarding.entity.Applicant;
import com.bank.kyc.onboarding.entity.DocumentRecord;
import com.bank.kyc.onboarding.repository.ApplicantRepository;
import com.bank.kyc.onboarding.repository.DocumentRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicantService {

    private final ApplicantRepository applicantRepository;
    private final DocumentRecordRepository documentRecordRepository;
    private final S3PresignedUrlService s3PresignedUrlService;

    @Transactional
    public ApplicantResponse registerApplicant(ApplicantRegistrationRequest request) {
        // Guard: prevent duplicate registrations
        applicantRepository.findByEmail(request.getEmail()).ifPresent(existing -> {
            throw new IllegalArgumentException("Applicant already registered with email: " + request.getEmail());
        });

        Applicant applicant = Applicant.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .dateOfBirth(request.getDateOfBirth())
                .nationality(request.getNationality())
                .status(ApplicantStatus.INITIATED)
                .build();

        Applicant saved = applicantRepository.save(applicant);
        log.info("Registered new applicant [{}] with status INITIATED", saved.getApplicantId());
        return mapToResponse(saved);
    }

    public ApplicantResponse getApplicant(UUID applicantId) {
        Applicant applicant = findOrThrow(applicantId);
        return mapToResponse(applicant);
    }

    public java.util.List<DocumentRecordResponse> getApplicantDocuments(UUID applicantId) {
        findOrThrow(applicantId); // ensure applicant exists
        return documentRecordRepository.findByApplicantApplicantId(applicantId).stream()
                .map(this::mapToDocResponse)
                .toList();
    }

    /**
     * Generates S3 presigned URL and creates a pending DocumentRecord in the DB.
     */
    @Transactional
    public PresignedUrlResponse requestDocumentUploadUrl(UUID applicantId, PresignedUrlRequest request) {
        Applicant applicant = findOrThrow(applicantId);

        PresignedUrlResponse urlResponse = s3PresignedUrlService.generatePresignedPutUrl(
                applicantId, request.getDocumentType(), request.getContentType());

        // Track the pending document record
        DocumentRecord record = DocumentRecord.builder()
                .applicant(applicant)
                .documentType(request.getDocumentType())
                .s3Bucket(urlResponse.getBucket())
                .s3Key(urlResponse.getS3Key())
                .contentType(request.getContentType())
                .verified(false)
                .build();

        documentRecordRepository.save(record);

        // Update applicant status to await documents
        applicant.setStatus(ApplicantStatus.DOCUMENTS_PENDING);
        applicantRepository.save(applicant);

        log.info("Applicant [{}] document upload URL generated. S3 Key: {}", applicantId, urlResponse.getS3Key());
        return urlResponse;
    }

    /**
     * Called after client has PUT the file to S3.
     * Verifies the object exists on S3 and transitions applicant status.
     */
    @Transactional
    public ApplicantResponse confirmDocumentUpload(UUID applicantId, String s3Key) {
        Applicant applicant = findOrThrow(applicantId);

        boolean exists = s3PresignedUrlService.verifyObjectExists(s3Key);
        if (!exists) {
            throw new IllegalStateException("Document not found on S3 for key: " + s3Key +
                    ". Please upload the file to the presigned URL first.");
        }

        // Mark document record as uploaded
        documentRecordRepository.findByApplicantApplicantId(applicantId).stream()
                .filter(doc -> doc.getS3Key().equals(s3Key))
                .findFirst()
                .ifPresent(doc -> {
                    doc.setVerificationNotes("Uploaded and confirmed by S3 HeadObject check");
                    documentRecordRepository.save(doc);
                });

        applicant.setStatus(ApplicantStatus.DOCUMENTS_UPLOADED);
        Applicant saved = applicantRepository.save(applicant);

        log.info("Applicant [{}] document confirmed on S3. Status -> DOCUMENTS_UPLOADED", applicantId);
        return mapToResponse(saved);
    }

    private Applicant findOrThrow(UUID applicantId) {
        return applicantRepository.findById(applicantId)
                .orElseThrow(() -> new IllegalArgumentException("Applicant not found: " + applicantId));
    }

    private ApplicantResponse mapToResponse(Applicant a) {
        return ApplicantResponse.builder()
                .applicantId(a.getApplicantId())
                .firstName(a.getFirstName())
                .lastName(a.getLastName())
                .email(a.getEmail())
                .phone(a.getPhone())
                .nationality(a.getNationality())
                .status(a.getStatus())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }

    private DocumentRecordResponse mapToDocResponse(DocumentRecord d) {
        return DocumentRecordResponse.builder()
                .documentId(d.getDocumentId())
                .documentType(d.getDocumentType())
                .s3Bucket(d.getS3Bucket())
                .s3Key(d.getS3Key())
                .contentType(d.getContentType())
                .fileSizeBytes(d.getFileSizeBytes())
                .sha256Checksum(d.getSha256Checksum())
                .verified(d.isVerified())
                .verificationNotes(d.getVerificationNotes())
                .uploadedAt(d.getUploadedAt())
                .build();
    }
}
