from fastapi import Header, HTTPException, Depends
from sqlalchemy.orm import Session
from .database import get_db
from .models import Device


def get_current_device(
    x_api_key: str = Header(..., description="API key from device registration"),
    db: Session = Depends(get_db),
) -> Device:
    """Authenticate requests using the API key header."""
    device = db.query(Device).filter(Device.api_key == x_api_key).first()
    if not device:
        raise HTTPException(status_code=401, detail="Invalid API key")
    return device
