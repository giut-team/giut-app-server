#!/usr/bin/env bash
# Exercise certificate bootstrap, redirects, host isolation, and WebSocket upgrade.
set -euo pipefail
IMAGE="${1:?Nginx image is required}"
TEST_DIR=$(mktemp -d)
TEST_ID="giut-nginx-check-$$"
PROXY="$TEST_ID-proxy"
BACKEND="$TEST_ID-backend"
cleanup() {
  docker rm -f "$PROXY" "$BACKEND" >/dev/null 2>&1 || true
  docker network rm "$TEST_ID" >/dev/null 2>&1 || true
  rm -rf "$TEST_DIR"
}
trap cleanup EXIT
mkdir -p "$TEST_DIR/certs/live/api.dev.giut.store" "$TEST_DIR/certs/archive/api.dev.giut.store" \
  "$TEST_DIR/webroot/.well-known/acme-challenge"
echo challenge-ok > "$TEST_DIR/webroot/.well-known/acme-challenge/probe"

cat > "$TEST_DIR/backend.py" <<'PY'
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    def do_GET(self):
        if self.path == "/ws/chat" and self.headers.get("Upgrade") == "websocket":
            self.send_response(101)
            self.send_header("Upgrade", "websocket")
            self.send_header("Connection", "Upgrade")
            self.end_headers()
        else:
            body = (self.headers.get("X-Forwarded-Proto", "") + "|" +
                    self.headers.get("Host", "") + "|" +
                    self.headers.get("X-Forwarded-Port", "")).encode()
            self.send_response(200)
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        self.close_connection = True
ThreadingHTTPServer(("0.0.0.0", 8080), Handler).serve_forever()
PY

docker network create "$TEST_ID" >/dev/null
docker run -d --name "$BACKEND" --network "$TEST_ID" --network-alias giut-dev \
  --mount "type=bind,source=$TEST_DIR/backend.py,target=/backend.py,readonly" \
  python:3.13-alpine python /backend.py >/dev/null
docker run -d --name "$PROXY" --network "$TEST_ID" \
  --mount "type=bind,source=$TEST_DIR/certs,target=/etc/letsencrypt,readonly" \
  --mount "type=bind,source=$TEST_DIR/webroot,target=/var/www/certbot,readonly" \
  -p 127.0.0.1::80 -p 127.0.0.1::443 "$IMAGE" >/dev/null
HTTP_PORT=$(docker port "$PROXY" 80/tcp | awk -F: '{print $NF}')
HTTPS_PORT=$(docker port "$PROXY" 443/tcp | awk -F: '{print $NF}')
ready=false
for ((attempt = 1; attempt <= 30; attempt++)); do
  if curl --noproxy '*' --max-time 2 -fsS -H 'Host: api.dev.giut.store' \
    "http://127.0.0.1:$HTTP_PORT/v3/api-docs" >/dev/null 2>&1; then
    ready=true
    break
  fi
  sleep 1
done
if [ "$ready" != true ]; then
  docker logs "$PROXY"
  docker logs "$BACKEND"
  exit 1
fi
docker exec "$PROXY" nginx -t
test "$(curl --noproxy '*' -fsS -H 'Host: api.dev.giut.store' \
  "http://127.0.0.1:$HTTP_PORT/.well-known/acme-challenge/probe")" = challenge-ok
if curl --noproxy '*' --max-time 2 -fsS -H 'Host: api.giut.store' \
  "http://127.0.0.1:$HTTP_PORT/v3/api-docs" >/dev/null 2>&1; then
  echo "Production Host unexpectedly accepted during bootstrap." >&2
  exit 1
fi

# Use live/archive symlinks, just as Certbot does on the EC2 host.
openssl req -x509 -newkey rsa:2048 -nodes -days 1 \
  -subj /CN=api.dev.giut.store -addext subjectAltName=DNS:api.dev.giut.store \
  -keyout "$TEST_DIR/certs/archive/api.dev.giut.store/privkey1.pem" \
  -out "$TEST_DIR/certs/archive/api.dev.giut.store/fullchain1.pem" >/dev/null 2>&1
ln -s ../../archive/api.dev.giut.store/privkey1.pem "$TEST_DIR/certs/live/api.dev.giut.store/privkey.pem"
ln -s ../../archive/api.dev.giut.store/fullchain1.pem "$TEST_DIR/certs/live/api.dev.giut.store/fullchain.pem"
docker exec "$PROXY" /docker-entrypoint.d/40-giut-config.sh
docker exec "$PROXY" nginx -t
docker exec "$PROXY" nginx -s reload
# Wait for the new worker to accept HTTPS.
for ((attempt = 1; attempt <= 30; attempt++)); do
  if curl --noproxy '*' --max-time 2 -fsS \
    --cacert "$TEST_DIR/certs/live/api.dev.giut.store/fullchain.pem" \
    --resolve "api.dev.giut.store:$HTTPS_PORT:127.0.0.1" \
    "https://api.dev.giut.store:$HTTPS_PORT/v3/api-docs" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

python3 - "$HTTP_PORT" "$HTTPS_PORT" "$TEST_DIR/certs/live/api.dev.giut.store/fullchain.pem" <<'PY'
import http.client, socket, ssl, sys
http_port, https_port = map(int, sys.argv[1:3])
context = ssl.create_default_context(cafile=sys.argv[3])
def request(path, tls=False, host="api.dev.giut.store", upgrade=False):
    connection = http.client.HTTPConnection("127.0.0.1", http_port, timeout=3)
    if tls:
        connection.sock = context.wrap_socket(
            socket.create_connection(("127.0.0.1", https_port), timeout=3),
            server_hostname="api.dev.giut.store")
    headers = {"Host": host}
    if upgrade:
        headers.update({"Upgrade": "websocket", "Connection": "Upgrade"})
    connection.request("GET", path, headers=headers)
    response = connection.getresponse()
    result = (response.status, dict(response.getheaders()),
              b"" if response.status == 101 else response.read())
    connection.close()
    return result

status, headers, _ = request("/v3/api-docs?probe=1")
assert status == 308 and headers["Location"] == "https://api.dev.giut.store/v3/api-docs?probe=1"
assert request("/.well-known/acme-challenge/probe")[2].strip() == b"challenge-ok"
assert request("/v3/api-docs", tls=True)[2] == b"https|api.dev.giut.store|443"
status, headers, _ = request("/ws/chat", tls=True, upgrade=True)
assert status == 101 and headers.get("Upgrade", "").lower() == "websocket"
for tls in (False, True):
    try:
        request("/v3/api-docs", tls=tls, host="api.giut.store")
    except (http.client.RemoteDisconnected, ConnectionResetError):
        pass
    else:
        raise AssertionError("Production Host must be rejected")
try:
    with socket.create_connection(("127.0.0.1", https_port), timeout=3) as sock:
        context.wrap_socket(sock, server_hostname="api.giut.store")
except ssl.SSLError:
    pass
else:
    raise AssertionError("Production SNI must be rejected")
print("PASS: bootstrap, ACME, HTTPS, redirect, forwarded headers, WebSocket upgrade, production Host/SNI isolation")
PY
