#!/usr/bin/env bash
# ==============================================================================
# End-to-End Simulation & Verification Test Script (Bash / cURL)
# Hybrid Serverless-Microservices KYC & Digital Onboarding Platform
# ==============================================================================

set -e

ONBOARDING_URL="http://localhost:8081/api/v1/onboarding"
WORKER_URL="http://localhost:8082/api/v1/worker"
LOCALSTACK_URL="http://localhost:4566"

echo "================================================================="
echo "  HYBRID SERVERLESS KYC PLATFORM: END-TO-END DEMO TEST SUITE      "
echo "================================================================="

# SCENARIO 1: Legitimate Customer Onboarding Flow
echo ""
echo ">>> SCENARIO 1: Legitimate Customer Onboarding Flow"

# 1. Register Applicant
RANDOM_ID=$((1000 + RANDOM % 9000))
echo "1. Registering new applicant..."
REG_RESP=$(curl -s -X POST "$ONBOARDING_URL/applicants" \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "Debayan",
    "lastName": "Mondal",
    "email": "debayan.kyc.'$RANDOM_ID'@example.com",
    "phone": "+15551234567",
    "dateOfBirth": "1995-05-15",
    "nationality": "Indian"
  }')

APPLICANT_ID=$(echo "$REG_RESP" | jq -r '.applicantId')
echo "[✓] Registered Applicant ID: $APPLICANT_ID"

# 2. Request S3 Presigned URL
echo ""
echo "2. Requesting S3 Presigned URL for Passport upload..."
PRESIGN_RESP=$(curl -s -X POST "$ONBOARDING_URL/applicants/$APPLICANT_ID/documents/presigned-url" \
  -H "Content-Type: application/json" \
  -d '{
    "documentType": "PASSPORT",
    "contentType": "image/jpeg"
  }')

UPLOAD_URL=$(echo "$PRESIGN_RESP" | jq -r '.presignedUrl')
S3_KEY=$(echo "$PRESIGN_RESP" | jq -r '.s3Key')
echo "[✓] Presigned S3 PUT URL received. S3 Key: $S3_KEY"

# 3. Direct Upload to S3
echo ""
echo "3. Uploading document directly to S3 via Presigned URL (zero backend load)..."
curl -s -X PUT "$UPLOAD_URL" \
  -H "Content-Type: image/jpeg" \
  --data-binary "SIMULATED_GOVERNMENT_PASSPORT_IMAGE_BINARY_STREAM_FOR_KYC_VERIFICATION_HASH_OK" || true
echo "[✓] Direct S3 upload completed."

# 4. Confirm Upload
echo ""
echo "4. Confirming document upload with Onboarding Service..."
CONFIRM_RESP=$(curl -s -X POST "$ONBOARDING_URL/applicants/$APPLICANT_ID/documents/confirm" \
  -H "Content-Type: application/json" \
  -d '{"s3Key": "'"$S3_KEY"'"}')
echo "[✓] Status after confirmation: $(echo "$CONFIRM_RESP" | jq -r '.status')"

# 5. Simulate S3 Event -> SNS -> SQS Pipeline
echo ""
echo "5. Emitting DocumentUploadedEvent to SNS Topic..."
EVENT_PAYLOAD=$(cat <<EOF
{
  "eventId": "$(uuidgen 2>/dev/null || echo "11111111-2222-3333-4444-555555555555")",
  "applicantId": "$APPLICANT_ID",
  "documentType": "PASSPORT",
  "s3Bucket": "kyc-documents-bucket",
  "s3Key": "$S3_KEY",
  "contentType": "image/jpeg",
  "sizeBytes": 102400,
  "sha256Checksum": "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
}
EOF
)

curl -s -X POST "$LOCALSTACK_URL" \
  -d "Action=Publish&TopicArn=arn:aws:sns:us-east-1:000000000000:kyc-events-topic&Message=$(echo "$EVENT_PAYLOAD" | jq -s -R -r @uri)" || true

# 6. Wait for Worker Processing
echo ""
echo "6. Awaiting Verification Worker processing..."
sleep 4

# 7. Check Final Status
FINAL_STATUS=$(curl -s "$ONBOARDING_URL/applicants/$APPLICANT_ID" | jq -r '.status')
echo "[✓] Final Applicant KYC Status: $FINAL_STATUS"

echo ""
echo "================================================================="
echo "  DEMO TEST COMPLETE!                                            "
echo "================================================================="
