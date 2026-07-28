# Elite Sport Photos

Event photography platform where photographers upload photos from sporting events and attendees find their photos using facial recognition.

## How it works

1. **Photographers** create events and upload photos (via presigned S3 URLs)
2. **Lambda** processes each photo in parallel: generates watermarked thumbnails and indexes faces via AWS Rekognition
3. **Attendees** take a selfie to find their photos across the event using face search
4. **Purchases** are handled via PayPal — buyers receive original (unwatermarked) photos

## Architecture

```
                    CloudFront (TLS)
                         |
                   EC2 t3.micro
                  /            \
           Spring Boot      PostgreSQL 16
           (Docker)         (Docker)

    S3 ──> S3 Event ──> Lambda (Go)
    (upload)             - thumbnail + watermark
                         - Rekognition face indexing
                         - callback to Spring Boot
```

## Tech stack

- **Backend**: Spring Boot 3.4.3, Java 21, Flyway migrations
- **Frontend**: React + TypeScript + Vite + Tailwind CSS
- **Photo processing**: Go Lambda (parallel, ~6s per photo)
- **Face search**: AWS Rekognition
- **Storage**: S3 with presigned URL uploads, 75-day lifecycle
- **Payments**: PayPal
- **Infrastructure**: OpenTofu (EC2, CloudFront, Lambda, S3, SES, ECR)
- **CI/CD**: GitHub Actions (OIDC auth, Docker build, SSH deploy)

## Project structure

```
src/                    # Spring Boot backend
frontend/               # React frontend
lambda/photo-processor/ # Go Lambda for photo processing
terraform-ec2/          # OpenTofu infrastructure config
```

## Local development

### Prerequisites

- Java 21
- Node.js 20
- Docker (for PostgreSQL + LocalStack)
- Go 1.22 (for Lambda development)

### Running locally

```bash
# Start PostgreSQL
docker run -d --name picmeup-db -e POSTGRES_DB=picmeup -e POSTGRES_USER=picmeup \
  -e POSTGRES_PASSWORD=picmeup -p 5432:5432 postgres:16-alpine

# Backend (uses 'local' profile with LocalStack for S3/Rekognition)
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# Frontend
cd frontend && npm install && npm run dev
```

The app runs at `http://localhost:5173` (frontend) and `http://localhost:8080` (API).

## Deployment

Push to `main` and trigger the **Build and Deploy** workflow, which:

1. Runs tests
2. Builds Docker image and pushes to ECR
3. Deploys to EC2 via SSH
4. Builds and deploys the Go Lambda

### Manual deployment

```bash
# Build and push Docker image
docker build --platform linux/amd64 -t <ecr-url>:latest .
docker push <ecr-url>:latest

# Deploy on EC2
ssh ec2-user@<elastic-ip> "sudo /opt/elitesportphotos/deploy.sh latest"

# Deploy Lambda
cd lambda/photo-processor && make build
aws lambda update-function-code --function-name elitesportphotos-photo-processor \
  --zip-file fileb://function.zip --region ap-southeast-2
```

## Infrastructure

Managed with OpenTofu in `terraform-ec2/`. Key resources:

| Resource | Purpose |
|---|---|
| EC2 t3.micro | Docker Compose (Spring Boot + PostgreSQL) |
| CloudFront | TLS termination, HTTPS redirect |
| Lambda (Go) | Photo processing (thumbnail, watermark, face indexing) |
| S3 | Photo storage (originals + thumbnails) |
| Rekognition | Face detection and search |
| SES | Transactional emails |
| ECR | Docker image registry |

```bash
cd terraform-ec2
tofu plan
tofu apply
```
