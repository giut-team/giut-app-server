#!/usr/bin/env bash
# Installed on the EC2 host by deploy-dev-ec2.sh, and run by systemd.
set -euo pipefail

TLS_DIR=/home/ubuntu/giut-dev-tls
NGINX_CONTAINER_NAME=giut-dev-nginx
CERTBOT_IMAGE=certbot/certbot:v5.8.0

exec 9>/var/lock/giut-dev-certificate.lock
flock -w 300 9
docker run --rm \
  --mount "type=bind,source=$TLS_DIR/letsencrypt,target=/etc/letsencrypt" \
  --mount "type=bind,source=$TLS_DIR/webroot,target=/var/www/certbot" \
  --mount "type=bind,source=$TLS_DIR/lib,target=/var/lib/letsencrypt" \
  --mount "type=bind,source=$TLS_DIR/logs,target=/var/log/letsencrypt" \
  "$CERTBOT_IMAGE" renew --non-interactive --quiet \
  --cert-name api.dev.giut.store --webroot --webroot-path /var/www/certbot

# Certbot keeps the live symlinks; reload Nginx to read a renewed certificate.
docker exec "$NGINX_CONTAINER_NAME" nginx -t
docker exec "$NGINX_CONTAINER_NAME" nginx -s reload
