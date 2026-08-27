#!/usr/bin/env bash
# Deploy Device Monitor to device.faltu.shop (Ubuntu VPS)
# Usage: bash deploy/setup-server.sh

set -euo pipefail

DOMAIN="device.faltu.shop"
APP_DIR="/var/www/device-monitor"
REPO_URL="${REPO_URL:-}"

echo "==> Device Monitor deploy: $DOMAIN"

if [[ $EUID -ne 0 ]]; then
  echo "Run as root: sudo bash deploy/setup-server.sh"
  exit 1
fi

echo "==> Installing dependencies..."
apt-get update -qq
apt-get install -y -qq curl git nginx certbot python3-certbot-nginx

if ! command -v node &>/dev/null; then
  curl -fsSL https://deb.nodesource.com/setup_20.x | bash -
  apt-get install -y -qq nodejs
fi

if ! command -v pm2 &>/dev/null; then
  npm install -g pm2
fi

echo "==> Setting up app directory..."
mkdir -p "$APP_DIR"

if [[ -n "$REPO_URL" ]]; then
  if [[ -d "$APP_DIR/.git" ]]; then
    git -C "$APP_DIR" pull
  else
    git clone "$REPO_URL" "$APP_DIR"
  fi
else
  echo "Copy project files to $APP_DIR (or set REPO_URL=...)"
  if [[ ! -f "$APP_DIR/backend/package.json" ]]; then
    echo "Error: $APP_DIR/backend/package.json not found."
    exit 1
  fi
fi

cd "$APP_DIR"

echo "==> Installing Node packages..."
cd backend && npm ci --omit=dev && cd ..
cd dashboard && npm ci && npm run build && cd ..

echo "==> Configuring nginx (only device.faltu.shop — other sites untouched)..."
ln -sf /etc/nginx/sites-available/device.faltu.shop /etc/nginx/sites-enabled/device.faltu.shop

if [[ ! -f /etc/letsencrypt/live/$DOMAIN/fullchain.pem ]]; then
  echo "==> SSL cert missing — using HTTP config first..."
  cp deploy/nginx/device.faltu.shop.init.conf /etc/nginx/sites-available/device.faltu.shop
  nginx -t
  systemctl reload nginx

  echo "==> Obtaining SSL certificate..."
  certbot certonly --webroot -w /var/www/html -d "$DOMAIN" \
    --non-interactive --agree-tos -m "${CERTBOT_EMAIL:-admin@faltu.shop}"

  echo "==> Enabling HTTPS config..."
  cp deploy/nginx/device.faltu.shop.conf /etc/nginx/sites-available/device.faltu.shop
else
  cp deploy/nginx/device.faltu.shop.conf /etc/nginx/sites-available/device.faltu.shop
fi

nginx -t
systemctl reload nginx

echo "==> Starting PM2 processes..."
pm2 delete device-monitor-api device-monitor-dashboard 2>/dev/null || true
pm2 start deploy/ecosystem.config.cjs
pm2 save
pm2 startup systemd -u root --hp /root 2>/dev/null || true

echo ""
echo "Deploy complete!"
echo "  Dashboard: https://$DOMAIN"
echo "  API:       https://$DOMAIN/api"
echo "  Health:    https://$DOMAIN/health"
echo ""
echo "Make sure backend/.env and dashboard/.env are configured before starting."
