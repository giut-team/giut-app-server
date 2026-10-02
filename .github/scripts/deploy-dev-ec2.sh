#!/usr/bin/env bash
set -euo pipefail

AWS_REGION="$1"
IMAGE_URI="$2"
NGINX_IMAGE_URI="$3"
ECR_REGISTRY="${IMAGE_URI%%/*}"
APP_CONTAINER_NAME=giut-dev
NGINX_CONTAINER_NAME=giut-dev-nginx
NETWORK_NAME=giut-dev-network
ENV_FILE=/home/ubuntu/giut-dev.env
SECRET_DIR=/home/ubuntu/giut-dev-secrets

test -f "$ENV_FILE"
install -d -o ubuntu -g ubuntu -m 700 "$SECRET_DIR"
docker network inspect "$NETWORK_NAME" >/dev/null 2>&1 ||
  docker network create "$NETWORK_NAME" >/dev/null

aws ecr get-login-password --region "$AWS_REGION" |
  docker login --username AWS --password-stdin "$ECR_REGISTRY"
docker pull "$IMAGE_URI"
docker pull "$NGINX_IMAGE_URI"

previous_image=$(docker inspect --format '{{.Image}}' "$APP_CONTAINER_NAME" 2>/dev/null || true)
previous_nginx_image=$(docker inspect --format '{{.Image}}' "$NGINX_CONTAINER_NAME" 2>/dev/null || true)

run_app() {
  docker run -d --name "$APP_CONTAINER_NAME" --restart unless-stopped \
    --network "$NETWORK_NAME" \
    --user "$(id -u ubuntu):$(id -g ubuntu)" \
    --env-file "$ENV_FILE" \
    --mount "type=bind,source=$SECRET_DIR,target=/run/secrets,readonly" \
    -p 127.0.0.1:8080:8080 \
    "$1"
}

run_nginx() {
  docker run -d --name "$NGINX_CONTAINER_NAME" --restart unless-stopped \
    --network "$NETWORK_NAME" \
    -p 80:80 \
    "$1"
}

wait_for_url() {
  local url="$1"
  local container_name="$2"
  local max_attempts="$3"

  for ((attempt = 1; attempt <= max_attempts; attempt++)); do
    if curl --noproxy '*' --max-time 5 -fsS "$url" >/dev/null 2>&1; then
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
    if ! run_app "$previous_image"; then
      echo "Failed to restore the previous app container." >&2
    fi
  fi
}

restore_nginx() {
  docker rm -f "$NGINX_CONTAINER_NAME" >/dev/null 2>&1 || true
  if [ -n "$previous_nginx_image" ]; then
    if ! run_nginx "$previous_nginx_image"; then
      echo "Failed to restore the previous Nginx container." >&2
    fi
  fi
}

if [ -n "$previous_image" ]; then
  docker rm -f "$APP_CONTAINER_NAME" >/dev/null
fi

if ! run_app "$IMAGE_URI"; then
  restore_app
  restore_nginx
  exit 1
fi

if ! wait_for_url http://127.0.0.1:8080/v3/api-docs "$APP_CONTAINER_NAME" 90; then
  docker logs --tail 80 "$APP_CONTAINER_NAME" || true
  restore_app
  restore_nginx
  exit 1
fi

if [ -n "$previous_nginx_image" ]; then
  docker rm -f "$NGINX_CONTAINER_NAME" >/dev/null
fi

if ! run_nginx "$NGINX_IMAGE_URI"; then
  docker logs --tail 80 "$NGINX_CONTAINER_NAME" || true
  restore_app
  restore_nginx
  exit 1
fi

if ! wait_for_url http://127.0.0.1/v3/api-docs "$NGINX_CONTAINER_NAME" 15; then
  docker logs --tail 80 "$NGINX_CONTAINER_NAME" || true
  restore_app
  restore_nginx
  exit 1
fi

echo "Giut dev app and Nginx are responding on ports 8080 and 80."
