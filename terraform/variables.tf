variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Environment name"
  type        = string
  default     = "prod"
}

variable "app_name" {
  description = "Application name"
  type        = string
  default     = "picmeup"
}

variable "vpc_cidr" {
  description = "VPC CIDR block"
  type        = string
  default     = "10.0.0.0/16"
}

variable "db_instance_class" {
  description = "RDS instance class"
  type        = string
  default     = "db.t4g.micro"
}

variable "db_name" {
  description = "Database name"
  type        = string
  default     = "picmeup"
}

variable "db_username" {
  description = "Database master username"
  type        = string
  default     = "picmeup"
}

variable "ecs_cpu" {
  description = "ECS task CPU units"
  type        = number
  default     = 1024
}

variable "ecs_memory" {
  description = "ECS task memory (MiB)"
  type        = number
  default     = 2048
}

variable "ecs_desired_count" {
  description = "Number of ECS tasks"
  type        = number
  default     = 1
}

variable "s3_bucket_name" {
  description = "S3 bucket name for photos"
  type        = string
  default     = "picmeup-photos-prod"
}

variable "container_port" {
  description = "Container port"
  type        = number
  default     = 8080
}

variable "rekognition_confidence_threshold" {
  description = "Rekognition face match confidence threshold"
  type        = number
  default     = 85.0
}

variable "domain_name" {
  description = "Custom domain name for the application"
  type        = string
  default     = "elitesportphotos.com"
}

variable "email_admin_recipients" {
  description = "Comma-separated list of admin email addresses for order notifications"
  type        = string
  default     = "trent@elitesportphotos.com,murillo.cg@gmail.com"
}

variable "paypal_base_url" {
  description = "PayPal API base URL (sandbox or live)"
  type        = string
  default     = "https://api-m.sandbox.paypal.com"
}

variable "admin_password" {
  description = "Admin password (plaintext) for HTTP Basic Auth — stored BCrypt-hashed in Secrets Manager"
  type        = string
  sensitive   = true
}
