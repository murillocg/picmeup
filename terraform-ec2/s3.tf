resource "aws_s3_bucket" "photos" {
  bucket = var.s3_bucket_name
}

resource "aws_s3_bucket_public_access_block" "photos" {
  bucket = aws_s3_bucket.photos.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "photos" {
  bucket = aws_s3_bucket.photos.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_cors_configuration" "photos" {
  bucket = aws_s3_bucket.photos.id

  cors_rule {
    allowed_headers = ["*"]
    allowed_methods = ["GET", "PUT"]
    allowed_origins = [
      "https://${var.domain_name}",
      "https://www.${var.domain_name}"
    ]
    max_age_seconds = 3600
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "photos" {
  bucket = aws_s3_bucket.photos.id

  rule {
    id     = "expire-old-photos"
    status = "Enabled"
    filter {
      prefix = "originals/"
    }
    expiration {
      days = 75
    }
  }

  rule {
    id     = "expire-old-thumbnails"
    status = "Enabled"
    filter {
      prefix = "thumbnails/"
    }
    expiration {
      days = 75
    }
  }

  # Order archives are rebuilt on demand from photos that are still here, so they are
  # a cache rather than data. Expiring them sooner than the originals keeps the bucket
  # from roughly doubling for every order anyone downloads.
  rule {
    id     = "expire-order-archives"
    status = "Enabled"
    filter {
      prefix = "zips/"
    }
    expiration {
      days = 30
    }
  }

  # A multipart upload that never completes leaves its parts in the bucket, billed but
  # absent from any listing. The application aborts them when a build fails, but cannot
  # when the process itself dies mid-write — which has happened. This is the backstop
  # that keeps those from accumulating invisibly forever.
  rule {
    id     = "abort-incomplete-uploads"
    status = "Enabled"
    filter {}
    abort_incomplete_multipart_upload {
      days_after_initiation = 1
    }
  }
}

resource "aws_s3_bucket_notification" "photo_upload" {
  bucket = aws_s3_bucket.photos.id

  lambda_function {
    lambda_function_arn = aws_lambda_function.photo_processor.arn
    events              = ["s3:ObjectCreated:*"]
    filter_prefix       = "originals/"
  }

  depends_on = [aws_lambda_permission.s3_invoke]
}
