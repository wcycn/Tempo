from datetime import datetime, timedelta

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import or_, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from ..config import settings
from ..database import get_db
from ..dependencies import current_user
from ..models import SessionToken, User
from ..schemas import AuthResponse, LoginRequest, RegisterRequest, UserPublic
from ..security import hash_password, new_session_token, verify_password

router = APIRouter(prefix="/api/auth", tags=["auth"])


@router.post("/register", response_model=AuthResponse, status_code=201)
def register(payload: RegisterRequest, db: Session = Depends(get_db)):
    user = User(username=payload.username, email=payload.email.lower(), display_name=payload.display_name,
                password_hash=hash_password(payload.password))
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


def _issue_token(user: User, db: Session) -> AuthResponse:
    raw, token_hash = new_session_token()
    db.add(SessionToken(token_hash=token_hash, user_id=user.id,
                        expires_at=datetime.utcnow() + timedelta(days=settings.session_ttl_days)))
    db.commit()
    return AuthResponse(access_token=raw, user=user)

