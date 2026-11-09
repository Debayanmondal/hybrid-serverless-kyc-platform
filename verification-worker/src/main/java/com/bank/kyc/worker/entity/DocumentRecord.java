package com.bank.kyc.worker.entity;

import com.bank.kyc.dto.DocumentType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_records", indexes = {
        @Index(name = "idx_worker_doc_applicant", columnList = "applicant_id"),
        @Index(name = "idx_worker_doc_s3key", columnList = "s3_key", unique = true)
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "document_id", updatable = false, nullable = false)
    private UUID documentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicant_id", nullable = false)
    private Applicant applicant;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 32)
    private DocumentType documentType;

    @Column(name = "s3_bucket", nullable = false, length = 128)
    private String s3Bucket;

    @Column(name = "s3_key", nullable = false, unique = true, length = 255)
    private String s3Key;

    @Column(name = "content_type", length = 64)
    private String contentType;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "sha256_checksum", length = 128)
    private String sha256Checksum;

    @Column(name = "verified", nullable = false)
    @Builder.Default
    private boolean verified = false;

    @Column(name = "verification_notes", length = 512)
    private String verificationNotes;

    @CreationTimestamp
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;
}
