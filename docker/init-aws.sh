#!/bin/bash
echo "=== Initializing LocalStack AWS Resources ==="

export AWS_DEFAULT_REGION=us-east-1

# 1. Create S3 Bucket for KYC Documents
echo "Creating S3 bucket: kyc-documents-bucket..."
awslocal s3 mb s3://kyc-documents-bucket
awslocal s3api put-bucket-cors --bucket kyc-documents-bucket --cors-configuration '{
  "CORSRules": [
    {
      "AllowedHeaders": ["*"],
      "AllowedMethods": ["GET", "PUT", "POST", "HEAD"],
      "AllowedOrigins": ["*"],
      "MaxAgeSeconds": 3000
    }
  ]
}'

# 2. Create SNS Topic for KYC Events
echo "Creating SNS Topic: kyc-events-topic..."
TOPIC_ARN=$(awslocal sns create-topic --name kyc-events-topic --output text --query 'TopicArn')
echo "Topic ARN: $TOPIC_ARN"

# 3. Create Dead-Letter Queue (DLQ)
echo "Creating DLQ: kyc-verification-dlq..."
DLQ_URL=$(awslocal sqs create-queue --queue-name kyc-verification-dlq --output text --query 'QueueUrl')
DLQ_ARN=$(awslocal sqs get-queue-attributes --queue-url "$DLQ_URL" --attribute-names QueueArn --output text --query 'Attributes.QueueArn')
echo "DLQ URL: $DLQ_URL"
echo "DLQ ARN: $DLQ_ARN"

# 4. Create SQS Queue with Redrive Policy pointing to DLQ
echo "Creating SQS Queue: kyc-verification-queue..."
REDRIVE_POLICY="{\"deadLetterTargetArn\":\"$DLQ_ARN\",\"maxReceiveCount\":3}"
QUEUE_URL=$(awslocal sqs create-queue --queue-name kyc-verification-queue \
  --attributes "{\"RedrivePolicy\":$(echo "$REDRIVE_POLICY" | jq -R .)}" \
  --output text --query 'QueueUrl' 2>/dev/null || \
  awslocal sqs create-queue --queue-name kyc-verification-queue --output text --query 'QueueUrl')

QUEUE_ARN=$(awslocal sqs get-queue-attributes --queue-url "$QUEUE_URL" --attribute-names QueueArn --output text --query 'Attributes.QueueArn')
echo "Queue URL: $QUEUE_URL"
echo "Queue ARN: $QUEUE_ARN"

# 5. Subscribe SQS Queue to SNS Topic (Fan-Out Pattern)
echo "Subscribing SQS queue to SNS topic..."
awslocal sns subscribe \
  --topic-arn "$TOPIC_ARN" \
  --protocol sqs \
  --notification-endpoint "$QUEUE_ARN"

echo "=== LocalStack AWS Initialization Complete! ==="
