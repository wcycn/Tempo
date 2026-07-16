from datetime import datetime, timedelta

import hashlib
import secrets
from typing import Optional

from fastapi import APIRouter, Depends, Header, HTTPException, status
from sqlalchemy import func, or_, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from ..config import settings
from ..database import get_db
from ..dependencies import current_user
from ..models import SessionToken, User
from ..schemas import AuthResponse, LoginRequest, ProfileUpdateRequest, RegisterRequest, SessionPublic, UserPublic
from ..security import hash_password, new_session_token, verify_password

router = APIRouter(prefix="/api/auth", tags=["auth"])


@router.post("/register", response_model=AuthResponse, status_code=201)
def register(payload: RegisterRequest, db: Session = Depends(get_db)):
    last_account_id = db.scalar(select(func.max(User.account_id))) or 100000
    next_account_id = last_account_id + 1
    if next_account_id > 999999:
        raise HTTPException(503, "账号 ID 已分配完")
    user = User(username=payload.username, email=payload.email.lower(), display_name=payload.display_name,
                account_id=next_account_id, password_hash=hash_password(payload.password))
    db.add(user)
    try:
        db.commit()
        db.refresh(user)
    except IntegrityError:
        db.rollback()
        raise HTTPException(status_code=409, detail="用户名或邮箱已存在")
    return _issue_token(user, db)


@router.post("/login", response_model=AuthResponse)
def login(payload: LoginRequest, db: Session = Depends(get_db)):
    user = db.scalar(select(User).where(or_(User.username == payload.account, User.email == payload.account.lower())))
    if not user or not verify_password(payload.password, user.password_hash):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="账号或密码错误")
    return _issue_token(user, db)


@router.get("/me", response_model=UserPublic)
def me(user: User = Depends(current_user)):
    return user


@router.patch("/me", response_model=UserPublic)
def update_profile(payload: ProfileUpdateRequest, user: User = Depends(current_user), db: Session = Depends(get_db)):
    updates = payload.model_dump(exclude_unset=True)
    if "display_name" in updates:
        display_name = (updates["display_name"] or "").strip()
        if not display_name:
            raise HTTPException(422, "昵称不能为空")
        updates["display_name"] = display_name
    for key, value in updates.items():
        setattr(user, key, value.strip() if isinstance(value, str) else value)
    db.commit()
    db.refresh(user)
    return user


@router.get("/sessions", response_model=list[SessionPublic])
def list_sessions(authorization: Optional[str] = Header(default=None), user: User = Depends(current_user), db: Session = Depends(get_db)):
    current_hash = _token_hash_from_header(authorization)
    sessions = db.scalars(select(SessionToken).where(SessionToken.user_id == user.id).order_by(SessionToken.created_at.desc())).all()
    return [SessionPublic(session_key=item.session_key, created_at=item.created_at, expires_at=item.expires_at,
                          is_current=item.token_hash == current_hash) for item in sessions]


@router.delete("/sessions/{session_key}", status_code=204)
def revoke_session(session_key: str, user: User = Depends(current_user), db: Session = Depends(get_db)):
    session = db.scalar(select(SessionToken).where(SessionToken.session_key == session_key, SessionToken.user_id == user.id))
    if not session:
        raise HTTPException(404, "登录设备不存在")
    db.delete(session)
    db.commit()


@router.post("/logout", status_code=204)
def logout(authorization: Optional[str] = Header(default=None), user: User = Depends(current_user), db: Session = Depends(get_db)):
    session = db.scalar(select(SessionToken).where(SessionToken.token_hash == _token_hash_from_header(authorization), SessionToken.user_id == user.id))
    if session:
        db.delete(session)
        db.commit()


def _issue_token(user: User, db: Session) -> AuthResponse:
    raw, token_hash = new_session_token()
    db.add(SessionToken(token_hash=token_hash, session_key=secrets.token_urlsafe(24), user_id=user.id,
                        expires_at=datetime.utcnow() + timedelta(days=settings.session_ttl_days)))
    db.commit()
    return AuthResponse(access_token=raw, user=user)


def _token_hash_from_header(authorization: Optional[str]) -> str:
    if not authorization or not authorization.lower().startswith("bearer "):
        raise HTTPException(status_code=401, detail="需要登录")
    return hashlib.sha256(authorization[7:].strip().encode()).hexdigest()
