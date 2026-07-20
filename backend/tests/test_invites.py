from datetime import datetime, timedelta

from sqlalchemy import create_engine
from sqlalchemy.orm import Session
from sqlalchemy.pool import StaticPool

from app.database import Base
from app.models import Friendship, Invite, User
from fastapi import HTTPException

from app.routes.friends import replace_availability
from app.routes.invites import create_invite, list_invites, match_times, respond
from app.schemas import AvailabilityBlockInput, AvailabilityUpdate, InviteCreate, InviteResponse, MatchRequest


def _user(username: str) -> User:
    return User(
        username=username,
        email=f"{username}@example.com",
        display_name=username,
        password_hash="not-used-in-this-test",
    )


def _session() -> Session:
    engine = create_engine(
        "sqlite+pysqlite:///:memory:",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    Base.metadata.create_all(engine)
    return Session(engine)


def test_receiver_can_keep_multiple_pending_invites_until_accepting_one():
    with _session() as db:
        receiver = _user("receiver")
        sender_a = _user("sender-a")
        sender_b = _user("sender-b")
        db.add_all([receiver, sender_a, sender_b])
        db.flush()
        db.add_all([
            Friendship(user_id=sender_a.id, friend_id=receiver.id, status="ACCEPTED"),
            Friendship(user_id=sender_b.id, friend_id=receiver.id, status="ACCEPTED"),
        ])
        db.commit()

        start = datetime.now() + timedelta(days=1)
        end = start + timedelta(hours=1)
        first = create_invite(
            InviteCreate(receiver_id=receiver.id, title="晚餐 A", start_at=start, end_at=end),
            sender_a,
            db,
        )
        second = create_invite(
            InviteCreate(receiver_id=receiver.id, title="晚餐 B", start_at=start, end_at=end),
            sender_b,
            db,
        )

        pending = [item for item in list_invites(receiver, db) if item["status"] == "PENDING"]
        assert {item["id"] for item in pending} == {first["id"], second["id"]}
        assert {item["sender_display_name"] for item in pending} == {"sender-a", "sender-b"}

        respond(first["id"], InviteResponse(status="ACCEPTED"), receiver, db)
        statuses = {
            item.id: item.status
            for item in db.query(Invite).filter(Invite.id.in_([first["id"], second["id"]])).all()
        }
        assert statuses[first["id"]] == "ACCEPTED"
        assert statuses[second["id"]] == "DECLINED"


def test_non_overlapping_pending_invite_is_not_cancelled_after_acceptance():
    with _session() as db:
        receiver = _user("receiver")
        sender_a = _user("sender-a")
        sender_b = _user("sender-b")
        db.add_all([receiver, sender_a, sender_b])
        db.flush()
        db.add_all([
            Friendship(user_id=sender_a.id, friend_id=receiver.id, status="ACCEPTED"),
            Friendship(user_id=sender_b.id, friend_id=receiver.id, status="ACCEPTED"),
        ])
        db.commit()

        first_start = datetime.now() + timedelta(days=1)
        second_start = first_start + timedelta(hours=2)
        first = create_invite(
            InviteCreate(receiver_id=receiver.id, title="午餐", start_at=first_start, end_at=first_start + timedelta(hours=1)),
            sender_a,
            db,
        )
        second = create_invite(
            InviteCreate(receiver_id=receiver.id, title="散步", start_at=second_start, end_at=second_start + timedelta(hours=1)),
            sender_b,
            db,
        )

        respond(first["id"], InviteResponse(status="ACCEPTED"), receiver, db)
        assert db.get(Invite, first["id"]).status == "ACCEPTED"
        assert db.get(Invite, second["id"]).status == "PENDING"


def test_sender_cannot_create_overlapping_pending_invites():
    with _session() as db:
        sender = _user("sender")
        receiver_a = _user("receiver-a")
        receiver_b = _user("receiver-b")
        db.add_all([sender, receiver_a, receiver_b])
        db.flush()
        db.add_all([
            Friendship(user_id=sender.id, friend_id=receiver_a.id, status="ACCEPTED"),
            Friendship(user_id=sender.id, friend_id=receiver_b.id, status="ACCEPTED"),
        ])
        db.commit()

        start = datetime.now() + timedelta(days=1)
        create_invite(
            InviteCreate(receiver_id=receiver_a.id, title="晚餐 A", start_at=start, end_at=start + timedelta(hours=1)),
            sender,
            db,
        )
        try:
            create_invite(
                InviteCreate(receiver_id=receiver_b.id, title="晚餐 B", start_at=start, end_at=start + timedelta(hours=1)),
                sender,
                db,
            )
            assert False, "overlapping outgoing pending invite must be rejected"
        except HTTPException as error:
            assert error.status_code == 409


def test_matching_excludes_sender_pending_lock_but_not_receiver_pending_lock():
    with _session() as db:
        sender = _user("sender")
        receiver = _user("receiver")
        third = _user("third")
        db.add_all([sender, receiver, third])
        db.flush()
        db.add_all([
            Friendship(user_id=sender.id, friend_id=receiver.id, status="ACCEPTED"),
            Friendship(user_id=third.id, friend_id=receiver.id, status="ACCEPTED"),
        ])
        db.commit()

        day = datetime.now() + timedelta(days=1)
        locked_start = day.replace(hour=12, minute=0, second=0, microsecond=0)
        locked_end = locked_start + timedelta(hours=1)
        create_invite(
            InviteCreate(receiver_id=receiver.id, title="午餐", start_at=locked_start, end_at=locked_end),
            sender,
            db,
        )
        request = MatchRequest(
            receiver_id=receiver.id,
            duration_minutes=60,
            from_date=locked_start.date().isoformat(),
            days=1,
            window_start_date=locked_start.date().isoformat(),
            window_end_date=locked_start.date().isoformat(),
            window_start_time="12:00",
            window_end_time="13:00",
        )

        assert match_times(request, sender, db) == []
        # 接收方的待应答不锁定时间，第三人仍可向该接收方匹配同一时段。
        assert len(match_times(request.model_copy(update={"receiver_id": receiver.id}), third, db)) == 1


def test_only_receiver_hard_availability_implicitly_declines_pending_invite():
    with _session() as db:
        sender = _user("sender")
        receiver = _user("receiver")
        db.add_all([sender, receiver])
        db.flush()
        db.add(Friendship(user_id=sender.id, friend_id=receiver.id, status="ACCEPTED"))
        db.commit()

        start = (datetime.now() + timedelta(days=1)).replace(hour=18, minute=0, second=0, microsecond=0)
        created = create_invite(
            InviteCreate(receiver_id=receiver.id, title="晚餐", start_at=start, end_at=start + timedelta(hours=1)),
            sender,
            db,
        )
        hard = AvailabilityUpdate(blocks=[AvailabilityBlockInput(
            date=start.date().isoformat(), start_time="18:00", end_time="19:00", status="HARD"
        )])

        replace_availability(hard, sender, db)
        assert db.get(Invite, created["id"]).status == "PENDING"

        replace_availability(hard, receiver, db)
        assert db.get(Invite, created["id"]).status == "DECLINED"
