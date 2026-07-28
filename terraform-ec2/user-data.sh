#!/bin/bash
set -euo pipefail

# --- Swap (t3.micro has only 1GB RAM) ---
dd if=/dev/zero of=/swapfile bs=128M count=16
chmod 600 /swapfile
mkswap /swapfile
swapon /swapfile
echo '/swapfile swap swap defaults 0 0' >> /etc/fstab

# --- System packages ---
dnf update -y
dnf install -y docker postgresql16

# --- Docker ---
systemctl enable docker
systemctl start docker
usermod -aG docker ec2-user

# --- Docker Compose plugin ---
DOCKER_CONFIG=/usr/libexec/docker/cli-plugins
mkdir -p $DOCKER_CONFIG
ARCH=$(uname -m)
curl -SL "https://github.com/docker/compose/releases/latest/download/docker-compose-linux-$${ARCH}" \
  -o $DOCKER_CONFIG/docker-compose
chmod +x $DOCKER_CONFIG/docker-compose

# --- Application directory ---
APP_DIR=/opt/${app_name}
mkdir -p $APP_DIR/data/postgres
chown -R ec2-user:ec2-user $APP_DIR

# --- Docker Compose ---
cat > $APP_DIR/docker-compose.yml <<'COMPOSE'
services:
  postgres:
    image: postgres:16-alpine
    restart: unless-stopped
    environment:
      POSTGRES_DB: elitesportphotos
      POSTGRES_USER: elitesportphotos
      POSTGRES_PASSWORD: $${DB_PASSWORD}
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U elitesportphotos"]
      interval: 10s
      timeout: 5s
      retries: 5

  app:
    image: $${ECR_IMAGE:-placeholder}
    restart: unless-stopped
    depends_on:
      postgres:
        condition: service_healthy
    env_file:
      - .env
    environment:
      - DATABASE_URL=jdbc:postgresql://postgres:5432/elitesportphotos
      - DB_USERNAME=elitesportphotos
      - JAVA_OPTS=-Xmx512m
    ports:
      - "80:8080"
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:8080/actuator/health || exit 1"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 120s

volumes:
  pgdata:
    driver: local
    driver_opts:
      type: none
      o: bind
      device: /opt/elitesportphotos/data/postgres
COMPOSE

# --- Env file template ---
cat > $APP_DIR/.env <<EOF
SPRING_PROFILES_ACTIVE=prod
DB_PASSWORD=CHANGE_ME
AWS_REGION=${aws_region}
S3_BUCKET=${s3_bucket}
PAYPAL_CLIENT_ID=CHANGE_ME
PAYPAL_CLIENT_SECRET=CHANGE_ME
PAYPAL_BASE_URL=https://api-m.paypal.com
ADMIN_USERNAME=admin
ADMIN_PASSWORD=CHANGE_ME
EMAIL_SENDER=noreply@elitesportphotos.com
EMAIL_ADMIN_RECIPIENTS=trent@elitesportphotos.com,murillo.cg@gmail.com
CORS_ALLOWED_ORIGINS=https://elitesportphotos.com,https://www.elitesportphotos.com
LAMBDA_CALLBACK_SECRET=CHANGE_ME
EOF

chmod 600 $APP_DIR/.env
chown ec2-user:ec2-user $APP_DIR/.env $APP_DIR/docker-compose.yml

# --- Deploy script ---
cat > $APP_DIR/deploy.sh <<DEPLOY
#!/bin/bash
set -euo pipefail

APP_DIR=/opt/${app_name}
IMAGE_TAG="\$1"
if [ -z "\$IMAGE_TAG" ]; then IMAGE_TAG="latest"; fi

aws ecr get-login-password --region ${aws_region} | \
  docker login --username AWS --password-stdin "${ecr_registry}"

export ECR_IMAGE="${ecr_url}:\$IMAGE_TAG"
docker pull "\$ECR_IMAGE"

cd "\$APP_DIR"
source "\$APP_DIR/.env"
ECR_IMAGE="\$ECR_IMAGE" DB_PASSWORD="\$DB_PASSWORD" docker compose up -d --no-deps app

echo "Waiting for health check..."
for i in \$(seq 1 30); do
  if curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo "App is healthy!"
    exit 0
  fi
  sleep 5
done

echo "Health check failed after 150 seconds"
exit 1
DEPLOY

chmod +x $APP_DIR/deploy.sh
chown ec2-user:ec2-user $APP_DIR/deploy.sh

# --- Daily PostgreSQL backup cron ---
cat > /etc/cron.daily/pg-backup <<'CRON'
#!/bin/bash
APP_DIR=/opt/elitesportphotos
BACKUP_FILE="$APP_DIR/data/backup-$(date +%Y%m%d).sql.gz"
docker compose -f $APP_DIR/docker-compose.yml exec -T postgres \
  pg_dump -U elitesportphotos elitesportphotos | gzip > "$BACKUP_FILE"
aws s3 cp "$BACKUP_FILE" s3://elitesportphotos-photos-prod/backups/
find $APP_DIR/data/backup-*.sql.gz -mtime +7 -delete 2>/dev/null || true
CRON

chmod +x /etc/cron.daily/pg-backup

echo "User-data setup complete"
