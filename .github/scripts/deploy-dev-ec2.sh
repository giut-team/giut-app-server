#!/usr/bin/env bash
set -euo pipefail

AWS_REGION="$1"
IMAGE_URI="$2"
ECR_REGISTRY="${IMAGE_URI%%/*}"
CONTAINER_NAME=giut-dev
ENV_FILE=/home/ec2-user/giut-dev.env
SECRET_DIR=/home/ec2-user/giut-dev-secrets

test -f "$ENV_FILE"
install -d -o ec2-user -g ec2-user -m 700 "$SECRET_DIR"

aws ecr get-login-password --region "$AWS_REGION" |
  docker login --username AWS --password-stdin "$ECR_REGISTRY"
docker pull "$IMAGE_URI"

previous_image=$(docker inspect --format '{{.Image}}' "$CONTAINER_NAME" 2>/dev/null || true)
if [ -n "$previous_image" ]; then
  docker stop "$CONTAINER_NAME"
  docker rm "$CONTAINER_NAME"
fi

run_container() {
  docker run -d --name "$CONTAINER_NAME" --restart unless-stopped \
    --user "$(id -u ec2-user):$(id -g ec2-user)" \
    --env-file "$ENV_FILE" \
    --mount "type=bind,source=$SECRET_DIR,target=/run/secrets,readonly" \
    -p 127.0.0.1:8080:8080 \
    "$1"
}

if ! run_container "$IMAGE_URI"; then
  docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
  if [ -n "$previous_image" ]; then
    run_container "$previous_image"
  fi
  exit 1
fi

for attempt in {1..30}; do
  if curl -fsS http://127.0.0.1:8080/v3/api-docs >/dev/null 2>&1; then
    exit 0
  fi
  sleep 2
done

docker logs --tail 100 "$CONTAINER_NAME" || true
docker rm -f "$CONTAINER_NAME" || true
if [ -n "$previous_image" ]; then
  run_container "$previous_image"
fi
exit 1
