# 개발 HTTPS 배포 및 main 머지 영향

## 적용 범위

| 구분 | main | dev |
| --- | --- | --- |
| 외부 주소 | 기존 api.giut.store | api.dev.giut.store |
| 대상 서버 | 운영 EC2_HOST 시크릿 | 52.79.220.77인 EC2_INSTANCE_ID |
| 배포 방식 | SSH로 JAR 전송 → systemd 재시작 | ECR 이미지 → SSM → Docker |
| 워크플로 | .github/workflows/deploy.yml | .github/workflows/deploy-dev.yml |
| 프록시 | 운영 서버의 기존 설정 | deploy/nginx 이미지 |

main에 파일을 머지하는 것만으로 운영 서버에 개발 Nginx 설정이 복사되지는 않는다.
main 워크플로는 개발 Dockerfile, Nginx 이미지, 개발 배포 스크립트를 실행하지 않는다.
수동 실행도 각 워크플로의 지정 브랜치에서만 허용한다.

## 첫 배포 전 준비

1. DNS에 A 레코드를 추가한다.
   - 관리 영역이 giut.store라면 레코드 이름은 api.dev, 값은 52.79.220.77이다.
   - 기존 api.giut.store 레코드를 유지한다.
   - 현재 배포 검사는 직접 연결하는 A 레코드를 전제로 한다. DNS 프록시는 끈다.
   - 잘못된 AAAA 레코드가 있으면 제거하거나 실제 개발 서버 IPv6로 맞춘다.
2. 개발 EC2 보안 그룹 및 호스트 방화벽에서 TCP 80, 443 인바운드를 허용한다.
   - HTTP-01 인증과 자동 갱신에는 80도 계속 필요하다.
   - Spring Boot의 8080은 서버 내부 127.0.0.1에만 공개한다.
3. GitHub 저장소 Settings → Environments → Giut → Environment variables에
   DEV_ACME_EMAIL을 인증서 알림을 받을 이메일 주소로 추가한다.
4. Giut 환경의 AWS_REGION, ECR_REPOSITORY, EC2_INSTANCE_ID, AWS_ROLE_ARN
   시크릿이 개발용인지 확인한다. 운영 EC2_HOST, EC2_USERNAME, EC2_SSH_KEY는
   기존 main 배포에서 사용한다.
5. 개발 서버에 기존 /home/ubuntu/giut-dev.env와
   /home/ubuntu/giut-dev-secrets를 준비한다. Docker, AWS CLI, curl, getent,
   flock, systemd가 필요하다. SSM 스크립트는 root로 실행한다.
   EC2의 IMDSv2 접근이 허용되어야 한다.

공인 IP가 바뀌면 개발 배포가 차단된다. 고정 IP를 유지하거나 DNS와
deploy-dev-ec2.sh의 EXPECTED_PUBLIC_IP 및 운영 배포의 차단 IP를 함께 수정한다.

준비가 끝나면 dev에 push하거나 GitHub Actions의 Deploy Giut Server (Dev)를
dev 브랜치로 수동 실행한다. DNS 미설정이나 다른 EC2 대상은 컨테이너 교체 전에 중단된다.

## 인증서 발급과 갱신

- 첫 실행은 bootstrap.conf로 HTTP 서버를 시작한다.
- Certbot이 /.well-known/acme-challenge/ 경로로 도메인 소유권을 확인하고 발급한다.
- 인증서가 생기면 default.conf로 전환해 Nginx를 검사하고 reload한다.
- 일반 HTTP 요청은 동일한 개발 도메인의 HTTPS로 308 리다이렉트한다.
- /ws/chat의 WebSocket 업그레이드는 HTTPS에서도 유지한다.
- 개발 도메인이 아닌 HTTP Host는 444로 종료한다.
  다른 TLS SNI는 핸드셰이크를 거부하며, 개발 SNI에 운영 Host를 보내도 요청을 종료한다.
- 인증서는 /home/ubuntu/giut-dev-tls/letsencrypt에 저장한다.
  Nginx에는 live 및 archive를 포함한 전체 디렉터리를 읽기 전용으로 마운트한다.
- giut-dev-cert-renew.timer가 서버 시간 기준 03시와 15시에 최대 1시간의 무작위 지연을
  두고 갱신 필요 여부를 확인한다. Certbot이 필요한 경우 갱신하고 Nginx가 reload된다.
- 배포와 갱신은 동일한 flock을 사용해 동시에 컨테이너나 인증서를 변경하지 않는다.

인증서 발급 또는 HTTPS 상태 확인에 실패하면 이전 앱과 Nginx 이미지로 복구를 시도한다.
기존과 같은 컨테이너 교체 방식이므로 배포 중 잠시 연결이 끊길 수 있다.
무중단 전환이 필요하면 별도 컨테이너로 시작한 뒤 프록시를 바꾸는 방식이 필요하다.

개발 EC2에서 확인:

```bash
sudo systemctl list-timers giut-dev-cert-renew.timer
sudo journalctl -u giut-dev-cert-renew.service --since '7 days ago'
sudo docker exec giut-dev-nginx nginx -t
```

갱신의 HTTP-01 검증을 실제 인증서 교체 없이 시험하려면:

```bash
sudo docker run --rm \
  --mount type=bind,source=/home/ubuntu/giut-dev-tls/letsencrypt,target=/etc/letsencrypt \
  --mount type=bind,source=/home/ubuntu/giut-dev-tls/webroot,target=/var/www/certbot \
  --mount type=bind,source=/home/ubuntu/giut-dev-tls/lib,target=/var/lib/letsencrypt \
  --mount type=bind,source=/home/ubuntu/giut-dev-tls/logs,target=/var/log/letsencrypt \
  certbot/certbot:v5.8.0 renew --dry-run --cert-name api.dev.giut.store \
  --webroot --webroot-path /var/www/certbot
```

## 환경 간 요청과 데이터 분리

개발 Docker 실행 시 아래 값은 env 파일 값보다 우선한다.
공용 application.properties의 운영 기본값이 개발 OAuth를 운영 서버로 보내는 것을 막는다.

```properties
KAKAO_REDIRECT_URI=https://api.dev.giut.store/api/oauth/kakao/callback
APPLE_REDIRECT_URI=https://api.dev.giut.store/api/oauth/apple/callback
APP_OAUTH_COOKIE_SECURE=true
SERVER_FORWARD_HEADERS_STRATEGY=framework
```

Kakao와 Apple 관리 화면에도 개발 콜백 URL을 허용해야 한다.
Apple은 해당 도메인 및 Return URL을 Services ID에 등록해야 한다.

다음 값들은 /home/ubuntu/giut-dev.env에서 개발 환경에 맞춰 설정한다.

- APP_OAUTH_SUCCESS_REDIRECT_URI: 개발 프론트엔드의 /oauth/callback 주소.
- APP_CORS_ALLOWED_ORIGINS: 실제 개발 프론트엔드 origin 목록을 쉼표로 구분.
- DB_URL, DB_USERNAME, DB_PASSWORD: 개발 DB 연결. 운영 DB와 같으면 개발 요청이 운영 데이터를 바꿀 수 있다.
- JWT_SECRET: 개발 전용 키. 운영과 같으면 양쪽에서 발급한 토큰이 서로 검증될 수 있다.
- OAuth 클라이언트와 Apple 키 관련 값: 현재 개발 앱에 맞는 값 및 /run/secrets 경로.

OAuth 쿠키 코드에는 Domain 지정이 없어 각 API 호스트의 쿠키로 분리된다.
프론트엔드의 API 주소도 운영은 https://api.giut.store,
개발은 https://api.dev.giut.store로 설정해야 한다.

실제 GitHub 시크릿, 서버 env 파일, 운영 Nginx 및 프론트엔드 배포 값은
이번 로컬 변경만으로 검증되지 않는다. 머지 전에 해당 값이 분리되어 있는지 확인한다.
application.properties는 추적 중인 파일이므로 .gitignore에 적혀 있어도
그 파일의 수정 사항을 커밋하면 main에도 적용된다.

## 검증

외부 배포 완료 후:

```bash
curl -I http://api.dev.giut.store/v3/api-docs
curl -f https://api.dev.giut.store/v3/api-docs
curl -f https://api.giut.store/v3/api-docs
```

첫 요청은 개발 HTTPS 주소로 308, 두 번째 요청은 개발 API 응답이어야 한다.
마지막 요청은 기존 운영 API가 유지되는지 확인한다.

로컬 Docker 및 개발 CI에서는 다음 스모크 테스트를 실행한다.
테스트 인증서와 임시 백엔드로 bootstrap → HTTPS 전환,
ACME 경로, 리다이렉트, 전달 헤더, WebSocket 업그레이드,
운영 Host 및 SNI 차단을 검증한다. 실제 Spring Boot, OAuth 로그인,
Let's Encrypt 발급 또는 운영 서버 연결의 성공을 보장하는 테스트는 아니다.

```bash
docker build -f deploy/nginx/Dockerfile -t giut-dev-nginx-check deploy/nginx
bash .github/scripts/test-dev-nginx.sh giut-dev-nginx-check
```

참고: [Let's Encrypt HTTP-01](https://letsencrypt.org/docs/challenge-types/),
[Certbot 갱신 및 webroot](https://eff-certbot.readthedocs.io/en/stable/using.html).
