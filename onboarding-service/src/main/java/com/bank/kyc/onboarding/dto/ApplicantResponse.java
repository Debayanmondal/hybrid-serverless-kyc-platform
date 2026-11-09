package com.bank.kyc.onboarding.dto;

import com.bank.kyc.dto.ApplicantStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class ApplicantResponse {
    private UUID applicantId;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String nationality;
    private ApplicantStatus status;
    private Instant createdAt;
    private Instant updatedAt;
}
