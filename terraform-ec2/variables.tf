variable "aws_region" {
  type    = string
  default = "ap-southeast-2"
}

variable "environment" {
  type    = string
  default = "prod"
}

variable "app_name" {
  type    = string
  default = "elitesportphotos"
}

variable "vpc_cidr" {
  type    = string
  default = "10.0.0.0/16"
}

variable "s3_bucket_name" {
  type    = string
  default = "elitesportphotos-prod"
}

variable "domain_name" {
  type    = string
  default = "elitesportphotos.com"
}

variable "instance_type" {
  type    = string
  default = "t3.micro"
}

variable "ssh_public_key" {
  type = string
}

variable "admin_ssh_cidr" {
  type    = string
  default = "0.0.0.0/0"
}

variable "github_repo" {
  type    = string
  default = "murillocg/picmeup"
}

variable "email_admin_recipients" {
  type    = string
  default = "trent@elitesportphotos.com,murillo.cg@gmail.com"
}

variable "lambda_callback_secret" {
  type      = string
  sensitive = true
}

# Google OAuth client backing Cognito's "Sign in with Google". Created in the
# Google Cloud console; its authorised redirect URI must be the Cognito Hosted
# UI endpoint, https://<prefix>.auth.<region>.amazoncognito.com/oauth2/idpresponse.
variable "google_oauth_client_id" {
  type = string
}

variable "google_oauth_client_secret" {
  type      = string
  sensitive = true
}
