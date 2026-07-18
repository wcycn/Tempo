from fastapi import APIRouter, Depends
from sqlalchemy import select
from sqlalchemy.orm import Session

from ..database import get_db
from ..dependencies import current_user
from ..models import Notification, User
from ..schemas import NotificationPublic

router = APIRouter(prefix="/api/notifications", tags=["notifications"])


@router.get("", response_model=list[NotificationPublic])
def list_notifications(user: User = Depends(current_user), db: Session = Depends(get_db)):
    return db.scalars(select(Notification).where(
        Notification.user_id == user.id, Notification.is_read.is_(False)
    ).order_by(Notification.created_at.desc()).limit(30)).all()


@router.post("/{notification_id}/read", status_code=204)
def mark_read(notification_id: int, user: User = Depends(current_user), db: Session = Depends(get_db)):
    item = db.scalar(select(Notification).where(Notification.id == notification_id, Notification.user_id == user.id))
    if item:
        item.is_read = True
        db.commit()
