from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from ..database import get_db
from ..models import Device
from ..schemas import DeviceRegisterRequest, DeviceResponse
from ..models import utcnow

router = APIRouter(prefix="/devices", tags=["Devices"])


from ..auth import get_current_device

@router.post("/register", response_model=DeviceResponse)
def register_device(req: DeviceRegisterRequest, db: Session = Depends(get_db)):
    """Register an Android device. Returns an API key for future requests."""
    # 1. Try to find by hardware_id (Best for re-installs on same phone)
    if req.hardware_id:
        existing = db.query(Device).filter(Device.hardware_id == req.hardware_id).first()
        if existing:
            existing.name = req.name
            existing.fcm_token = req.fcm_token
            existing.last_seen_at = utcnow()
            db.commit()
            db.refresh(existing)
            return existing

    # 2. Try to find by FCM token (Fallback for legacy or token rotation)
    existing = db.query(Device).filter(Device.fcm_token == req.fcm_token).first()
    if existing:
        existing.name = req.name
        existing.last_seen_at = utcnow()
        # Adopt hardware_id if it was missing
        if req.hardware_id:
            existing.hardware_id = req.hardware_id
        db.commit()
        db.refresh(existing)
        return existing

    # 3. Create new device
    device = Device(name=req.name, fcm_token=req.fcm_token, hardware_id=req.hardware_id)
    db.add(device)
    db.commit()
    db.refresh(device)
    return device


@router.post("/heartbeat")
def heartbeat(
    current_device: Device = Depends(get_current_device),
    db: Session = Depends(get_db),
):
    """Periodic heartbeat from the Android app to show it's online."""
    current_device.last_seen_at = utcnow()
    db.commit()
    return {"status": "online", "last_seen": current_device.last_seen_at}


@router.get("/", response_model=list[DeviceResponse])
def list_devices(db: Session = Depends(get_db)):
    """List all registered devices."""
    return db.query(Device).all()


@router.delete("/{device_id}")
def delete_device(device_id: str, db: Session = Depends(get_db)):
    """Delete a registered device."""
    device = db.query(Device).filter(Device.id == device_id).first()
    if not device:
        raise HTTPException(status_code=404, detail="Device not found")
    db.delete(device)
    db.commit()
    return {"detail": "Device deleted"}
