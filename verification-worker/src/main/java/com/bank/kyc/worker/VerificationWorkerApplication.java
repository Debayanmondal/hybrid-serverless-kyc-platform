package com.bank.kyc.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VerificationWorkerApplication {
    public static void main(String[] args) {
        SpringApplication.run(VerificationWorkerApplication.class, args);
    }
}
