from __future__ import annotations

from datetime import date
from typing import Optional

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile, status

from ..ai.provider import ZhipuCalendarProvider, ZhipuProviderError
from ..ai.schemas import CalendarDraftResponse
from ..config import settings
from ..dependencies import current_user
from ..models import User

router = APIRouter(prefix="/api/ai", tags=["ai"])
_SUPPORTED_AUDIO_TYPES = {"audio/wav", "audio/x-wav", "audio/mpeg", "audio/mp3", "audio/mp4", "audio/m4a"}


@router.post("/calendar/parse-audio", response_model=CalendarDraftResponse)
async def parse_calendar_audio(
    file: UploadFile = File(...),
    timezone: str = Form(default="Asia/Shanghai", max_length=64),
    today: Optional[str] = Form(default=None, pattern=r"^\d{4}-\d{2}-\d{2}$"),
    _: User = Depends(current_user),
):
    """Return an unpersisted calendar draft from a short voice recording."""
    if file.content_type and file.content_type not in _SUPPORTED_AUDIO_TYPES:
        raise HTTPException(415, "仅支持 WAV、MP3 或 M4A 音频")
    audio = await file.read(settings.ai_audio_max_bytes + 1)
    if len(audio) > settings.ai_audio_max_bytes:
        raise HTTPException(413, "音频不能超过 25 MB")
    if not settings.zhipu_api_key:
        raise HTTPException(status.HTTP_503_SERVICE_UNAVAILABLE, "AI 语音功能尚未配置")
    try:
        effective_date = date.fromisoformat(today) if today else date.today()
    except ValueError as exc:
        raise HTTPException(422, "当前日期格式无效") from exc
    try:
        transcript, draft = await ZhipuCalendarProvider().parse_audio(
            audio=audio,
            filename=file.filename or "recording.wav",
            content_type=file.content_type,
            timezone=timezone,
            today=effective_date,
        )
    except ZhipuProviderError as exc:
        raise HTTPException(503, str(exc)) from exc
    except RuntimeError as exc:
        raise HTTPException(502, "AI 解析暂时失败，请稍后重试") from exc
    finally:
        audio = b""
    return CalendarDraftResponse(draft=draft, transcript=transcript, provider="zhipu")
