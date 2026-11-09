variable "aws_region" {
  description = "AWS region to deploy resources"
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Deployment environment (dev, staging, prod)"
  type        = string
  default     = "prod"
}

variable "project_name" {
  description = "Project name prefix for resources"
  type        = string
  default     = "kyc-platform"
}

variable "s3_bucket_name" {
  description = "S3 bucket for KYC document uploads"
  type        = string
  default     = "kyc-platform-documents-prod"
}

variable "db_username" {
  description = "PostgreSQL RDS master username"
  type        = string
  default     = "postgres"
}

variable "db_password" {
  description = "PostgreSQL RDS master password"
  type        = string
  sensitive   = true
  default     = "SuperSecretKycPass123!"
}
