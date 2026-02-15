from pydantic import BaseModel
from datetime import datetime
from typing import Optional, Literal


# ── Device ──────────────────────────────────────────────

class DeviceRegisterRequest(BaseModel):
    name: str
    fcm_token: str
    hardware_id: Optional[str] = None


class DeviceResponse(BaseModel):
    id: str
    name: str
    api_key: str
    created_at: datetime
    last_seen_at: datetime

    class Config:
        from_attributes = True


# ── SMS ─────────────────────────────────────────────────

class SmsDispatchRequest(BaseModel):
    to: str
    message: str


class SmsSendRequest(BaseModel):
    to: str
    message: str
    device_id: str


class SmsSendResponse(BaseModel):
    message_id: str
    status: str


class SmsStatusUpdate(BaseModel):
    message_id: str
    status: Literal["SENT", "DELIVERED", "FAILED"]
    error: Optional[str] = None
    device_token: str  # For auth — the device proves identity


class MessageResponse(BaseModel):
    id: str
    device_id: str
    recipient: str
    body: str
    status: str
    error: Optional[str] = None
    created_at: datetime
    sent_at: Optional[datetime] = None
    delivered_at: Optional[datetime] = None
    failed_at: Optional[datetime] = None

    class Config:
        from_attributes = True
