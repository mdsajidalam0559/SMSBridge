import firebase_admin
from firebase_admin import credentials, messaging
from .config import get_settings

_app = None


def init_firebase():
    global _app
    if _app:
        return
    cred = credentials.Certificate(get_settings().firebase_service_account_path)
    _app = firebase_admin.initialize_app(cred)


def send_push(fcm_token: str, data: dict) -> str:
    """Send a data-only FCM message. Returns the message ID from FCM."""
    message = messaging.Message(
        data={k: str(v) for k, v in data.items()},  # FCM data must be str values
        token=fcm_token,
        android=messaging.AndroidConfig(
            priority="high",  # Bypasses Doze mode
        ),
    )
    return messaging.send(message)
