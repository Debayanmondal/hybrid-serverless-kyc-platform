# Hybrid Serverless-Microservices KYC & Digital Onboarding Platform

[![Java](https://img.shields.io/badge/Java-17%20%2F%2021-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.0-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![AWS S3](https://img.shields.io/badge/Amazon%20S3-Presigned%20URLs-569A31?style=for-the-badge&logo=amazons3&logoColor=white)](https://aws.amazon.com/s3/)
[![AWS Lambda](https://img.shields.io/badge/AWS%20Lambda-Serverless-FF9900?style=for-the-badge&logo=awslambda&logoColor=white)](https://aws.amazon.com/lambda/)
[![AWS SNS & SQS](https://img.shields.io/badge/AWS%20SNS%20%26%20SQS-Fan--Out%20%26%20Queuing-CC2264?style=for-the-badge&logo=amazonaws&logoColor=white)](https://aws.amazon.com/sqs/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-LocalStack%20%2B%20Compose-2496ED?style=for-the-badge&logo=docker&logoColor=white)](https://www.docker.com/)
[![Terraform](https://img.shields.io/badge/Terraform-IaC-844FBA?style=for-the-badge&logo=terraform&logoColor=white)](https://www.terraform.io/)
[![Kubernetes](https://img.shields.io/badge/Kubernetes-EKS%20Ready-326CE5?style=for-the-badge&logo=kubernetes&logoColor=white)](https://kubernetes.io/)

An enterprise-grade, asynchronous customer identity verification and digital banking onboarding platform. Inspired by **Stripe Identity**, **Onfido**, and tier-1 digital banking engines, this platform bridges **Spring Boot microservices** with **AWS Serverless (Lambda, S3, SNS, SQS)** to achieve zero-bandwidth file ingestion, high-throughput asynchronous document verification, and a resilient, auditable KYC state machine.

---

## 🏛️ High-Level System Architecture

```
                    [ Customer / Applicant ]
                               │
               1. Request S3 Presigned URL
                               ▼
                    [ Onboarding Service ] 
                    (Spring Boot / Port 8081)
                               │
            2. Direct Upload   ▼   3. Object Created Event
               ┌──────────► [ Amazon S3 ] ──────────┐
               │         (Private Bucket)           │
        [ Applicant ]                               ▼
                                             [ AWS Lambda ]
                                        (Image Validation & Check)
                                                    │
                                                    ▼
                                             [ AWS SNS / SQS ]
                                                    │
                                                    ▼
                                        [ KYC Verification Worker ]
                                         (Spring Boot Background)
                                                    │
                                                    ▼
                                            [ Amazon RDS Postgres ]
```

---

## 🔄 Sequence Diagram & Workflow

```mermaid
sequenceDiagram
    autonumber
    actor User as Applicant (Mobile / Web)
    participant Onboarding as Onboarding Service (Spring Boot 8081)
    participant S3 as Amazon S3 (Private Bucket)
    participant Lambda as AWS Lambda (Doc Processor)
    participant SNS as AWS SNS (kyc-events-topic)
    participant SQS as AWS SQS (kyc-verification-queue)
    participant Worker as Verification Worker (Spring Boot 8082)
    participant DB as Amazon RDS PostgreSQL

    User->>Onboarding: POST /api/v1/onboarding/applicants (Register Applicant)
    Onboarding->>DB: Save Applicant (Status: INITIATED)
    Onboarding-->>User: 201 Created (applicantId)

    User->>Onboarding: POST /applicants/{id}/documents/presigned-url (PASSPORT / ID)
    Onboarding->>S3: Generate Presigned PUT URL with Object Metadata
    Onboarding->>DB: Save Pending DocumentRecord (Status: DOCUMENTS_PENDING)
    Onboarding-->>User: 200 OK (Presigned URL & Expiration)

    rect rgb(235, 248, 255)
    Note over User,S3: Direct-to-Cloud Upload (Zero Microservice Memory/Bandwidth Impact)
    User->>S3: HTTP PUT Document Binary to Presigned URL
    S3-->>User: 200 OK (Uploaded to S3)
    end

    User->>Onboarding: POST /applicants/{id}/documents/confirm
    Onboarding->>S3: S3 HeadObject Verification
    Onboarding->>DB: Transition Status -> DOCUMENTS_UPLOADED
    Onboarding-->>User: 200 OK (Confirmed)

    rect rgb(255, 245, 235)
    Note over S3,SNS: Serverless Event Trigger Pipeline
    S3->>Lambda: S3 Event Notification (s3:ObjectCreated:Put)
    Lambda->>S3: HeadObject: Read Metadata & Compute Checksum
    Lambda->>SNS: Publish DocumentUploadedEvent
    SNS->>SQS: Fan-Out Message Delivery
    end

    rect rgb(235, 255, 235)
    Note over SQS,DB: Asynchronous Verification & Account Activation
    Worker->>SQS: Long-Polling ReceiveMessageRequest (20s wait)
    SQS-->>Worker: DocumentUploadedEvent
    Worker->>DB: Fetch Applicant & Document Records
    Worker->>Worker: Run Verification Engine (AML, Blacklist, File Checks)
    alt Approved
        Worker->>DB: Update Status: IDENTITY_VERIFIED -> ACCOUNT_ACTIVATED
        Worker->>SNS: Publish KycDecisionEvent (APPROVED)
    else Fraud / Non-Compliant
        Worker->>DB: Update Status: REJECTED (Reason Logged)
        Worker->>SNS: Publish KycDecisionEvent (REJECTED)
    end
    Worker->>SQS: DeleteMessage (Acknowledge from Queue)
    end

    User->>Onboarding: GET /api/v1/onboarding/applicants/{id}
    Onboarding->>DB: Query Status
    Onboarding-->>User: 200 OK (Status: ACCOUNT_ACTIVATED)
```

---

## 🎯 Core Architectural Problems Solved

### 1. Eliminating Microservice Bandwidth & Memory Bottlenecks (S3 Presigned URLs)
* **The Problem**: In conventional web applications, users upload heavy PDF bank statements or high-resolution ID photos (10–25 MB each) directly to backend Spring Boot endpoints via `MultipartFile`. Ingesting, buffering, and parsing hundreds of concurrent uploads saturates heap memory, triggers aggressive Garbage Collection pauses, and exhausts thread pools, causing catastrophic `OutOfMemoryError` failures.
* **The Solution**: The client requests a cryptographically signed **Amazon S3 Presigned URL** with short expiration (15 minutes). The client then performs an **HTTP PUT directly to Amazon S3**. The Spring Boot microservice never buffers the document bytes, allowing tiny container instances (250m CPU, 512Mi RAM) to handle tens of thousands of onboarding customers concurrently.

### 2. Serverless Document Ingestion Pipeline (AWS Lambda & S3 Event Notifications)
* **The Problem**: Polling S3 for newly uploaded objects is inefficient and expensive. Conversely, having clients notify the backend when an upload finishes creates race conditions where the client might report success before S3 finishes replicating the object.
* **The Solution**: An **S3 Event Notification** automatically invokes an **AWS Lambda function** (`DocumentProcessorLambda`) immediately upon `s3:ObjectCreated:*`. The Lambda extracts object metadata (`applicant-id`, `document-type`), executes cryptographic SHA-256 integrity hashing, and publishes a structured `DocumentUploadedEvent` to AWS SNS.

### 3. Fan-Out Decoupling Architecture (AWS SNS to AWS SQS)
* **The Problem**: A single document upload event must trigger multiple independent downstream processes: identity verification, compliance audit logging, fraud screening, and customer SMS/Email alerts. Hard-coupling these into one service violates Single Responsibility and risks cascading failures.
* **The Solution**: **AWS SNS Topic (`kyc-events-topic`)** acts as the event fan-out publisher. Multiple independent **AWS SQS Queues** subscribe to the topic:
  - `kyc-verification-queue` (consumed by the `VerificationWorker`)
  - `kyc-audit-compliance-queue` (consumed by compliance loggers)
  - `kyc-notifications-queue` (consumed by customer notification workers)
  Adding a new downstream consumer requires zero changes to the Lambda or producer code.

### 4. Resilient Asynchronous Queue Workers & Dead-Letter Queues (DLQ)
* **The Problem**: External banking checks, OCR microservices, or database locks can intermittently fail. If consumer worker threads crash or drop messages, customers get stuck indefinitely in an incomplete onboarding state.
* **The Solution**:
  - The `VerificationWorker` leverages **SQS Long Polling (`WaitTimeSeconds: 20`)** to eliminate empty API responses and reduce AWS API costs by up to 90%.
  - Configured with a **Visibility Timeout (30s)**: If a worker crashes midway through verification, SQS automatically makes the message visible again for another worker instance to pick up.
  - **Dead-Letter Queue (DLQ)** with redrive policy (`maxReceiveCount: 3`): Poison-pill messages that fail processing 3 times are sequestered into `kyc-verification-dlq` for manual inspection and alerting, preventing queue poisoning.

### 5. Strict State Machine & Optimistic Locking
* **State Machine Flow**:
  $$\text{INITIATED} \longrightarrow \text{DOCUMENTS\_PENDING} \longrightarrow \text{DOCUMENTS\_UPLOADED} \longrightarrow \text{IDENTITY\_VERIFIED} \longrightarrow \text{ACCOUNT\_ACTIVATED}$$
  *(or $\text{REJECTED}$ with reason)*
* **Concurrency Protection**: The `Applicant` entity uses JPA `@Version` optimistic locking to prevent concurrent state corruption when multiple background processes or API requests arrive simultaneously.

---

## 📁 Repository Structure

```
hybrid-serverless-kyc-platform/
├── pom.xml                               # Root multi-module Maven POM
├── docker-compose.yml                    # LocalStack (S3, SNS, SQS, Lambda) & PostgreSQL 16
├── build.ps1                             # Automated build script
├── setup-git.ps1                         # Git repository initialization script
├── docker/
│   └── init-aws.sh                       # LocalStack automated resource provisioner
├── kyc-contracts/                        # Shared lightweight event contracts and DTOs
│   └── src/main/java/com/bank/kyc/
│       ├── dto/ApplicantStatus.java      # KYC lifecycle state enum
│       ├── dto/DocumentType.java         # PASSPORT, DRIVING_LICENSE, etc.
│       ├── event/DocumentUploadedEvent.java
│       └── event/KycDecisionEvent.java
├── onboarding-service/                   # Customer-facing Spring Boot Microservice (Port 8081)
│   ├── Dockerfile
│   └── src/main/java/com/bank/kyc/onboarding/
│       ├── controller/OnboardingController.java
│       ├── service/ApplicantService.java
│       ├── service/S3PresignedUrlService.java
│       ├── entity/Applicant.java
│       └── entity/DocumentRecord.java
├── lambda-processor/                     # Serverless AWS Lambda Event Handler (Java 17)
│   └── src/main/java/com/bank/kyc/lambda/
│       └── DocumentProcessorLambda.java
├── verification-worker/                  # Asynchronous SQS Worker Microservice (Port 8082)
│   ├── Dockerfile
│   └── src/main/java/com/bank/kyc/worker/
│       ├── listener/SqsMessageListener.java
│       ├── service/KycVerificationService.java
│       ├── controller/WorkerHealthController.java
│       └── entity/Applicant.java
├── terraform/                            # Production AWS Infrastructure as Code
│   ├── main.tf                           # S3, Lambda, SNS, SQS, RDS, IAM definitions
│   ├── variables.tf
│   └── outputs.tf
├── k8s/                                  # Production Kubernetes Manifests
│   ├── onboarding-service.yaml
│   ├── verification-worker.yaml
│   └── configmap.yaml
└── scripts/
    ├── demo-test.ps1                     # PowerShell End-to-End Simulation Suite
    └── demo-test.sh                      # Bash / cURL End-to-End Simulation Suite
```

---

## 🚀 Quickstart & Local Setup

### Prerequisites
- **Docker & Docker Compose** (version 20.10+)
- **Java 17 or 21** (Optional if running entirely via Docker Compose)

### 1. Launch Platform with Docker Compose
Clone the repository and start all services (Postgres, LocalStack, Onboarding Service, Verification Worker):

```bash
docker compose up --build -d
```

Check running containers:
```bash
docker compose ps
```

LocalStack automatically creates the S3 bucket, SNS topic, SQS queues, and DLQ via `docker/init-aws.sh`.

---

### 2. Run the Automated End-to-End Test Suite

Run the complete simulation test suite (registers user, requests presigned URL, uploads document, triggers serverless pipeline, and verifies account activation):

#### On Windows (PowerShell):
```powershell
.\scripts\demo-test.ps1
```

#### On Linux / macOS (Bash):
```bash
chmod +x ./scripts/demo-test.sh
./scripts/demo-test.sh
```

---

## 📡 REST API Reference

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `POST` | `/api/v1/onboarding/applicants` | Register a new customer (`status: INITIATED`) |
| `POST` | `/api/v1/onboarding/applicants/{id}/documents/presigned-url` | Generate S3 Presigned URL (`status: DOCUMENTS_PENDING`) |
| `POST` | `/api/v1/onboarding/applicants/{id}/documents/confirm` | Confirm direct S3 upload completion (`status: DOCUMENTS_UPLOADED`) |
| `GET` | `/api/v1/onboarding/applicants/{id}` | Check customer KYC onboarding status (`status: ACCOUNT_ACTIVATED`) |
| `GET` | `/api/v1/onboarding/applicants/{id}/documents` | Retrieve customer document verification records & notes |
| `GET` | `/api/v1/worker/status` | Verification Worker health and SQS processed message metrics |

---

## 👨‍💻 Author & Contact

**Debayan Mondal**  
*Senior Java / Spring Boot & Cloud Architect*  
- GitHub: [https://github.com/Debayanmondal](https://github.com/Debayanmondal)
- Project Repository: [https://github.com/Debayanmondal/hybrid-serverless-kyc-platform](https://github.com/Debayanmondal/hybrid-serverless-kyc-platform)
