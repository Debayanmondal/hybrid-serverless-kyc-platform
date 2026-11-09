output "s3_bucket_name" {
  description = "KYC documents S3 bucket name"
  value       = aws_s3_bucket.kyc_bucket.id
}

output "sns_topic_arn" {
  description = "SNS topic ARN for KYC events"
  value       = aws_sns_topic.kyc_events.arn
}

output "sqs_queue_url" {
  description = "SQS queue URL for KYC verification worker"
  value       = aws_sqs_queue.kyc_verification_queue.id
}

output "sqs_dlq_url" {
  description = "SQS dead letter queue URL"
  value       = aws_sqs_queue.kyc_dlq.id
}

output "lambda_function_arn" {
  description = "Lambda document processor ARN"
  value       = aws_lambda_function.document_processor.arn
}

output "rds_endpoint" {
  description = "RDS PostgreSQL endpoint"
  value       = aws_db_instance.kyc_postgres.endpoint
}
