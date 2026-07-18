from datetime import date, datetime, time, timedelta
import secrets

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import or_, select
from sqlalchemy.orm import Session

from ..database import get_db
from ..dependencies import current_user
from ..models import AvailabilityBlock, CalendarEvent, CalendarGroup, Friendship, GroupActivity, GroupActivityParticipant, GroupInvitation, GroupMember, Notification, User
from ..schemas import (
    FriendUserPublic,
    GroupActivityCreate,
    GroupActivityPublic,
    GroupActivityResponse,
    GroupCreate,
    GroupInvitationPublic,
    GroupInvitationResponse,
    GroupMemberCreate,
    GroupPublic,
)

router = APIRouter(prefix="/api/groups", tags=["groups"])


def _is_member(group_id: int, user_id: int, db: Session) -> bool:
    return db.scalar(select(GroupMember).where(GroupMember.group_id == group_id, GroupMember.user_id == user_id)) is not None


def _is_owner(group: CalendarGroup, user: User) -> bool:
    return group.owner_id == user.id


def _public_group(group: CalendarGroup, db: Session):
    members = db.scalars(select(User).join(GroupMember, GroupMember.user_id == User.id)
                         .where(GroupMember.group_id == group.id)).all()
    return {"id": group.id, "owner_id": group.owner_id, "name": group.name,
            "members": [FriendUserPublic.model_validate(member) for member in members]}


def _public_activity(activity: GroupActivity, db: Session):
    rows = db.scalars(select(GroupActivityParticipant).where(GroupActivityParticipant.activity_id == activity.id)).all()
    users = {user.id: user for user in db.scalars(select(User).where(User.id.in_([row.user_id for row in rows]))).all()}
    creator = db.get(User, activity.creator_id)
    return {
        **GroupActivityPublic.model_validate(activity).model_dump(),
        "creator_display_name": creator.display_name if creator else "未知用户",
        "participants": [FriendUserPublic.model_validate(users[row.user_id]) for row in rows if row.user_id in users and row.status in {"JOINED", "PENDING_CONFIRM", "CONFIRMED"}],
        "pending_confirmation_ids": [row.user_id for row in rows if row.status == "PENDING_CONFIRM"],
        "confirmed_count": sum(row.status == "CONFIRMED" for row in rows),
        "pending_count": sum(row.status == "PENDING_CONFIRM" for row in rows),
        "declined_count": sum(row.status == "DECLINED" for row in rows),
        "confirmed_participant_ids": [row.user_id for row in rows if row.status == "CONFIRMED"],
    }


def _new_activity_code(db: Session) -> str:
    for _ in range(20):
        code = str(secrets.randbelow(90_000_000) + 10_000_000)
        if db.scalar(select(GroupActivity).where(GroupActivity.activity_code == code)) is None:
            return code
    raise HTTPException(503, "暂时无法生成活动编号，请稍后重试")


def _parse_range(start: datetime, end: datetime):
    return start, end


def _build_busy(db: Session, user_ids: list[int]):
    events = db.scalars(select(CalendarEvent).where(CalendarEvent.user_id.in_(user_ids), CalendarEvent.status == "HARD")).all()
    blocks = db.scalars(select(AvailabilityBlock).where(
        AvailabilityBlock.user_id.in_(user_ids),
        AvailabilityBlock.status == "HARD",
    )).all()
    busy = {user_id: [] for user_id in user_ids}
    for event in events:
        busy[event.user_id].append((event.start_at, event.end_at))
    for block in blocks:
        try:
            day = date.fromisoformat(block.date)
            start = datetime.combine(day, time.fromisoformat(block.start_time))
            end_day = day + timedelta(days=1) if block.end_time == "24:00" else day
            end = datetime.combine(end_day, time.min) if block.end_time == "24:00" else datetime.combine(day, time.fromisoformat(block.end_time))
            busy[block.user_id].append((start, end))
        except ValueError:
            continue
    return busy


def _available(user_id: int, start: datetime, end: datetime, busy) -> bool:
    return not any(existing_start < end and existing_end > start for existing_start, existing_end in busy.get(user_id, []))


def _find_proposal(activity: GroupActivity, user_ids: list[int], busy):
    now = datetime.utcnow()
    if activity.time_rule == "FIXED":
        if not activity.fixed_start_at or not activity.fixed_end_at or activity.fixed_end_at <= activity.fixed_start_at or activity.fixed_start_at <= now:
            return None
        available = [uid for uid in user_ids if _available(uid, activity.fixed_start_at, activity.fixed_end_at, busy)]
        return (activity.fixed_start_at, activity.fixed_end_at, available) if len(available) >= activity.min_participants else None

    duration = timedelta(minutes=activity.duration_minutes)
    first = activity.window_start_at or (now + timedelta(minutes=(15 - (now.minute % 15)) % 15))
    if first < now:
        first = now + timedelta(minutes=(15 - (now.minute % 15)) % 15)
    first = first.replace(second=0, microsecond=0)
    window_end = activity.window_end_at
    last_day = (window_end.date() if window_end else max(now.date() + timedelta(days=31), (activity.deadline_at + timedelta(days=31)).date()))
    candidates = []
    cursor = first
    while cursor.date() <= last_day and (window_end is None or cursor + duration <= window_end):
        end = cursor + duration
        available = [uid for uid in user_ids if _available(uid, cursor, end, busy)]
        if len(available) >= activity.min_participants:
            candidates.append((cursor, end, available))
            if activity.time_rule == "EARLIEST":
                return candidates[0]
        cursor += timedelta(minutes=15)
        if len(candidates) > 2000:
            break
    if not candidates:
        return None
    return max(candidates, key=lambda item: (len(item[2]), -item[0].timestamp()))


def _select_participants(activity: GroupActivity, available_ids: list[int], rows: list[GroupActivityParticipant]) -> list[int]:
    if activity.participant_mode != "EXACT":
        return available_ids
    available = set(available_ids)
    ordered = sorted(
        (row for row in rows if row.user_id in available),
        key=lambda row: (row.user_id != activity.creator_id, row.joined_at, row.user_id),
    )
    return [row.user_id for row in ordered[:activity.min_participants]]


def _try_match(activity: GroupActivity, db: Session):
    if activity.status != "OPEN":
        return False
    activity.status = "MATCHING"
    db.commit()
    participants = db.scalars(select(GroupActivityParticipant).where(
        GroupActivityParticipant.activity_id == activity.id,
        GroupActivityParticipant.status.in_(["JOINED", "PENDING_CONFIRM", "CONFIRMED"]),
    ).order_by(GroupActivityParticipant.joined_at, GroupActivityParticipant.id)).all()
    if len(participants) < activity.min_participants:
        activity.status = "OPEN"
        db.commit()
        return False
    user_ids = [row.user_id for row in participants]
    proposal = _find_proposal(activity, user_ids, _build_busy(db, user_ids))
    if not proposal:
        activity.status = "NO_AVAILABLE"
        activity.proposed_start_at = None
        activity.proposed_end_at = None
        db.commit()
        return False
    start, end, available = proposal
    selected = _select_participants(activity, available, participants)
    if len(selected) < activity.min_participants:
        activity.status = "NO_AVAILABLE"
        activity.proposed_start_at = None
        activity.proposed_end_at = None
        db.commit()
        return False
    activity.status = "CONFIRMING"
    activity.proposed_start_at = start
    activity.proposed_end_at = end
    activity.round += 1
    for row in participants:
        row.status = "PENDING_CONFIRM" if row.user_id in selected else "JOINED"
        row.responded_at = None
    db.commit()
    return True


def _maybe_finalize(activity: GroupActivity, db: Session):
    pending = db.scalars(select(GroupActivityParticipant).where(
        GroupActivityParticipant.activity_id == activity.id,
        GroupActivityParticipant.status == "PENDING_CONFIRM")).all()
    confirmed = db.scalars(select(GroupActivityParticipant).where(
        GroupActivityParticipant.activity_id == activity.id,
        GroupActivityParticipant.status == "CONFIRMED")).all()
    # 最低人数模式达到目标即可成立，剩余待确认成员不再占用本次活动名额。
    if activity.participant_mode == "MINIMUM" and len(confirmed) >= activity.min_participants:
        for row in pending:
            row.status = "NOT_SELECTED"
            row.responded_at = datetime.utcnow()
    else:
        declined = db.scalar(select(GroupActivityParticipant).where(
            GroupActivityParticipant.activity_id == activity.id,
            GroupActivityParticipant.status == "DECLINED"))
        if declined:
            activity.status = "RECALC_REQUIRED"
            db.commit()
            return
        if pending:
            return
    if len(confirmed) < activity.min_participants:
        activity.status = "RECALC_REQUIRED"
        db.commit()
        return
    activity.status = "CONFIRMED"
    for row in confirmed:
        exists = db.scalar(select(CalendarEvent).where(
            CalendarEvent.user_id == row.user_id,
            CalendarEvent.title == activity.title,
            CalendarEvent.start_at == activity.proposed_start_at,
            CalendarEvent.end_at == activity.proposed_end_at,
        ))
        if not exists:
            db.add(CalendarEvent(user_id=row.user_id, title=activity.title, description=activity.description,
                                 start_at=activity.proposed_start_at, end_at=activity.proposed_end_at,
                                 category="群组", status="HARD"))
    db.commit()


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


def _public_invitation(invitation: GroupInvitation, db: Session):
    group = db.get(CalendarGroup, invitation.group_id)
    inviter = db.get(User, invitation.inviter_id)
    return GroupInvitationPublic(
        id=invitation.id, group_id=invitation.group_id, group_name=group.name if group else "已删除群组",
        inviter_id=invitation.inviter_id, inviter_display_name=inviter.display_name if inviter else "群主",
        target_id=invitation.target_id, status=invitation.status, created_at=invitation.created_at,
    )


@router.patch("/{group_id}", response_model=GroupPublic)
def update_group(group_id: int, payload: GroupCreate, user: User = Depends(current_user), db: Session = Depends(get_db)):
    group = db.get(CalendarGroup, group_id)
    name = payload.name.strip()
    if not group or not _is_owner(group, user):
        raise HTTPException(404, "群组不存在或你不是群主")
    if not name:
        raise HTTPException(422, "群名不能为空")
    group.name = name
    db.commit()
    db.refresh(group)
    return _public_group(group, db)


@router.post("/{group_id}/members", response_model=GroupInvitationPublic)
def add_member(group_id: int, payload: GroupMemberCreate, user: User = Depends(current_user), db: Session = Depends(get_db)):
    group = db.get(CalendarGroup, group_id)
    target = db.get(User, payload.user_id)
    if not group or not _is_owner(group, user):
        raise HTTPException(404, "群组不存在或你不是群主")
    if not target or target.id == user.id:
        raise HTTPException(404, "用户不存在或不能邀请自己")
    accepted = db.scalar(select(Friendship).where(
        or_((Friendship.user_id == user.id) & (Friendship.friend_id == target.id),
            (Friendship.user_id == target.id) & (Friendship.friend_id == user.id)),
        Friendship.status == "ACCEPTED"))
    if not accepted:
        raise HTTPException(403, "只能邀请已同意的好友加入群组")
    if _is_member(group.id, target.id, db):
        raise HTTPException(409, "对方已经是群组成员")
    existing = db.scalar(select(GroupInvitation).where(GroupInvitation.group_id == group.id, GroupInvitation.target_id == target.id))
    if existing and existing.status == "PENDING":
        raise HTTPException(409, "已经发送过入群邀请，请等待对方确认")
    if existing:
        existing.inviter_id = user.id
        existing.status = "PENDING"
        existing.responded_at = None
        invitation = existing
    else:
        invitation = GroupInvitation(group_id=group.id, inviter_id=user.id, target_id=target.id)
        db.add(invitation)
    db.commit()
    db.refresh(invitation)
    return _public_invitation(invitation, db)


@router.get("/invitations", response_model=list[GroupInvitationPublic])
def list_invitations(user: User = Depends(current_user), db: Session = Depends(get_db)):
    invitations = db.scalars(select(GroupInvitation).where(
        GroupInvitation.target_id == user.id, GroupInvitation.status == "PENDING"
    ).order_by(GroupInvitation.created_at.desc())).all()
    return [_public_invitation(item, db) for item in invitations]


@router.patch("/invitations/{invitation_id}", response_model=GroupInvitationPublic)
def respond_invitation(invitation_id: int, payload: GroupInvitationResponse, user: User = Depends(current_user), db: Session = Depends(get_db)):
    invitation = db.get(GroupInvitation, invitation_id)
    if not invitation or invitation.target_id != user.id or invitation.status != "PENDING":
        raise HTTPException(404, "入群邀请不存在或已处理")
    invitation.status = payload.status
    invitation.responded_at = datetime.utcnow()
    if payload.status == "ACCEPTED":
        if not _is_member(invitation.group_id, user.id, db):
            db.add(GroupMember(group_id=invitation.group_id, user_id=user.id, role="MEMBER"))
    db.commit()
    db.refresh(invitation)
    return _public_invitation(invitation, db)


@router.delete("/{group_id}/members/{member_id}", status_code=204)
def remove_member(group_id: int, member_id: int, user: User = Depends(current_user), db: Session = Depends(get_db)):
    group = db.get(CalendarGroup, group_id)
    if not group or not _is_owner(group, user):
        raise HTTPException(404, "群组不存在或你不是群主")
    if member_id == group.owner_id:
        raise HTTPException(400, "群主不能移出自己")
    member = db.scalar(select(GroupMember).where(GroupMember.group_id == group_id, GroupMember.user_id == member_id))
    if not member:
        raise HTTPException(404, "成员不存在")
    active_participation = db.scalar(select(GroupActivityParticipant).join(
        GroupActivity, GroupActivity.id == GroupActivityParticipant.activity_id
    ).where(
        GroupActivity.group_id == group_id,
        GroupActivityParticipant.user_id == member_id,
        GroupActivityParticipant.status.in_(["JOINED", "PENDING_CONFIRM", "CONFIRMED"]),
        GroupActivity.status.in_(["OPEN", "MATCHING", "CONFIRMING", "RECALC_REQUIRED", "NO_AVAILABLE"]),
    ))
    if active_participation:
        raise HTTPException(409, "该成员正在参与群组活动，请先结束活动后再移出")
    db.delete(member)
    db.commit()


@router.get("/{group_id}/activities", response_model=list[GroupActivityPublic])
def list_activities(group_id: int, user: User = Depends(current_user), db: Session = Depends(get_db)):
    if not _is_member(group_id, user.id, db):
        raise HTTPException(403, "你不是该群组成员")
    activities = db.scalars(select(GroupActivity).where(GroupActivity.group_id == group_id).order_by(GroupActivity.created_at.desc())).all()
    now = datetime.utcnow()
    for activity in activities:
        # MATCHING 在提交后进程异常退出时可能遗留；下一次读取时自动恢复，避免永久卡死。
        if activity.status == "MATCHING" and activity.updated_at and activity.updated_at <= now - timedelta(seconds=30):
            activity.status = "OPEN"
            _try_match(activity, db)
        if activity.deadline_at <= now and activity.status == "CONFIRMING":
            pending = db.scalars(select(GroupActivityParticipant).where(
                GroupActivityParticipant.activity_id == activity.id,
                GroupActivityParticipant.status == "PENDING_CONFIRM")).all()
            for row in pending:
                row.status = "TIMED_OUT"
            activity.status = "RECALC_REQUIRED"
        elif activity.deadline_at <= now and activity.status == "OPEN":
            joined_count = len(db.scalars(select(GroupActivityParticipant).where(
                GroupActivityParticipant.activity_id == activity.id,
                GroupActivityParticipant.status == "JOINED")).all())
            if joined_count < activity.min_participants:
                activity.status = "CANCELLED"
    db.commit()
    return [_public_activity(activity, db) for activity in activities]


@router.post("/{group_id}/activities", response_model=GroupActivityPublic, status_code=201)
def create_activity(group_id: int, payload: GroupActivityCreate, user: User = Depends(current_user), db: Session = Depends(get_db)):
    group = db.get(CalendarGroup, group_id)
    if not group or not _is_member(group_id, user.id, db):
        raise HTTPException(403, "群组不存在或你不是群组成员")
    if payload.deadline_at <= datetime.utcnow():
        raise HTTPException(400, "响应截止时间必须晚于当前时间")
    if payload.time_rule == "FIXED" and (not payload.fixed_start_at or not payload.fixed_end_at or payload.fixed_end_at <= payload.fixed_start_at):
        raise HTTPException(422, "固定时间活动必须提供有效的开始和结束时间")
    if payload.time_rule == "FIXED" and payload.fixed_start_at <= datetime.utcnow():
        raise HTTPException(400, "固定活动时间不能早于当前时间")
    if payload.time_rule == "FIXED" and (payload.window_start_at is not None or payload.window_end_at is not None):
        raise HTTPException(422, "固定时间不需要可约范围")
    if payload.time_rule != "FIXED":
        if payload.window_start_at is None or payload.window_end_at is None:
            raise HTTPException(422, "自动匹配必须提供可约范围，避免活动被安排到非预期时间")
        if payload.window_start_at and payload.window_end_at <= payload.window_start_at:
            raise HTTPException(422, "可约范围无效")
        if payload.window_end_at and payload.window_end_at <= datetime.utcnow():
            raise HTTPException(400, "可约范围必须晚于当前时间")
    activity = GroupActivity(group_id=group_id, creator_id=user.id, activity_code=_new_activity_code(db), **payload.model_dump())
    db.add(activity)
    db.flush()
    db.add(GroupActivityParticipant(activity_id=activity.id, user_id=user.id, status="JOINED"))
    db.commit()
    db.refresh(activity)
    return _public_activity(activity, db)


@router.post("/activities/{activity_id}/join", response_model=GroupActivityPublic)
def join_activity(activity_id: int, user: User = Depends(current_user), db: Session = Depends(get_db)):
    activity = db.get(GroupActivity, activity_id)
    if not activity or not _is_member(activity.group_id, user.id, db):
        raise HTTPException(404, "活动不存在或你不是群组成员")
    if activity.status != "OPEN" or activity.deadline_at <= datetime.utcnow():
        raise HTTPException(409, "活动已进入匹配/确认或结束状态，暂不可加入")
    row = db.scalar(select(GroupActivityParticipant).where(GroupActivityParticipant.activity_id == activity_id, GroupActivityParticipant.user_id == user.id))
    if row:
        row.status = "JOINED"
    else:
        db.add(GroupActivityParticipant(activity_id=activity_id, user_id=user.id, status="JOINED"))
    db.commit()
    _try_match(activity, db)
    db.refresh(activity)
    return _public_activity(activity, db)


@router.delete("/activities/{activity_id}/join", response_model=GroupActivityPublic)
def leave_activity(activity_id: int, user: User = Depends(current_user), db: Session = Depends(get_db)):
    activity = db.get(GroupActivity, activity_id)
    if not activity or not _is_member(activity.group_id, user.id, db):
        raise HTTPException(404, "活动不存在或你不是群组成员")
    row = db.scalar(select(GroupActivityParticipant).where(GroupActivityParticipant.activity_id == activity_id, GroupActivityParticipant.user_id == user.id))
    if not row:
        raise HTTPException(404, "你尚未参与接龙")
    if activity.status != "OPEN":
        raise HTTPException(409, "活动已进入匹配或确认阶段，暂不能退出")
    db.delete(row)
    db.commit()
    return _public_activity(activity, db)


@router.patch("/activities/{activity_id}/response", response_model=GroupActivityPublic)
def respond_activity(activity_id: int, payload: GroupActivityResponse, user: User = Depends(current_user), db: Session = Depends(get_db)):
    activity = db.get(GroupActivity, activity_id)
    if not activity or not _is_member(activity.group_id, user.id, db):
        raise HTTPException(404, "活动不存在或你不是群组成员")
    row = db.scalar(select(GroupActivityParticipant).where(GroupActivityParticipant.activity_id == activity_id, GroupActivityParticipant.user_id == user.id))
    if not row or row.status != "PENDING_CONFIRM":
        raise HTTPException(409, "当前没有等待你确认的成团方案")
    row.status = payload.status
    row.responded_at = datetime.utcnow()
    db.commit()
    if payload.status == "DECLINED":
        activity.status = "RECALC_REQUIRED"
        db.commit()
    else:
        _maybe_finalize(activity, db)
    db.refresh(activity)
    return _public_activity(activity, db)


@router.post("/activities/{activity_id}/recalculate", response_model=GroupActivityPublic)
def recalculate_activity(activity_id: int, user: User = Depends(current_user), db: Session = Depends(get_db)):
    activity = db.get(GroupActivity, activity_id)
    group = db.get(CalendarGroup, activity.group_id) if activity else None
    if not activity or not group or (activity.creator_id != user.id and group.owner_id != user.id):
        raise HTTPException(404, "活动不存在或你没有管理权限")
    if activity.status not in {"RECALC_REQUIRED", "NO_AVAILABLE", "OPEN"}:
        raise HTTPException(409, "当前活动不需要重新匹配")
    db.query(GroupActivityParticipant).filter(
        GroupActivityParticipant.activity_id == activity.id,
        GroupActivityParticipant.status == "DECLINED",
    ).update({"status": "EXCLUDED"}, synchronize_session=False)
    db.query(GroupActivityParticipant).filter(
        GroupActivityParticipant.activity_id == activity.id,
        GroupActivityParticipant.status == "TIMED_OUT",
    ).update({"status": "JOINED"}, synchronize_session=False)
    db.query(GroupActivityParticipant).filter(
        GroupActivityParticipant.activity_id == activity.id,
        GroupActivityParticipant.status.in_(["CONFIRMED", "PENDING_CONFIRM"]),
    ).update({"status": "JOINED", "responded_at": None}, synchronize_session=False)
    activity.status = "OPEN"
    db.commit()
    _try_match(activity, db)
    db.refresh(activity)
    return _public_activity(activity, db)


@router.post("/activities/{activity_id}/cancel", response_model=GroupActivityPublic)
def cancel_activity(activity_id: int, user: User = Depends(current_user), db: Session = Depends(get_db)):
    activity = db.get(GroupActivity, activity_id)
    group = db.get(CalendarGroup, activity.group_id) if activity else None
    if not activity or not group or (activity.creator_id != user.id and group.owner_id != user.id):
        raise HTTPException(404, "活动不存在或你没有管理权限")
    if activity.status == "CANCELLED":
        raise HTTPException(409, "活动已经取消")
    if activity.status == "CONFIRMED":
        events = db.scalars(select(CalendarEvent).where(
            CalendarEvent.category == "群组",
            CalendarEvent.title == activity.title,
            CalendarEvent.start_at == activity.proposed_start_at,
            CalendarEvent.end_at == activity.proposed_end_at,
        )).all()
        for event in events:
            db.delete(event)
    member_ids = db.scalars(select(GroupMember.user_id).where(GroupMember.group_id == activity.group_id)).all()
    for member_id in member_ids:
        db.add(Notification(
            user_id=member_id, type="GROUP_ACTIVITY_CANCELLED", title="群组活动已取消",
            body=f"群组活动“{activity.title}”已由管理者取消。",
            reference_type="group_activity", reference_id=str(activity.id),
        ))
    db.query(GroupActivityParticipant).filter(
        GroupActivityParticipant.activity_id == activity.id,
        GroupActivityParticipant.status == "PENDING_CONFIRM",
    ).update({"status": "CANCELLED"}, synchronize_session=False)
    activity.status = "CANCELLED"
    db.commit()
    db.refresh(activity)
    return _public_activity(activity, db)
