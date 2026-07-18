from datetime import datetime
import hashlib

from fastapi import APIRouter, Depends
from sqlalchemy import select
from sqlalchemy.orm import Session

from ..database import get_db
from ..dependencies import current_user
from ..models import CalendarCache, CalendarEvent, GroupActivity, GroupMember, Invite, User
from ..schemas import InvitePublic, SyncResponse

router = APIRouter(prefix="/api/sync", tags=["sync"])


@router.get("/snapshot", response_model=SyncResponse)
def snapshot(user: User = Depends(current_user), db: Session = Depends(get_db)):
    events = list(db.scalars(select(CalendarEvent).where(CalendarEvent.user_id == user.id)))
    accepted = list(db.scalars(select(Invite).where(
        ((Invite.sender_id == user.id) | (Invite.receiver_id == user.id)), Invite.status == "ACCEPTED")))
    cache = list(db.scalars(select(CalendarCache).where(CalendarCache.user_id == user.id)))
    group_activities = list(db.scalars(select(GroupActivity).join(GroupMember, GroupMember.group_id == GroupActivity.group_id).where(GroupMember.user_id == user.id)))
    version_parts = [
        *(f"event:{item.id}:{item.updated_at.isoformat()}" for item in events),
        *(f"invite:{item.id}:{item.updated_at.isoformat()}" for item in accepted),
        *(f"cache:{item.cache_date}:{item.solar_label}:{item.lunar_label}" for item in cache),
        *(f"group_activity:{item.id}:{item.status}:{item.updated_at.isoformat()}" for item in group_activities),
    ]
    server_version = hashlib.sha256("|".join(sorted(version_parts)).encode()).hexdigest()[:16]
    accepted_payload = []
    for invite in accepted:
        sender = db.get(User, invite.sender_id)
        receiver = db.get(User, invite.receiver_id)
        payload = InvitePublic.model_validate(invite).model_dump()
        payload["sender_display_name"] = sender.display_name if sender else None
        payload["receiver_display_name"] = receiver.display_name if receiver else None
        accepted_payload.append(payload)
    return SyncResponse(events=events, accepted_invites=accepted_payload,
                        calendar_cache=[{"date": item.cache_date, "solar_label": item.solar_label,
                                         "lunar_label": item.lunar_label, "festival": item.festival,
                                         "solar_term": item.solar_term} for item in cache],
                        server_time=datetime.utcnow(), server_version=server_version)
