#!/bin/sh
set -eu

certificate_dir=/etc/letsencrypt/live/api.dev.giut.store
if [ -s "$certificate_dir/fullchain.pem" ] && [ -s "$certificate_dir/privkey.pem" ]; then
    cp /opt/giut-nginx/default.conf /etc/nginx/conf.d/default.conf
else
    cp /opt/giut-nginx/bootstrap.conf /etc/nginx/conf.d/default.conf
fi
