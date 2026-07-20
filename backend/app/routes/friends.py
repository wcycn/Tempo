from datetime import date as date_type, datetime, time, timedelta
from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy import or_, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from ..database import get_db
from ..dependencies import current_user
from ..models import AvailabilityBlock, Friendship, Invite, User
from ..schemas import AvailabilityPublic, AvailabilityUpdate, FriendRequestCreate, FriendResponse, FriendshipPublic, FriendUserPublic

router = APIRouter(prefix="/api/friends", tags=["friends"])


def _range_datetime(date_value: str, clock: str) -> datetime:
    day = date_type.fromisoformat(date_value)
    if clock == "24:00":
        return datetime.combine(day + timedelta(days=1), time.min)
    return datetime.combine(day, time.fromisoformat(clock))


@router.put("/availability", status_code=204)
def replace_availability(payload: AvailabilityUpdate, user: User = Depends(current_user), db: Session = Depends(get_db)):
    dates = {item.date for item in payload.blocks} | set(payload.dates)
    hard_ranges = []
    for item in payload.blocks:
        if item.status == "HARD":
            hard_ranges.append((_range_datetime(item.date, item.start_time), _range_datetime(item.date, item.end_time)))
    if hard_ranges:
        pending = db.scalars(select(Invite).where(
            Invite.status == "PENDING",
            Invite.receiver_id == user.id,
        )).all()
        for invite in pending:
            if any(invite.start_at < end and invite.end_at > start for start, end in hard_ranges):
                # 只有接收方改红会隐式拒绝；发起方必须显式点击取消邀约。
                invite.status = "DECLINED"
    if payload.replace_all:
        db.query(AvailabilityBlock).filter(AvailabilityBlock.user_id == user.id).delete(synchronize_session=False)
    elif dates:
        db.query(AvailabilityBlock).filter(AvailabilityBlock.user_id == user.id, AvailabilityBlock.date.in_(dates)).delete(synchronize_session=False)
    for item in payload.blocks:
        if item.end_time <= item.start_time:
            raise HTTPException(400, "结束时间必须晚于开始时间")
        db.add(AvailabilityBlock(user_id=user.id, **item.model_dump()))
    db.commit()


@router.get("/find", response_model=list[FriendUserPublic])
@router.get("/search", response_model=list[FriendUserPublic])
def search_users(q: str, user: User = Depends(current_user), db: Session = Depends(get_db)):
    query = q.strip()
    if not query:
        return []
    statement = select(User).where(
        User.id != user.id,
        or_(User.username.ilike(f"%{query}%"), User.display_name.ilike(f"%{query}%"))
    ).limit(20)
    return db.scalars(statement).all()


@router.get("/{friend_id}/availability", response_model=list[AvailabilityPublic])
def get_availability(friend_id: int, date: str, user: User = Depends(current_user), db: Session = Depends(get_db)):
    if not db.get(User, friend_id):
        raise HTTPException(404, "用户不存在")
    relation = db.scalar(select(Friendship).where(
        or_((Friendship.user_id == user.id) & (Friendship.friend_id == friend_id),
            (Friendship.user_id == friend_id) & (Friendship.friend_id == user.id)),
        Friendship.status == "ACCEPTED"))
    if not relation:
        raise HTTPException(403, "只有已同意的好友可以查看时间状态")
    return db.scalars(select(AvailabilityBlock).where(AvailabilityBlock.user_id == friend_id, AvailabilityBlock.date == date).order_by(AvailabilityBlock.start_time)).all()


@router.get("", response_model=list[FriendshipPublic])
def list_friends(user: User = Depends(current_user), db: Session = Depends(get_db)):
    rows = db.scalars(select(Friendship).where(or_(Friendship.user_id == user.id, Friendship.friend_id == user.id))).all()
    result = []
    for row in rows:
        other_id = row.friend_id if row.user_id == user.id else row.user_id
        other = db.get(User, other_id)
        if other:
            result.append({"id": row.id, "user_id": row.user_id, "friend_id": row.friend_id,
                           "status": row.status, "friend": other})
    return result


@router.post("/requests", response_model=FriendshipPublic, status_code=201)
def send_request(payload: FriendRequestCreate, user: User = Depends(current_user), db: Session = Depends(get_db)):
    if payload.friend_id == user.id:
        raise HTTPException(400, "不能添加自己")
    target = db.get(User, payload.friend_id)
    if not target:
        raise HTTPException(404, "用户不存在")
    existing = db.scalar(select(Friendship).where(
        or_((Friendship.user_id == user.id) & (Friendship.friend_id == target.id),
            (Friendship.user_id == target.id) & (Friendship.friend_id == user.id))))
    if existing:
        if existing.status == "ACCEPTED":
            raise HTTPException(409, "你们已经是好友")
        if existing.status == "PENDING":
            if existing.user_id == user.id:
                raise HTTPException(409, "好友申请已经发送，等待对方处理")
            raise HTTPException(409, "对方已经向你发送好友申请，请先处理申请")
        existing.user_id = user.id
        existing.friend_id = target.id
        existing.status = "PENDING"
        db.commit()
        db.refresh(existing)
        return {"id": existing.id, "user_id": existing.user_id, "friend_id": existing.friend_id,
                "status": existing.status, "friend": target}
    row = Friendship(user_id=user.id, friend_id=target.id, status="PENDING")
    db.add(row)
    try:
        db.commit()
        db.refresh(row)
    except IntegrityError:
        db.rollback()
        raise HTTPException(409, "好友申请已存在")
    return {"id": row.id, "user_id": row.user_id, "friend_id": row.friend_id,
            "status": row.status, "friend": target}


@router.patch("/{friendship_id}", response_model=FriendshipPublic)
def respond(friendship_id: int, payload: FriendResponse, user: User = Depends(current_user), db: Session = Depends(get_db)):
    row = db.scalar(select(Friendship).where(Friendship.id == friendship_id, Friendship.friend_id == user.id,
                                            Friendship.status == "PENDING"))
    if not row:
        raise HTTPException(404, "好友申请不存在或已处理")
    row.status = payload.status
    db.commit()
    db.refresh(row)
    requester = db.get(User, row.user_id)
    return {"id": row.id, "user_id": row.user_id, "friend_id": row.friend_id,
            "status": row.status, "friend": requester}


@router.delete("/{friendship_id}", status_code=204)
def delete_friend(
    friendship_id: int,
    confirm: bool = Query(default=False),
    user: User = Depends(current_user),
    db: Session = Depends(get_db),
):
    if not confirm:
        raise HTTPException(400, "删除好友前需要明确确认")
    row = db.scalar(select(Friendship).where(Friendship.id == friendship_id,
                                            or_(Friendship.user_id == user.id, Friendship.friend_id == user.id)))
    if not row:
        raise HTTPException(404, "好友关系不存在")
    db.delete(row)
    db.commit()
