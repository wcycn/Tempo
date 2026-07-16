from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import or_, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from ..database import get_db
from ..dependencies import current_user
from ..models import Friendship, User
from ..schemas import FriendRequestCreate, FriendshipPublic, FriendUserPublic

router = APIRouter(prefix="/api/friends", tags=["friends"])


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
        raise HTTPException(409, "好友申请或好友关系已存在")
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
