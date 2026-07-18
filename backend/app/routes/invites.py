from datetime import datetime, date, time, timedelta
from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import or_, select
from sqlalchemy.orm import Session

from ..database import get_db
from ..dependencies import current_user
from ..models import AvailabilityBlock, CalendarEvent, Friendship, Invite, User
from ..schemas import InviteCreate, InvitePublic, InviteResponse, MatchOption, MatchRequest

router = APIRouter(prefix="/api/invites", tags=["invites"])


def _invite_payload(invite: Invite, db: Session) -> dict:
    sender = db.get(User, invite.sender_id)
    receiver = db.get(User, invite.receiver_id)
    payload = InvitePublic.model_validate(invite).model_dump()
    payload["sender_display_name"] = sender.display_name if sender else None
    payload["receiver_display_name"] = receiver.display_name if receiver else None
    return payload


@router.post("/match", response_model=list[MatchOption])
def match_times(payload: MatchRequest, user: User = Depends(current_user), db: Session = Depends(get_db)):
    if payload.receiver_id == user.id or not db.get(User, payload.receiver_id):
        raise HTTPException(400, "接收方不存在或不能匹配自己")
    start_date = date.fromisoformat(payload.from_date)
    if (payload.window_start_date is None) != (payload.window_end_date is None) or (payload.window_start_time is None) != (payload.window_end_time is None) or (payload.window_start_date and payload.window_end_date and payload.window_start_date > payload.window_end_date) or (payload.window_start_time and payload.window_end_time and payload.window_start_time >= payload.window_end_time):
        raise HTTPException(400, "匹配时间范围无效")
    blocks = db.scalars(select(AvailabilityBlock).where(AvailabilityBlock.user_id.in_([user.id, payload.receiver_id]))).all()
    by_user = {user.id: [b for b in blocks if b.user_id == user.id], payload.receiver_id: [b for b in blocks if b.user_id == payload.receiver_id]}
    slots_needed = payload.duration_minutes // 15
    pure = _collect_options(start_date, payload.days, slots_needed, by_user, strict_green=True, window_start_date=payload.window_start_date, window_end_date=payload.window_end_date, window_start_time=payload.window_start_time, window_end_time=payload.window_end_time)
    return pure[:3] if pure else _collect_options(start_date, payload.days, slots_needed, by_user, strict_green=False, window_start_date=payload.window_start_date, window_end_date=payload.window_end_date, window_start_time=payload.window_start_time, window_end_time=payload.window_end_time)[:6]


def _collect_options(start_date, days, slots_needed, by_user, strict_green, window_start_date=None, window_end_date=None, window_start_time=None, window_end_time=None):
    result = []
    now = datetime.now()
    for offset in range(days):
        current = start_date + timedelta(days=offset)
        valid = []
        for index in range(96):
            start = f"{index // 4:02d}:{index % 4 * 15:02d}"
            in_window = (
                (window_start_date is None or window_start_date <= str(current) <= window_end_date)
                and (window_start_time is None or window_start_time <= start < window_end_time)
            )
            slot_start = datetime.combine(current, time(index // 4, (index % 4) * 15))
            if slot_start <= now:
                in_window = False
            statuses = []
            for uid in by_user:
                block = next((b for b in by_user[uid] if b.date == str(current) and b.start_time <= start < b.end_time), None)
                statuses.append(block.status if block else "FLEXIBLE")
            valid.append(in_window and (all(s == "FREE" for s in statuses) if strict_green else all(s != "HARD" for s in statuses)))
        run = 0
        for index, okay in enumerate(valid + [False]):
            run = run + 1 if okay else 0
            if run >= slots_needed:
                end_index = index + 1
                start_index = end_index - slots_needed
                start_dt = datetime.combine(current, time(start_index // 4, (start_index % 4) * 15))
                end_dt = start_dt + timedelta(minutes=slots_needed * 15)
                if start_dt <= now:
                    continue
                result.append(MatchOption(start_at=start_dt, end_at=end_dt, match_type="PURE_GREEN" if strict_green else "WITH_FLEXIBLE", score=100 - offset if strict_green else 70 - offset))
                run = 0
    return result


@router.get("", response_model=list[InvitePublic])
def list_invites(user: User = Depends(current_user), db: Session = Depends(get_db)):
    now = datetime.utcnow()
    expired = db.scalars(select(Invite).where(
        or_(Invite.sender_id == user.id, Invite.receiver_id == user.id),
        Invite.status == "PENDING", Invite.end_at <= now
    )).all()
    for item in expired:
        item.status = "EXPIRED"
    if expired:
        db.commit()
    invites = list(db.scalars(select(Invite).where(or_(Invite.sender_id == user.id, Invite.receiver_id == user.id)).order_by(Invite.updated_at.desc())))
    return [_invite_payload(item, db) for item in invites]


@router.post("", response_model=InvitePublic, status_code=201)
def create_invite(payload: InviteCreate, user: User = Depends(current_user), db: Session = Depends(get_db)):
    target = db.get(User, payload.receiver_id)
    if payload.receiver_id == user.id or not target:
        raise HTTPException(400, "接收方不存在或不能邀请自己")
    relation = db.scalar(select(Friendship).where(
        or_((Friendship.user_id == user.id) & (Friendship.friend_id == target.id),
            (Friendship.user_id == target.id) & (Friendship.friend_id == user.id)),
        Friendship.status == "ACCEPTED"
    ))
    if not relation:
        raise HTTPException(403, "只有已同意的好友可以发起邀约")
    if payload.end_at <= payload.start_at:
        raise HTTPException(400, "结束时间必须晚于开始时间")
    if payload.start_at <= datetime.utcnow():
        raise HTTPException(400, "不能邀约已经过去的时间")
    hard_event = db.scalar(select(CalendarEvent).where(
        CalendarEvent.user_id == user.id, CalendarEvent.status == "HARD",
        CalendarEvent.start_at < payload.end_at, CalendarEvent.end_at > payload.start_at
    ))
    if hard_event:
        raise HTTPException(409, "你的这段时间已经被其他硬性事务占用")
    invite = Invite(sender_id=user.id, status="PENDING", **payload.model_dump())
    db.add(invite); db.commit(); db.refresh(invite)
    return _invite_payload(invite, db)


@router.patch("/{invite_id}", response_model=InvitePublic)
def respond(invite_id: int, payload: InviteResponse, user: User = Depends(current_user), db: Session = Depends(get_db)):
    invite = db.scalar(select(Invite).where(Invite.id == invite_id, or_(Invite.receiver_id == user.id, Invite.sender_id == user.id)))
    if not invite or invite.status != "PENDING":
        raise HTTPException(404, "待应答邀约不存在")
    if invite.start_at <= datetime.utcnow():
        invite.status = "EXPIRED"
        db.commit()
        raise HTTPException(400, "该邀约时间已经过去")
    if payload.status == "CANCELLED" and invite.sender_id != user.id:
        raise HTTPException(403, "只有发起方可以取消邀约")
    if payload.status in {"ACCEPTED", "DECLINED"} and invite.receiver_id != user.id:
        raise HTTPException(403, "只有接收方可以应答邀约")
    if payload.status == "ACCEPTED":
        conflict = db.scalar(select(CalendarEvent).where(
            CalendarEvent.user_id == user.id, CalendarEvent.status == "HARD",
            CalendarEvent.start_at < invite.end_at, CalendarEvent.end_at > invite.start_at
        ))
        if conflict:
            raise HTTPException(409, "你的这段时间已经被硬性日程占用")
    invite.status = payload.status
    if payload.status == "ACCEPTED":
        conflicts = db.scalars(select(Invite).where(
            Invite.id != invite.id, Invite.receiver_id == user.id, Invite.status == "PENDING",
            Invite.start_at < invite.end_at, Invite.end_at > invite.start_at)).all()
        for conflict in conflicts:
            conflict.status = "DECLINED"
    db.commit(); db.refresh(invite)
    return _invite_payload(invite, db)


@router.delete("/{invite_id}", status_code=204)
def delete_invite(invite_id: int, user: User = Depends(current_user), db: Session = Depends(get_db)):
    invite = db.scalar(select(Invite).where(
        Invite.id == invite_id,
        or_(Invite.receiver_id == user.id, Invite.sender_id == user.id)
    ))
    if not invite:
        raise HTTPException(404, "邀约不存在")
    if invite.status in {"PENDING", "ACCEPTED"}:
        raise HTTPException(409, "待应答或已确认邀约不能删除")
    db.delete(invite)
    db.commit()
