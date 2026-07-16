from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import or_, select
from sqlalchemy.orm import Session

from ..database import get_db
from ..dependencies import current_user
from ..models import CalendarGroup, Friendship, GroupMember, User
from ..schemas import FriendUserPublic, GroupCreate, GroupMemberCreate, GroupPublic

router = APIRouter(prefix="/api/groups", tags=["groups"])


def _public_group(group: CalendarGroup, db: Session):
    members = db.scalars(select(User).join(GroupMember, GroupMember.user_id == User.id)
                         .where(GroupMember.group_id == group.id)).all()
    return {"id": group.id, "owner_id": group.owner_id, "name": group.name,
            "members": [FriendUserPublic.model_validate(member) for member in members]}


@router.get("", response_model=list[GroupPublic])
def list_groups(user: User = Depends(current_user), db: Session = Depends(get_db)):
    groups = db.scalars(select(CalendarGroup).outerjoin(GroupMember, GroupMember.group_id == CalendarGroup.id)
                        .where(or_(CalendarGroup.owner_id == user.id, GroupMember.user_id == user.id))
                        .distinct()).all()
    return [_public_group(group, db) for group in groups]


@router.post("", response_model=GroupPublic, status_code=201)
def create_group(payload: GroupCreate, user: User = Depends(current_user), db: Session = Depends(get_db)):
    group = CalendarGroup(owner_id=user.id, name=payload.name.strip())
    db.add(group)
    db.flush()
    db.add(GroupMember(group_id=group.id, user_id=user.id, role="OWNER"))
    db.commit()
    db.refresh(group)
    return _public_group(group, db)


@router.post("/{group_id}/members", response_model=GroupPublic)
def add_member(group_id: int, payload: GroupMemberCreate, user: User = Depends(current_user), db: Session = Depends(get_db)):
    group = db.get(CalendarGroup, group_id)
    target = db.get(User, payload.user_id)
    if not group or group.owner_id != user.id:
        raise HTTPException(404, "群组不存在或你不是群主")
    if not target:
        raise HTTPException(404, "用户不存在")
    accepted = db.scalar(select(Friendship).where(
        or_((Friendship.user_id == user.id) & (Friendship.friend_id == target.id),
            (Friendship.user_id == target.id) & (Friendship.friend_id == user.id)),
        Friendship.status == "ACCEPTED"))
    if not accepted:
        raise HTTPException(403, "只能邀请已同意的好友加入群组")
    exists = db.scalar(select(GroupMember).where(GroupMember.group_id == group.id, GroupMember.user_id == target.id))
    if not exists:
        db.add(GroupMember(group_id=group.id, user_id=target.id))
        db.commit()
    return _public_group(group, db)
