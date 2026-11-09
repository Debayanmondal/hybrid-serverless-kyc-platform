package com.bank.kyc.onboarding.repository;

import com.bank.kyc.onboarding.entity.DocumentRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentRecordRepository extends JpaRepository<DocumentRecord, UUID> {
    List<DocumentRecord> findByApplicantApplicantId(UUID applicantId);
}
