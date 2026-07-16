from datetime import datetime

from sqlalchemy import select
from sqlalchemy.orm import Session

from ..models import CalendarEvent


def find_hard_conflict(db: Session, user_id: int, start_at: datetime, end_at: datetime) -> CalendarEvent | None:
    """返回重叠的硬性事务；绿色/黄色事件不阻塞硬性事务检测。"""
    return db.scalar(select(CalendarEvent).where(
        CalendarEvent.user_id == user_id,
        CalendarEvent.status == "HARD",
        CalendarEvent.start_at < end_at,
        CalendarEvent.end_at > start_at,
    ))

