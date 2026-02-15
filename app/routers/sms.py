from fastapi import APIRouter, Depends, HTTPException
import time
from sqlalchemy.orm import Session
from ..database import get_db
from ..models import Device, Message, utcnow
from ..schemas import (
    SmsSendRequest,
    SmsSendResponse,
    SmsStatusUpdate,
    MessageResponse,
    SmsDispatchRequest,
)
from ..firebase import send_push
from ..auth import get_current_device

router = APIRouter(prefix="/sms", tags=["SMS"])


@router.post("/send", response_model=SmsSendResponse)
def send_sms(
    req: SmsSendRequest,
    db: Session = Depends(get_db),
    current_device: Device = Depends(get_current_device),
):
    """Send an SMS via a registered Android device."""
    # Find the target device
    device = db.query(Device).filter(Device.id == req.device_id).first()
    if not device:
        raise HTTPException(status_code=404, detail="Device not found")

    # Create message record
    msg = Message(
        device_id=device.id,
        recipient=req.to,
        body=req.message,
        status="PENDING",
    )
    db.add(msg)
    db.commit()
    db.refresh(msg)

    # Send FCM push to the Android device
    try:
        send_push(
            fcm_token=device.fcm_token,
            data={
                "action": "send_sms",
                "message_id": msg.id,
                "recipient": req.to,
                "message": req.message,
            },
        )
        msg.status = "QUEUED"
        db.commit()
    except Exception as e:
        msg.status = "FAILED"
        msg.error = str(e)
        msg.failed_at = utcnow()
        db.commit()
        raise HTTPException(status_code=502, detail=f"FCM push failed: {e}")

    return SmsSendResponse(message_id=msg.id, status=msg.status)


@router.post("/dispatch", response_model=SmsSendResponse)
def dispatch_sms(
    req: SmsDispatchRequest,
    db: Session = Depends(get_db),
):
    """Smartly route SMS to the most recently active device. Retries if first fails."""
    # 1. Get ALL active devices sorted by last_seen_at DESC
    devices = db.query(Device).order_by(Device.last_seen_at.desc()).all()

    if not devices:
        raise HTTPException(status_code=503, detail="No devices registered")

    global_deadline = time.time() + 10  # Max 10 seconds total wait
    last_msg = None

    for device in devices:
        # Check global deadline
        remaining_global = global_deadline - time.time()
        if remaining_global <= 0:
            break

        # Create message record
        msg = Message(
            device_id=device.id,
            recipient=req.to,
            body=req.message,
            status="PENDING",
        )
        db.add(msg)
        db.commit()
        db.refresh(msg)
        last_msg = msg

        # Calculate TTL for this attempt (Max 5s, but capped by global deadline)
        attempt_ttl = min(5, int(remaining_global))
        if attempt_ttl < 1:
            attempt_ttl = 1

        try:
            send_push(
                fcm_token=device.fcm_token,
                data={
                    "action": "send_sms",
                    "message_id": msg.id,
                    "recipient": req.to,
                    "message": req.message,
                },
                ttl=attempt_ttl
            )
            msg.status = "QUEUED"
            db.commit()
        except Exception as e:
            msg.status = "FAILED"
            msg.error = f"FCM Error: {str(e)}"
            msg.failed_at = utcnow()
            db.commit()
            continue # Try next device

        # Poll for status change
        # We poll for 'attempt_ttl' seconds
        poll_start = time.time()
        while (time.time() - poll_start) < attempt_ttl:
            time.sleep(0.1)
            db.refresh(msg)
            if msg.status not in ["PENDING", "QUEUED"]:
                # Success (or explicit failure from phone)
                return SmsSendResponse(message_id=msg.id, status=msg.status)
        
        # If we get here, it timed out
        msg.status = "FAILED"
        msg.error = "Timeout / Device Unreachable"
        msg.failed_at = utcnow()
        db.commit()
        # Continue to next device loop...

    # If we fall out of the loop, all attempts failed
    if last_msg:
        return SmsSendResponse(message_id=last_msg.id, status=last_msg.status)
    else:
         raise HTTPException(status_code=503, detail="All devices failed")


@router.post("/status")
def update_sms_status(req: SmsStatusUpdate, db: Session = Depends(get_db)):
    """Called by the Android app to report SMS delivery status (ACK)."""
    # Verify the device token exists
    device = db.query(Device).filter(Device.fcm_token == req.device_token).first()
    if not device:
        raise HTTPException(status_code=401, detail="Unknown device")

    # Update last seen
    device.last_seen_at = utcnow()

    # Find the message
    msg = db.query(Message).filter(Message.id == req.message_id).first()
    if not msg:
        raise HTTPException(status_code=404, detail="Message not found")

    # Update status
    msg.status = req.status
    if req.status == "SENT":
        msg.sent_at = utcnow()
    elif req.status == "DELIVERED":
        msg.delivered_at = utcnow()
    elif req.status == "FAILED":
        msg.failed_at = utcnow()
        msg.error = req.error

    db.commit()
    return {"detail": f"Message {msg.id} status updated to {req.status}"}


@router.get("/{message_id}", response_model=MessageResponse)
def get_message(
    message_id: str,
    db: Session = Depends(get_db),
    current_device: Device = Depends(get_current_device),
):
    """Get the status of a sent message."""
    msg = db.query(Message).filter(Message.id == message_id).first()
    if not msg:
        raise HTTPException(status_code=404, detail="Message not found")
    return msg


@router.get("/", response_model=list[MessageResponse])
def list_messages(
    limit: int = 50,
    db: Session = Depends(get_db),
    current_device: Device = Depends(get_current_device),
):
    """List recent messages."""
    return (
        db.query(Message)
        .order_by(Message.created_at.desc())
        .limit(limit)
        .all()
    )
