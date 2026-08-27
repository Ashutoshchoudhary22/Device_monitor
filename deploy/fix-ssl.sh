#!/usr/bin/env bash
# Fix SSL + start Device Monitor (run on VPS after failed deploy)
set -euo pipefail

DOMAIN="device.faltu.shop"
APP_DIR="/var/www/device-monitor"

cd "$APP_DIR"

echo "==> HTTP nginx config (SSL ke bina)..."
cp deploy/nginx/device.faltu.shop.init.conf /etc/nginx/sites-available/device.faltu.shop
ln -sf /etc/nginx/sites-available/device.faltu.shop /etc/nginx/sites-enabled/device.faltu.shop
nginx -t
systemctl reload nginx

echo "==> SSL certificate..."
certbot certonly --webroot -w /var/www/html -d "$DOMAIN" \
  --non-interactive --agree-tos -m "${CERTBOT_EMAIL:-admin@faltu.shop}"

echo "==> HTTPS nginx config..."
cp deploy/nginx/device.faltu.shop.conf /etc/nginx/sites-available/device.faltu.shop
nginx -t
systemctl reload nginx

echo "==> PM2 start..."
pm2 delete device-monitor-api device-monitor-dashboard 2>/dev/null || true
pm2 start deploy/ecosystem.config.cjs
pm2 save

echo ""
echo "Done!"
echo "  curl https://$DOMAIN/health"
echo "  curl https://faltu.shop"
