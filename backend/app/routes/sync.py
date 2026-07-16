from datetime import datetime

from fastapi import APIRouter, Depends
from sqlalchemy import select
from sqlalchemy.orm import Session

from ..database import get_db
from ..dependencies import current_user
from ..models import CalendarCache, CalendarEvent, Invite, User
from ..schemas import SyncResponse

router = APIRouter(prefix="/api/sync", tags=["sync"])


@router.get("/snapshot", response_model=SyncResponse)
def snapshot(user: User = Depends(current_user), db: Session = Depends(get_db)):
    events = list(db.scalars(select(CalendarEvent).where(CalendarEvent.user_id == user.id)))
    accepted = list(db.scalars(select(Invite).where(
        ((Invite.sender_id == user.id) | (Invite.receiver_id == user.id)), Invite.status == "ACCEPTED")))
    cache = list(db.scalars(select(CalendarCache).where(CalendarCache.user_id == user.id)))
    return SyncResponse(events=events, accepted_invites=accepted,
                        calendar_cache=[{"date": item.cache_date, "solar_label": item.solar_label,
                                         "lunar_label": item.lunar_label, "festival": item.festival,
                                         "solar_term": item.solar_term} for item in cache],
                        server_time=datetime.utcnow())

