from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from ..database import get_db
from ..models import Device
from ..schemas import DeviceRegisterRequest, DeviceResponse
from ..models import utcnow

router = APIRouter(prefix="/devices", tags=["Devices"])


@router.post("/register", response_model=DeviceResponse)
def register_device(req: DeviceRegisterRequest, db: Session = Depends(get_db)):
    """Register an Android device. Returns an API key for future requests."""
    # Check if device with same FCM token already exists → update it
    existing = db.query(Device).filter(Device.fcm_token == req.fcm_token).first()
    if existing:
        existing.name = req.name
        existing.last_seen_at = utcnow()
        db.commit()
        db.refresh(existing)
        return existing

    device = Device(name=req.name, fcm_token=req.fcm_token)
    db.add(device)
    db.commit()
    db.refresh(device)
    return device


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
