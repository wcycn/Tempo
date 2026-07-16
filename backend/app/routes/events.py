from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select
from sqlalchemy.orm import Session

from ..database import get_db
from ..dependencies import current_user
from ..models import CalendarEvent, User
from ..schemas import EventCreate, EventPublic
from ..services.event_service import find_hard_conflict

router = APIRouter(prefix="/api/events", tags=["events"])


@router.get("", response_model=list[EventPublic])
def list_events(user: User = Depends(current_user), db: Session = Depends(get_db)):
    return list(db.scalars(select(CalendarEvent).where(CalendarEvent.user_id == user.id).order_by(CalendarEvent.start_at)))


@router.post("", response_model=EventPublic, status_code=201)
def create_event(payload: EventCreate, user: User = Depends(current_user), db: Session = Depends(get_db)):
    if payload.end_at <= payload.start_at:
        raise HTTPException(400, "结束时间必须晚于开始时间")
    if payload.status == "HARD":
        conflict = find_hard_conflict(db, user.id, payload.start_at, payload.end_at)
        if conflict:
            raise HTTPException(409, "与已有硬性事务冲突")
    event = CalendarEvent(user_id=user.id, **payload.model_dump())
    db.add(event); db.commit(); db.refresh(event)
    return event


@router.put("/{event_id}", response_model=EventPublic)
def update_event(event_id: int, payload: EventCreate, user: User = Depends(current_user), db: Session = Depends(get_db)):
    if payload.end_at <= payload.start_at:
        raise HTTPException(400, "结束时间必须晚于开始时间")
    event = db.scalar(select(CalendarEvent).where(CalendarEvent.id == event_id, CalendarEvent.user_id == user.id))
    if not event:
        raise HTTPException(404, "日程不存在")
    if payload.status == "HARD":
        conflict = find_hard_conflict(db, user.id, payload.start_at, payload.end_at)
        if conflict and conflict.id != event_id:
            raise HTTPException(409, "与已有硬性事务冲突")
    for key, value in payload.model_dump().items():
        setattr(event, key, value)
    db.commit(); db.refresh(event)
    return event


@router.delete("/{event_id}", status_code=204)
def delete_event(event_id: int, user: User = Depends(current_user), db: Session = Depends(get_db)):
    event = db.scalar(select(CalendarEvent).where(CalendarEvent.id == event_id, CalendarEvent.user_id == user.id))
    if not event:
        raise HTTPException(404, "日程不存在")
    db.delete(event); db.commit()
