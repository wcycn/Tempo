from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import or_, select
from sqlalchemy.orm import Session

from ..database import get_db
from ..dependencies import current_user
from ..models import Invite, User
from ..schemas import InviteCreate, InvitePublic, InviteResponse

router = APIRouter(prefix="/api/invites", tags=["invites"])


@router.get("", response_model=list[InvitePublic])
def list_invites(user: User = Depends(current_user), db: Session = Depends(get_db)):
    return list(db.scalars(select(Invite).where(or_(Invite.sender_id == user.id, Invite.receiver_id == user.id)).order_by(Invite.updated_at.desc())))


@router.post("", response_model=InvitePublic, status_code=201)
def create_invite(payload: InviteCreate, user: User = Depends(current_user), db: Session = Depends(get_db)):
    if payload.receiver_id == user.id or not db.get(User, payload.receiver_id):
        raise HTTPException(400, "接收方不存在或不能邀请自己")
    if payload.end_at <= payload.start_at:
        raise HTTPException(400, "结束时间必须晚于开始时间")
    invite = Invite(sender_id=user.id, status="PENDING", **payload.model_dump())
    db.add(invite); db.commit(); db.refresh(invite)
    return invite


@router.patch("/{invite_id}", response_model=InvitePublic)
def respond(invite_id: int, payload: InviteResponse, user: User = Depends(current_user), db: Session = Depends(get_db)):
    invite = db.scalar(select(Invite).where(Invite.id == invite_id, Invite.receiver_id == user.id))
    if not invite or invite.status != "PENDING":
        raise HTTPException(404, "待应答邀约不存在")
    invite.status = payload.status
    db.commit(); db.refresh(invite)
    return invite

