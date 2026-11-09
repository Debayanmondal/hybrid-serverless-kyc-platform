package com.bank.kyc.worker.entity;

import com.bank.kyc.dto.ApplicantStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "applicants", indexes = {
        @Index(name = "idx_worker_applicants_email", columnList = "email", unique = true),
        @Index(name = "idx_worker_applicants_status", columnList = "status")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Applicant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "applicant_id", updatable = false, nullable = false)
    private UUID applicantId;

    @Column(name = "first_name", nullable = false, length = 64)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 64)
    private String lastName;

    @Column(name = "email", nullable = false, unique = true, length = 128)
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "date_of_birth", length = 10)
    private String dateOfBirth;

    @Column(name = "nationality", length = 64)
    private String nationality;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private ApplicantStatus status = ApplicantStatus.INITIATED;

    @Column(name = "rejection_reason", length = 512)
    private String rejectionReason;

    /**
     * Optimistic locking to prevent concurrent state corruption.
     */
    @Version
    @Column(name = "version")
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
