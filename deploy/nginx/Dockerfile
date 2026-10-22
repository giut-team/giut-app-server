FROM nginx:stable-alpine

COPY default.conf bootstrap.conf /opt/giut-nginx/
COPY proxy-locations.conf /etc/nginx/snippets/giut-proxy.conf
COPY select-config.sh /docker-entrypoint.d/40-giut-config.sh
COPY renew-certificate.sh /opt/giut-nginx/renew-certificate.sh
RUN chmod 755 /docker-entrypoint.d/40-giut-config.sh
