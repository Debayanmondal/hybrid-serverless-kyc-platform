package com.bank.kyc.worker.repository;

import com.bank.kyc.worker.entity.DocumentRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRecordRepository extends JpaRepository<DocumentRecord, UUID> {
    Optional<DocumentRecord> findByS3Key(String s3Key);
    List<DocumentRecord> findByApplicantApplicantId(UUID applicantId);
}
