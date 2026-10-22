#!/usr/bin/env bash
set -euo pipefail

AWS_REGION="${1:?AWS region is required}"
IMAGE_URI="${2:?App image is required}"
NGINX_IMAGE_URI="${3:?Nginx image is required}"
ACME_EMAIL="${4:?Certificate notification email is required}"
ECR_REGISTRY="${IMAGE_URI%%/*}"
DOMAIN=api.dev.giut.store
EXPECTED_PUBLIC_IP=54.116.56.48
CERTBOT_IMAGE=certbot/certbot:v5.8.0
APP_CONTAINER_NAME=giut-dev
NGINX_CONTAINER_NAME=giut-dev-nginx
NETWORK_NAME=giut-dev-network
ENV_FILE=/home/ubuntu/giut-dev.env
SECRET_DIR=/home/ubuntu/giut-dev-secrets
TLS_DIR=/home/ubuntu/giut-dev-tls

[[ "$ACME_EMAIL" =~ ^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$ ]]

# Check the actual SSM destination before touching any running containers.
IMDS_TOKEN=$(curl --noproxy '*' --connect-timeout 3 --max-time 5 -fsS -X PUT \
  -H 'X-aws-ec2-metadata-token-ttl-seconds: 60' \
  http://169.254.169.254/latest/api/token)
PUBLIC_IP=$(curl --noproxy '*' --connect-timeout 3 --max-time 5 -fsS \
  -H "X-aws-ec2-metadata-token: $IMDS_TOKEN" \
  http://169.254.169.254/latest/meta-data/public-ipv4)
if [ "$PUBLIC_IP" != "$EXPECTED_PUBLIC_IP" ]; then
  echo "Refusing dev deployment: this instance is not $EXPECTED_PUBLIC_IP." >&2
  exit 1
fi
DNS_IPS=$(getent ahostsv4 "$DOMAIN" | awk '{print $1}' | sort -u || true)
if [ "$DNS_IPS" != "$EXPECTED_PUBLIC_IP" ]; then
  echo "Set the DNS A record $DOMAIN to $EXPECTED_PUBLIC_IP before deploying." >&2
  exit 1
fi

test -f "$ENV_FILE"
install -d -o ubuntu -g ubuntu -m 700 "$SECRET_DIR"
install -d -m 700 "$TLS_DIR" "$TLS_DIR/letsencrypt" "$TLS_DIR/lib" "$TLS_DIR/logs"
install -d -m 755 "$TLS_DIR/webroot"
docker network inspect "$NETWORK_NAME" >/dev/null 2>&1 ||
  docker network create "$NETWORK_NAME" >/dev/null

aws ecr get-login-password --region "$AWS_REGION" |
  docker login --username AWS --password-stdin "$ECR_REGISTRY"
docker pull "$IMAGE_URI"
docker pull "$NGINX_IMAGE_URI"
docker pull "$CERTBOT_IMAGE"

# Share this lock with the renewal service throughout container replacement.
exec 9>/var/lock/giut-dev-certificate.lock
flock -w 300 9

previous_image=$(docker inspect --format '{{.Image}}' "$APP_CONTAINER_NAME" 2>/dev/null || true)
previous_nginx_image=$(docker inspect --format '{{.Image}}' "$NGINX_CONTAINER_NAME" 2>/dev/null || true)

run_app() {
  docker run -d --name "$APP_CONTAINER_NAME" --restart unless-stopped \
    --network "$NETWORK_NAME" \
    --user "$(id -u ubuntu):$(id -g ubuntu)" \
    --env-file "$ENV_FILE" \
    -e KAKAO_REDIRECT_URI="https://$DOMAIN/api/oauth/kakao/callback" \
    -e APPLE_REDIRECT_URI="https://$DOMAIN/api/oauth/apple/callback" \
    -e APP_OAUTH_COOKIE_SECURE=true \
    -e SERVER_FORWARD_HEADERS_STRATEGY=framework \
    --mount "type=bind,source=$SECRET_DIR,target=/run/secrets,readonly" \
    -p 127.0.0.1:8080:8080 "$1"
}

run_nginx() {
  docker run -d --name "$NGINX_CONTAINER_NAME" --restart unless-stopped \
    --network "$NETWORK_NAME" \
    --mount "type=bind,source=$TLS_DIR/letsencrypt,target=/etc/letsencrypt,readonly" \
    --mount "type=bind,source=$TLS_DIR/webroot,target=/var/www/certbot,readonly" \
    -p 80:80 -p 443:443 "$1"
}

wait_for_url() {
  local url="$1" container_name="$2" max_attempts="$3"
  shift 3
  for ((attempt = 1; attempt <= max_attempts; attempt++)); do
    if curl --noproxy '*' --max-time 5 -fsS "$@" "$url" >/dev/null 2>&1; then
      return 0
    fi
    if [ "$(docker inspect --format '{{.State.Running}}' "$container_name" 2>/dev/null || true)" != true ]; then
      return 1
    fi
    sleep 2
  done
  return 1
}

restore_app() {
  docker rm -f "$APP_CONTAINER_NAME" >/dev/null 2>&1 || true
  if [ -n "$previous_image" ]; then
    run_app "$previous_image" || echo "Failed to restore the previous app container." >&2
  fi
}

restore_nginx() {
  docker rm -f "$NGINX_CONTAINER_NAME" >/dev/null 2>&1 || true
  if [ -n "$previous_nginx_image" ]; then
    run_nginx "$previous_nginx_image" || echo "Failed to restore the previous Nginx container." >&2
  fi
}

rollback_on_failure() {
  local status=$?
  trap - EXIT
  if [ "$status" -ne 0 ]; then
    docker logs --tail 80 "$APP_CONTAINER_NAME" || true
    docker logs --tail 80 "$NGINX_CONTAINER_NAME" || true
    restore_app
    restore_nginx
  fi
  exit "$status"
}
trap rollback_on_failure EXIT

if [ -n "$previous_image" ]; then
  docker rm -f "$APP_CONTAINER_NAME" >/dev/null
fi
run_app "$IMAGE_URI"
wait_for_url http://127.0.0.1:8080/v3/api-docs "$APP_CONTAINER_NAME" 90

if [ -n "$previous_nginx_image" ]; then
  docker rm -f "$NGINX_CONTAINER_NAME" >/dev/null
fi
run_nginx "$NGINX_IMAGE_URI"
# Both bootstrap HTTP and the HTTPS redirect must respond to the correct Host.
wait_for_url http://127.0.0.1/v3/api-docs "$NGINX_CONTAINER_NAME" 15 -H "Host: $DOMAIN"

docker run --rm \
  --mount "type=bind,source=$TLS_DIR/letsencrypt,target=/etc/letsencrypt" \
  --mount "type=bind,source=$TLS_DIR/webroot,target=/var/www/certbot" \
  --mount "type=bind,source=$TLS_DIR/lib,target=/var/lib/letsencrypt" \
  --mount "type=bind,source=$TLS_DIR/logs,target=/var/log/letsencrypt" \
  "$CERTBOT_IMAGE" certonly --non-interactive --agree-tos \
  --email "$ACME_EMAIL" --webroot --webroot-path /var/www/certbot \
  --cert-name "$DOMAIN" -d "$DOMAIN" --keep-until-expiring

docker exec "$NGINX_CONTAINER_NAME" /docker-entrypoint.d/40-giut-config.sh
docker exec "$NGINX_CONTAINER_NAME" nginx -t
docker exec "$NGINX_CONTAINER_NAME" nginx -s reload
wait_for_url "https://$DOMAIN/v3/api-docs" "$NGINX_CONTAINER_NAME" 15 \
  --resolve "$DOMAIN:443:127.0.0.1"

docker cp "$NGINX_CONTAINER_NAME:/opt/giut-nginx/renew-certificate.sh" \
  /usr/local/sbin/giut-dev-renew-certificate
chmod 755 /usr/local/sbin/giut-dev-renew-certificate
cat > /etc/systemd/system/giut-dev-cert-renew.service <<'UNIT'
[Unit]
Description=Renew the Giut development TLS certificate
Wants=network-online.target
After=network-online.target docker.service
Requires=docker.service

[Service]
Type=oneshot
ExecStart=/usr/local/sbin/giut-dev-renew-certificate
UNIT
cat > /etc/systemd/system/giut-dev-cert-renew.timer <<'UNIT'
[Unit]
Description=Check the Giut development TLS certificate twice daily

[Timer]
OnCalendar=*-*-* 03,15:00:00
RandomizedDelaySec=1h
Persistent=true

[Install]
WantedBy=timers.target
UNIT
systemctl daemon-reload
systemctl enable --now giut-dev-cert-renew.timer

trap - EXIT
echo "Giut dev HTTPS is ready at https://$DOMAIN; certificate renewal timer installed."
