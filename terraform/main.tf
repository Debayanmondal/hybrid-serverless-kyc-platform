terraform {
  required_version = ">= 1.5.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.40"
    }
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = var.project_name
      Environment = var.environment
      ManagedBy   = "Terraform"
    }
  }
}

# ==============================================================================
# 1. Amazon S3 Private Bucket for Customer KYC Documents
# ==============================================================================
resource "aws_s3_bucket" "kyc_bucket" {
  bucket        = var.s3_bucket_name
  force_destroy = false
}

resource "aws_s3_bucket_versioning" "kyc_versioning" {
  bucket = aws_s3_bucket.kyc_bucket.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "kyc_encryption" {
  bucket = aws_s3_bucket.kyc_bucket.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "kyc_block_public" {
  bucket = aws_s3_bucket.kyc_bucket.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_cors_configuration" "kyc_cors" {
  bucket = aws_s3_bucket.kyc_bucket.id

  cors_rule {
    allowed_headers = ["*"]
    allowed_methods = ["PUT", "POST", "GET", "HEAD"]
    allowed_origins = ["*"]
    max_age_seconds = 3000
  }
}

# ==============================================================================
# 2. AWS SNS Topic (Fan-Out Pipeline)
# ==============================================================================
resource "aws_sns_topic" "kyc_events" {
  name = "${var.project_name}-events-topic"
}

# ==============================================================================
# 3. AWS SQS Queues: Dead Letter Queue (DLQ) & Verification Worker Queue
# ==============================================================================
resource "aws_sqs_queue" "kyc_dlq" {
  name                      = "${var.project_name}-verification-dlq"
  message_retention_seconds = 1209600 # 14 days
}

resource "aws_sqs_queue" "kyc_verification_queue" {
  name                       = "${var.project_name}-verification-queue"
  visibility_timeout_seconds = 60
  receive_wait_time_seconds  = 20 # Long-polling enabled

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.kyc_dlq.arn
    maxReceiveCount     = 3
  })
}

# Allow SNS to publish messages directly into SQS
resource "aws_sqs_queue_policy" "sqs_policy" {
  queue_url = aws_sqs_queue.kyc_verification_queue.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid       = "AllowSnsPublish"
        Effect    = "Allow"
        Principal = { Service = "sns.amazonaws.com" }
        Action    = "sqs:SendMessage"
        Resource  = aws_sqs_queue.kyc_verification_queue.arn
        Condition = {
          ArnEquals = {
            "aws:SourceArn" = aws_sns_topic.kyc_events.arn
          }
        }
      }
    ]
  })
}

# Subscribe SQS to SNS Topic
resource "aws_sns_topic_subscription" "kyc_sqs_sub" {
  topic_arn = aws_sns_topic.kyc_events.arn
  protocol  = "sqs"
  endpoint  = aws_sqs_queue.kyc_verification_queue.arn
}

# ==============================================================================
# 4. AWS Lambda Execution Role & Serverless Document Processor
# ==============================================================================
resource "aws_iam_role" "lambda_exec_role" {
  name = "${var.project_name}-lambda-exec-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "lambda.amazonaws.com"
        }
      }
    ]
  })
}

resource "aws_iam_policy" "lambda_policy" {
  name = "${var.project_name}-lambda-policy"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = "arn:aws:logs:*:*:*"
      },
      {
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:GetObjectMetadata",
          "s3:HeadObject"
        ]
        Resource = "${aws_s3_bucket.kyc_bucket.arn}/*"
      },
      {
        Effect = "Allow"
        Action = [
          "sns:Publish"
        ]
        Resource = aws_sns_topic.kyc_events.arn
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "lambda_attach" {
  role       = aws_iam_role.lambda_exec_role.name
  policy_arn = aws_iam_policy.lambda_policy.arn
}

resource "aws_lambda_function" "document_processor" {
  function_name = "${var.project_name}-document-processor"
  role          = aws_iam_role.lambda_exec_role.arn
  handler       = "com.bank.kyc.lambda.DocumentProcessorLambda::handleRequest"
  runtime       = "java17"
  memory_size   = 512
  timeout       = 30

  # Pre-built shaded jar deployed by CI/CD
  filename         = "../lambda-processor/target/lambda-processor-1.0.0.jar"
  source_code_hash = fileexists("../lambda-processor/target/lambda-processor-1.0.0.jar") ? filebase64sha256("../lambda-processor/target/lambda-processor-1.0.0.jar") : null

  environment {
    variables = {
      SNS_TOPIC_ARN = aws_sns_topic.kyc_events.arn
      AWS_REGION    = var.aws_region
    }
  }
}

# Grant S3 permission to invoke Lambda
resource "aws_lambda_permission" "allow_s3" {
  statement_id  = "AllowExecutionFromS3"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.document_processor.function_name
  principal     = "s3.amazonaws.com"
  source_arn    = aws_s3_bucket.kyc_bucket.arn
}

# S3 Event Notification triggering Lambda on ObjectCreated
resource "aws_s3_bucket_notification" "bucket_notification" {
  bucket = aws_s3_bucket.kyc_bucket.id

  lambda_function {
    lambda_function_arn = aws_lambda_function.document_processor.arn
    events              = ["s3:ObjectCreated:*"]
    filter_prefix       = "kyc/"
  }

  depends_on = [aws_lambda_permission.allow_s3]
}

# ==============================================================================
# 5. Amazon RDS PostgreSQL Instance
# ==============================================================================
resource "aws_db_instance" "kyc_postgres" {
  identifier          = "${var.project_name}-postgres"
  engine              = "postgres"
  engine_version      = "16.1"
  instance_class      = "db.t4g.micro"
  allocated_storage   = 20
  storage_type        = "gp3"
  db_name             = "kyc_db"
  username            = var.db_username
  password            = var.db_password
  skip_final_snapshot = true
}
