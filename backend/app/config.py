from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_env: str = "development"
    database_url: str = "sqlite:///./calendar.db"
    session_ttl_days: int = 30
    allowed_origins: str = "http://localhost:3000,http://localhost:5173"
    zhipu_api_key: str = ""
    zhipu_asr_model: str = "glm-asr-2512"
    zhipu_text_model: str = "glm-4-flash"
    zhipu_base_url: str = "https://open.bigmodel.cn/api/paas/v4"
    ai_audio_max_bytes: int = 25 * 1024 * 1024
    ai_audio_max_seconds: int = 30

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    @property
    def origins(self) -> list[str]:
        return [item.strip() for item in self.allowed_origins.split(",") if item.strip()]


settings = Settings()
