package com.bank.kyc.worker.controller;

import com.bank.kyc.worker.listener.SqsMessageListener;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/worker")
@RequiredArgsConstructor
public class WorkerHealthController {

    private final SqsMessageListener sqsMessageListener;

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getWorkerStatus() {
        return ResponseEntity.ok(Map.of(
                "service", "kyc-verification-worker",
                "status", "UP",
                "timestamp", Instant.now(),
                "messagesProcessed", sqsMessageListener.getProcessedCount(),
                "processingErrors", sqsMessageListener.getErrorCount()
        ));
    }
}
