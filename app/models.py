import uuid
from datetime import datetime, timezone
from sqlalchemy import Column, String, DateTime, Text, ForeignKey
from sqlalchemy.orm import relationship
from .database import Base


def generate_uuid():
    return str(uuid.uuid4())


def generate_api_key():
    return f"sg_{uuid.uuid4().hex}"


def utcnow():
    return datetime.now(timezone.utc)


class Device(Base):
    __tablename__ = "devices"

    id = Column(String, primary_key=True, default=generate_uuid)
    name = Column(String, nullable=False)
    fcm_token = Column(Text, nullable=False)
    api_key = Column(String, unique=True, nullable=False, default=generate_api_key)
    created_at = Column(DateTime, default=utcnow)
    last_seen_at = Column(DateTime, default=utcnow)

    messages = relationship("Message", back_populates="device")


class Message(Base):
    __tablename__ = "messages"

    id = Column(String, primary_key=True, default=generate_uuid)
    device_id = Column(String, ForeignKey("devices.id"), nullable=False)
    recipient = Column(String, nullable=False)
    body = Column(Text, nullable=False)
    status = Column(String, default="PENDING")  # PENDING, QUEUED, SENT, DELIVERED, FAILED
    error = Column(Text, nullable=True)
    created_at = Column(DateTime, default=utcnow)
    sent_at = Column(DateTime, nullable=True)
    delivered_at = Column(DateTime, nullable=True)
    failed_at = Column(DateTime, nullable=True)

    device = relationship("Device", back_populates="messages")
