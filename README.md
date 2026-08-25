# Device Monitor

A permission-based personal device monitoring system for your own Android devices. Includes an Android app, Node.js backend, and Next.js dashboard with real-time location, battery, and device status.

**This app is for monitoring devices you own with explicit user consent. It does not implement hidden surveillance, stealth mode, anti-uninstall, keylogging, WhatsApp message reading, or permission bypassing.**

## Project Structure

```
device-monitor/
├── android-app/     # Kotlin Android app
├── backend/         # Node.js + Express + MongoDB + Socket.IO
└── dashboard/       # Next.js + React + Tailwind dashboard
```

## Requirements

- **Android**: Android Studio (Hedgehog+), JDK 17, min SDK 26
- **Backend**: Node.js 18+, MongoDB 6+
- **Dashboard**: Node.js 18+

---

## 1. MongoDB Setup

```bash
# Install MongoDB locally or use MongoDB Atlas
# Default connection: mongodb://localhost:27017/device-monitor
```

---

## 2. Backend Setup

```bash
cd device-monitor/backend
cp .env.example .env
# Edit .env — set JWT_SECRET and MONGODB_URI

npm install
npm run dev
```

Server runs at **http://localhost:3001**

### Backend Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `PORT` | Server port | `3001` |
| `MONGODB_URI` | MongoDB connection string | `mongodb://localhost:27017/device-monitor` |
| `JWT_SECRET` | JWT signing secret | (required in production) |
| `JWT_EXPIRES_IN` | Token expiry | `7d` |
| `CORS_ORIGIN` | Dashboard URL | `http://localhost:3000` |
| `LOCATION_RETENTION_DAYS` | Auto-delete locations older than N days | `90` |
| `LOCATION_MAX_PER_DEVICE` | Max location points per device | `50000` |

### Run Backend Tests

```bash
cd device-monitor/backend
npm test
```

---

## 3. Dashboard Setup

```bash
cd device-monitor/dashboard
cp .env.example .env.local
# NEXT_PUBLIC_API_URL=http://localhost:3001/api
# NEXT_PUBLIC_SOCKET_URL=http://localhost:3001

npm install
npm run dev
```

Dashboard runs at **http://localhost:3000**

### Dashboard Pages

- `/login` — Sign in
- `/register` — Create account
- `/dashboard` — Overview cards + live map
- `/devices` — All devices
- `/devices/[deviceId]` — Live device detail + map
- `/devices/[deviceId]/history` — Location history by date

---

## 4. Android Studio Setup

1. Open **Android Studio**
2. **File → Open** → select `device-monitor/android-app`
3. Wait for Gradle sync to complete
4. Configure API URL in `app/build.gradle.kts`:

```kotlin
buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:3001/api/\"")
```

### Network Configuration

| Target | API Base URL |
|--------|--------------|
| Android Emulator | `http://10.0.2.2:3001/api/` |
| Physical phone (same LAN) | `http://YOUR_PC_LAN_IP:3001/api/` |

Find your PC IP: `ipconfig` (Windows) or `ifconfig` (macOS/Linux).

**Important**: Use your PC's LAN IP (e.g. `192.168.1.100`), not `localhost`.

---

## 5. Build APK

### In Android Studio

1. **Build → Build Bundle(s) / APK(s) → Build APK(s)**
2. APK output: `android-app/app/build/outputs/apk/debug/app-debug.apk`

### Command line (with Gradle wrapper)

```bash
cd device-monitor/android-app
./gradlew assembleDebug
```

---

## 6. Install APK

### Via Android Studio

1. Connect device via USB
2. Enable **Developer Options → USB debugging**
3. Click **Run** (green play button)

### Via ADB

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## 7. Android Permissions (Manual Grant)

The app requests permissions at runtime. You may need to grant these manually in **Settings → Apps → Device Monitor → Permissions**:

| Permission | Purpose | Required for |
|------------|---------|--------------|
| **Location (While using)** | GPS coordinates | Location tracking |
| **Location (All the time)** | Background tracking | Continuous tracking when app closed |
| **Notifications** | Foreground service notification | Persistent tracking indicator (Android 13+) |

### Permission Flow

1. Sign in with dashboard credentials
2. Tap **Start Tracking**
3. Grant location when prompted
4. Grant **Allow all the time** for background location
5. A persistent notification appears while tracking is active

---

## 8. Features

### Implemented

- Device registration with unique ID
- JWT authentication (encrypted storage on Android)
- Live location via Foreground Service + FusedLocationProvider
- Configurable update interval (5–60 seconds)
- Battery percentage, charging state, temperature, health
- Device online/offline status + network type
- Location history in MongoDB with retention limits
- Real-time Socket.IO updates on dashboard
- Offline location queue with Room + sync on reconnect
- Exponential backoff retry for failed uploads
- Dark mode dashboard with Leaflet maps

### Intentionally Disabled

- **Call history** — Restricted by Google Play / Android for most apps
- **WhatsApp messages** — Cannot access private app databases per Android security

---

## 9. API Endpoints

| Method | Endpoint | Auth |
|--------|----------|------|
| POST | `/api/auth/register` | No |
| POST | `/api/auth/login` | No |
| POST | `/api/devices/register` | JWT |
| GET | `/api/devices` | JWT |
| GET | `/api/devices/:deviceId` | JWT |
| POST | `/api/devices/:deviceId/location` | JWT |
| GET | `/api/devices/:deviceId/location/history` | JWT |
| POST | `/api/devices/:deviceId/status` | JWT |
| POST | `/api/devices/:deviceId/battery` | JWT |

### Socket.IO Events

- `device:online`
- `device:offline`
- `device:location`
- `device:battery`
- `device:status`

---

## 10. Production Deployment

### Backend

1. Set strong `JWT_SECRET` in production `.env`
2. Use MongoDB Atlas or managed MongoDB
3. Deploy to VPS, Railway, Render, etc.
4. Enable HTTPS (nginx/Caddy reverse proxy)
5. Set `CORS_ORIGIN` to your dashboard domain

### HTTPS Setup (nginx example)

```nginx
server {
    listen 443 ssl;
    server_name api.yourdomain.com;

    ssl_certificate /path/to/cert.pem;
    ssl_certificate_key /path/to/key.pem;

    location / {
        proxy_pass http://localhost:3001;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
    }
}
```

### Dashboard

```bash
cd dashboard
npm run build
npm start
# Or deploy to Vercel with env vars set
```

### Android Production APK

1. Update `API_BASE_URL` to `https://api.yourdomain.com/api/`
2. Remove `android:usesCleartextTraffic="true"` from AndroidManifest for HTTPS-only
3. **Build → Generate Signed Bundle / APK**

---

## 11. Troubleshooting

### Android can't reach backend

- Emulator: use `10.0.2.2`, not `localhost`
- Physical device: same WiFi network, use PC LAN IP
- Windows firewall: allow port 3001 inbound
- Verify backend: `curl http://localhost:3001/health`

### Location not updating

- Grant **Allow all the time** location permission
- Disable battery optimization for Device Monitor
- Check persistent notification is visible (tracking active)

### Dashboard not showing live updates

- Verify `NEXT_PUBLIC_SOCKET_URL` matches backend URL
- Check browser console for Socket.IO connection errors
- Ensure JWT token is valid (re-login)

### MongoDB connection failed

- Confirm MongoDB is running: `mongosh`
- Check `MONGODB_URI` in backend `.env`

### Token expired on Android

- Log out and log in again in the app

---

## 12. Security Notes

- Never commit `.env` files with real secrets
- Never expose MongoDB credentials to the frontend
- Use HTTPS in production
- JWT secret must be long and random
- Rate limiting is enabled on the backend
- All inputs are validated and sanitized

---

## License

For personal use on devices you own with explicit consent.
