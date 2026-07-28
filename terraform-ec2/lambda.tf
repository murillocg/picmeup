resource "aws_lambda_function" "photo_processor" {
  function_name = "${var.app_name}-photo-processor"
  role          = aws_iam_role.lambda.arn
  handler       = "bootstrap"
  runtime       = "provided.al2023"
  architectures = ["x86_64"]
  timeout       = 60
  memory_size   = 512

  filename         = "${path.module}/lambda-placeholder.zip"
  source_code_hash = filebase64sha256("${path.module}/lambda-placeholder.zip")

  environment {
    variables = {
      S3_BUCKET           = var.s3_bucket_name
      CALLBACK_URL        = "http://${aws_eip.app.public_ip}/api/internal/photos"
      CALLBACK_SECRET     = var.lambda_callback_secret
      REKOGNITION_PREFIX  = var.app_name
      AWS_REGION_OVERRIDE = var.aws_region
    }
  }

  lifecycle {
    ignore_changes = [filename, source_code_hash]
  }
}

resource "aws_lambda_permission" "s3_invoke" {
  statement_id  = "AllowS3Invoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.photo_processor.function_name
  principal     = "s3.amazonaws.com"
  source_arn    = aws_s3_bucket.photos.arn
}

resource "aws_cloudwatch_log_group" "lambda" {
  name              = "/aws/lambda/${var.app_name}-photo-processor"
  retention_in_days = 7
}
