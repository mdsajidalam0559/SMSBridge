# SMS Bridge

![Python](https://img.shields.io/badge/Python-3.9%2B-blue?style=for-the-badge&logo=python&logoColor=white)
![FastAPI](https://img.shields.io/badge/FastAPI-0.109-009688?style=for-the-badge&logo=fastapi&logoColor=white)
![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Firebase](https://img.shields.io/badge/Firebase-Cloud%20Messaging-FFCA28?style=for-the-badge&logo=firebase&logoColor=black)
![License](https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge)

A robust, self-hosted SMS gateway solution that transforms your Android device into a programmable SMS sending engine. Built with **FastAPI** and **Kotlin**, leveraging **Firebase Cloud Messaging** for real-time push delivery.

---

##  Table of Contents

- [Architecture](#-architecture)
- [Features](#-features)
- [Tech Stack](#-tech-stack)
- [Prerequisites](#-prerequisites)
- [Installation](#-installation)
  - [Backend Setup](#backend-setup)
  - [Android Setup](#android-setup)
- [API Reference](#-api-reference)
- [Troubleshooting](#-troubleshooting)
- [License](#-license)

---

##  Architecture

The system uses a push-based architecture to ensure low latency and battery efficiency. The Android app does not poll the server; instead, it waits for high-priority FCM data messages to trigger SMS sending.

![Flow Diagram](./static/flow_diagram.png)

---

##  Features

-  RESTful API: Clean, documented endpoints for queuing messages and managing devices.
-  Real-time Delivery: Uses FCM for instant message delivery (sub-5s latency).
-  Status Tracking: Granular status updates: `QUEUED` → `SENT` → `DELIVERED` (or `FAILED`).
-  Multi-Device Support: Register multiple Android phones and route messages to specific devices via `device_id`.
-  Secure: API Key authentication for all endpoints.
-  Battery Efficient: Event-driven architecture; no background polling required.

---

##  Tech Stack

**Backend**
- **Framework**: FastAPI (Python)
- **Database**: SQLite (via SQLAlchemy)
- **Push Notifications**: Firebase Admin SDK
- **Server**: Uvicorn

**Mobile**
- **OS**: Android (Min SDK 26)
- **Language**: Kotlin
- **Networking**: OkHttp
- **Background Work**: Firebase Messaging Service + Broadcast Receivers

---

##  Prerequisites

- **Python 3.9+** installed on your server/machine.
- **Android Device** (Android 8.0 Oreo or higher) with Google Play Services.
- **Firebase Project**:
  - `service-account.json` (for Backend)
  - `google-services.json` (for Android App)

---

##  Installation

### Backend Setup

1. **Clone the repository**
   ```bash
   git clone <your-repo-url>
   cd sms_gateway
   ```

2. **Install Dependencies**
   ```bash
   pip install -r requirements.txt
   ```

3. **Configuration**
   Copy the example environment file:
   ```bash
   cp .env.example .env
   ```
   Edit `.env` and set the path to your Firebase credentials:
   ```ini
   FIREBASE_SERVICE_ACCOUNT_PATH=./service-account.json
   DATABASE_URL=sqlite:///./sms_gateway.db
   ```

4. **Start the Server**
   ```bash
   uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
   ```
    The API will be available at `http://localhost:8000`.

### Android Setup

**Option A: Install Pre-built APK**
1. Download the latest `sms-gateway.apk` from the `static/` directory (or serve it via the backend).
2. Install it on your Android device (Enable "Install from unknown sources").

**Option B: Build from Source**
1. Open `sms_gateway/android` in **Android Studio**.
2. Place your `google-services.json` in `android/app/`.
3. Build and Run on your device.

**Device Registration:**
1. Open the app.
2. Enter your server URL (e.g., `http://192.168.1.100:8000`).
3. Tap **Register Device**.
4. Grant **SMS** and **Notification** permissions when prompted.

---

##  API Reference

Interactive validation documentation is available at `/docs` (Swagger UI).

### 1. Send SMS
**Endpoint**: `POST /sms/send`

**Headers**: `X-API-Key: <your_api_key>`

**Body**:
```json
{
  "to": "+1234567890",
  "message": "Hello from SMS Gateway!",
  "device_id": "your-device-uuid"
}
```

### 2. Check Message Status
**Endpoint**: `GET /sms/{message_id}`

**Response**:
```json
{
  "id": "uuid",
  "status": "DELIVERED",
  "sent_at": "2024-03-20T10:00:00",
  "delivered_at": "2024-03-20T10:00:05"
}
```

### 3. List Devices
**Endpoint**: `GET /devices/`

**Response**:
```json
[
  {
    "id": "uuid",
    "name": "Google Pixel 8",
    "api_key": "sg_..."
  }
]
```

---

##  Troubleshooting

| Issue | Solution |
|-------|----------|
| **Server Unreachable** | Ensure phone and server are on the same WiFi network. Use your local IP (e.g., `192.168.x.x`), not `localhost`. |
| **Permission Denied** | On Android 13+, you may need to manually enable "Allow restricted settings" for the app in system settings if installed via APK. |
| **Message Pending/Queued** | Check if the phone has internet access and can receive FCM notifications. Verify `API Key` in `service-account.json`. |
| **Cleartext Traffic Error** | If using HTTP, ensure `android:usesCleartextTraffic="true"` is set in `AndroidManifest.xml` (already configured in this project). |

---

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
