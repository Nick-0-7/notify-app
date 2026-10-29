"""Worker configuration and environment settings."""
import os
from pydantic_settings import BaseSettings, SettingsConfigDict
from pydantic import Field


class Settings(BaseSettings):
    supabase_url: str = Field(default="https://your-project-ref.supabase.co")
    supabase_service_role_key: str = Field(default="")
    firebase_credentials_json_path: str = Field(default="worker/firebase_credentials.json")
    firebase_service_account_json: str = Field(default="")

    worker_log_level: str = Field(default="INFO")
    worker_interval_minutes: int = Field(default=30)
    worker_max_concurrent_requests: int = Field(default=5)
    worker_user_agent: str = Field(default="NotifyBot/1.0 (+https://github.com/notify; contact: admin@notify-app.internal)")
    worker_request_timeout_seconds: float = Field(default=15.0)
    worker_max_redirects: int = Field(default=3)
    worker_max_response_bytes: int = Field(default=10 * 1024 * 1024)  # 10MB limit

    model_config = SettingsConfigDict(
        env_file=(".env", "../.env"),
        env_file_encoding="utf-8",
        extra="ignore"
    )


settings = Settings()
