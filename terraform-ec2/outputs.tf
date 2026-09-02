output "elastic_ip" {
  description = "EC2 Elastic IP"
  value       = aws_eip.app.public_ip
}

output "ecr_repository_url" {
  description = "ECR repository URL"
  value       = aws_ecr_repository.app.repository_url
}

output "s3_bucket" {
  description = "S3 bucket name"
  value       = aws_s3_bucket.photos.id
}

output "instance_id" {
  description = "EC2 instance ID"
  value       = aws_instance.app.id
}

output "cloudfront_domain" {
  description = "CloudFront distribution domain — set as CNAME in GoDaddy"
  value       = aws_cloudfront_distribution.main.domain_name
}

output "cloudfront_distribution_id" {
  description = "CloudFront distribution ID"
  value       = aws_cloudfront_distribution.main.id
}

output "acm_validation_records" {
  description = "DNS records to add in GoDaddy for ACM certificate validation"
  value = {
    for dvo in aws_acm_certificate.main.domain_validation_options : dvo.domain_name => {
      name  = dvo.resource_record_name
      type  = dvo.resource_record_type
      value = dvo.resource_record_value
    }
  }
}

output "ses_verification_token" {
  description = "TXT record value for SES domain verification"
  value       = aws_ses_domain_identity.main.verification_token
}

output "ses_dkim_tokens" {
  description = "CNAME records for SES DKIM"
  value       = aws_ses_domain_dkim.main.dkim_tokens
}

output "github_actions_role_arn" {
  description = "GitHub Actions OIDC role ARN"
  value       = aws_iam_role.github_actions.arn
}

output "cognito_user_pool_id" {
  description = "Cognito user pool ID"
  value       = aws_cognito_user_pool.main.id
}

output "cognito_web_client_id" {
  description = "Cognito app client ID — public, used by the frontend"
  value       = aws_cognito_user_pool_client.web.id
}

output "cognito_hosted_ui_domain" {
  description = "Hosted UI base URL — its /oauth2/idpresponse must match the Google client's redirect URI"
  value       = "https://${aws_cognito_user_pool_domain.main.domain}.auth.${var.aws_region}.amazoncognito.com"
}

output "cognito_issuer_uri" {
  description = "JWT issuer URI for the backend resource server"
  value       = "https://cognito-idp.${var.aws_region}.amazonaws.com/${aws_cognito_user_pool.main.id}"
}
