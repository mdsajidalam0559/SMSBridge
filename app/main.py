from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from .database import init_db
from .firebase import init_firebase
from .routers import devices, sms

app = FastAPI(
    title="SMS Gateway",
    description="Lightweight self-hosted SMS gateway. Send SMS via your Android phone.",
    version="1.0.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(devices.router)
app.include_router(sms.router)


@app.on_event("startup")
def startup():
    init_db()
    init_firebase()


@app.get("/health")
def health():
    return {"status": "ok"}


# Serve static files (APK download)
import os
static_dir = os.path.join(os.path.dirname(os.path.dirname(__file__)), "static")
if os.path.isdir(static_dir):
    app.mount("/static", StaticFiles(directory=static_dir), name="static")
