from pydantic_settings import BaseSettings
from functools import lru_cache


class Settings(BaseSettings):
    firebase_service_account_path: str = "./service-account.json"
    database_url: str = "sqlite:///./sms_gateway.db"
    api_host: str = "0.0.0.0"
    api_port: int = 8000

    class Config:
        env_file = ".env"


@lru_cache()
def get_settings() -> Settings:
    return Settings()
