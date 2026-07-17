from __future__ import annotations

from typing import Optional

from pydantic import BaseModel, Field


class CalendarDraft(BaseModel):
    """An AI suggestion; it is never persisted without explicit user confirmation."""

    title: str = Field(min_length=1, max_length=160)
    description: Optional[str] = Field(default=None, max_length=500)
    date: Optional[str] = Field(default=None, pattern=r"^\d{4}-\d{2}-\d{2}$")
    start_time: Optional[str] = Field(default=None, pattern=r"^\d{2}:\d{2}$")
    end_time: Optional[str] = Field(default=None, pattern=r"^\d{2}:\d{2}$")
    category: str = Field(default="自定义", max_length=40)
    status: str = Field(default="HARD", pattern=r"^(HARD|FREE|FLEXIBLE)$")
    flexible_tail_minutes: int = Field(default=0, ge=0, le=60)
    confidence: float = Field(default=0.0, ge=0.0, le=1.0)
    missing_fields: list[str] = Field(default_factory=list)


class CalendarDraftResponse(BaseModel):
    draft: CalendarDraft
    transcript: Optional[str] = None
    provider: str
