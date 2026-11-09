package com.bank.kyc.event;

import com.bank.kyc.dto.ApplicantStatus;
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
public class KycDecisionEvent {
    private UUID eventId;
    private UUID applicantId;
    private ApplicantStatus decision;  // APPROVED or REJECTED
    private String reason;
    private Instant decidedAt;
}
