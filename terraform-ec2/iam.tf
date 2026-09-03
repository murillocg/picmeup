data "aws_caller_identity" "current" {}

# --- EC2 Instance Role ---

resource "aws_iam_role" "ec2" {
  name = "${var.app_name}-ec2"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_instance_profile" "ec2" {
  name = "${var.app_name}-ec2"
  role = aws_iam_role.ec2.name
}

resource "aws_iam_role_policy" "ec2_s3" {
  name = "${var.app_name}-s3"
  role = aws_iam_role.ec2.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["s3:PutObject", "s3:GetObject", "s3:DeleteObject", "s3:ListBucket"]
      Resource = [aws_s3_bucket.photos.arn, "${aws_s3_bucket.photos.arn}/*"]
    }]
  })
}

resource "aws_iam_role_policy" "ec2_rekognition" {
  name = "${var.app_name}-rekognition"
  role = aws_iam_role.ec2.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "rekognition:CreateCollection",
        "rekognition:DeleteCollection",
        "rekognition:IndexFaces",
        "rekognition:SearchFacesByImage",
        "rekognition:ListFaces"
      ]
      Resource = "arn:aws:rekognition:${var.aws_region}:${data.aws_caller_identity.current.account_id}:collection/${var.app_name}-*"
    }]
  })
}

resource "aws_iam_role_policy" "ec2_ses" {
  name = "${var.app_name}-ses"
  role = aws_iam_role.ec2.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["ses:SendEmail", "ses:SendRawEmail"]
      Resource = "*"
    }]
  })
}

# Creating the Cognito identity is part of issuing an invite: the users table grants
# authority, but Cognito is what an emailed sign-in code is actually sent to. Without a
# pool user there is nothing to send to, and prevent_user_existence_errors means the
# login page cannot say so — it shows "check your email" either way.
#
# Scoped to the one pool. Deliberately no AdminDeleteUser: access is revoked by disabling
# the users row, which keeps photo attribution intact.
resource "aws_iam_role_policy" "ec2_cognito" {
  name = "${var.app_name}-cognito"
  role = aws_iam_role.ec2.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["cognito-idp:AdminCreateUser", "cognito-idp:AdminGetUser"]
      Resource = aws_cognito_user_pool.main.arn
    }]
  })
}

resource "aws_iam_role_policy" "ec2_ecr" {
  name = "${var.app_name}-ecr"
  role = aws_iam_role.ec2.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "ecr:GetAuthorizationToken",
        "ecr:BatchCheckLayerAvailability",
        "ecr:GetDownloadUrlForLayer",
        "ecr:BatchGetImage"
      ]
      Resource = "*"
    }]
  })
}

# --- Lambda Execution Role ---

resource "aws_iam_role" "lambda" {
  name = "${var.app_name}-lambda"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "lambda.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "lambda_basic" {
  role       = aws_iam_role.lambda.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_role_policy" "lambda_s3" {
  name = "${var.app_name}-lambda-s3"
  role = aws_iam_role.lambda.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["s3:GetObject", "s3:PutObject"]
      Resource = "${aws_s3_bucket.photos.arn}/*"
    }]
  })
}

resource "aws_iam_role_policy" "lambda_rekognition" {
  name = "${var.app_name}-lambda-rekognition"
  role = aws_iam_role.lambda.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "rekognition:CreateCollection",
        "rekognition:IndexFaces"
      ]
      Resource = "arn:aws:rekognition:${var.aws_region}:${data.aws_caller_identity.current.account_id}:collection/${var.app_name}-*"
    }]
  })
}

# --- GitHub Actions OIDC ---

resource "aws_iam_openid_connect_provider" "github" {
  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = ["ffffffffffffffffffffffffffffffffffffffff"]
}

resource "aws_iam_role" "github_actions" {
  name = "${var.app_name}-github-actions"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Federated = aws_iam_openid_connect_provider.github.arn }
      Action    = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = { "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com" }
        StringLike   = { "token.actions.githubusercontent.com:sub" = "repo:${var.github_repo}:*" }
      }
    }]
  })
}

resource "aws_iam_role_policy" "github_actions_ecr" {
  name = "${var.app_name}-github-actions-ecr"
  role = aws_iam_role.github_actions.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "ecr:GetAuthorizationToken",
        "ecr:BatchCheckLayerAvailability",
        "ecr:GetDownloadUrlForLayer",
        "ecr:BatchGetImage",
        "ecr:PutImage",
        "ecr:InitiateLayerUpload",
        "ecr:UploadLayerPart",
        "ecr:CompleteLayerUpload"
      ]
      Resource = "*"
    }]
  })
}

resource "aws_iam_role_policy" "github_actions_lambda" {
  name = "${var.app_name}-github-actions-lambda"
  role = aws_iam_role.github_actions.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["lambda:UpdateFunctionCode"]
      Resource = aws_lambda_function.photo_processor.arn
    }]
  })
}
