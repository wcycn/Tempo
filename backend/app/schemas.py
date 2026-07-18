from __future__ import annotations

from datetime import datetime
from typing import Optional

from pydantic import BaseModel, ConfigDict, EmailStr, Field


class UserPublic(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    account_id: int
    username: str
    email: str
    display_name: str
    phone: Optional[str] = None
    hobbies: Optional[str] = None
    signature: Optional[str] = None


class RegisterRequest(BaseModel):
    username: str = Field(min_length=3, max_length=40, pattern=r"^\S+$")
    email: EmailStr
    password: str = Field(min_length=8, max_length=128)
    display_name: str = Field(min_length=1, max_length=80)


class PasswordChangeRequest(BaseModel):
    current_password: str = Field(min_length=1, max_length=128)
    new_password: str = Field(min_length=8, max_length=128)


class AccountDeleteRequest(BaseModel):
    password: str = Field(min_length=1, max_length=128)


class ProfileUpdateRequest(BaseModel):
    display_name: Optional[str] = Field(default=None, min_length=1, max_length=80)
    phone: Optional[str] = Field(default=None, max_length=30)
    hobbies: Optional[str] = Field(default=None, max_length=240)
    signature: Optional[str] = Field(default=None, max_length=240)


class FriendUserPublic(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    account_id: int
    username: str
    display_name: str
    phone: Optional[str] = None
    hobbies: Optional[str] = None
    signature: Optional[str] = None


class FriendRequestCreate(BaseModel):
    friend_id: int


class FriendshipPublic(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    user_id: int
    friend_id: int
    status: str
    friend: FriendUserPublic


class FriendResponse(BaseModel):
    status: str = Field(pattern=r"^(ACCEPTED|DECLINED)$")


class NotificationPublic(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    type: str
    title: str
    body: str
    reference_type: Optional[str] = None
    reference_id: Optional[str] = None
    created_at: datetime


class GroupCreate(BaseModel):
    name: str = Field(min_length=1, max_length=100)


class GroupMemberCreate(BaseModel):
    user_id: int


class GroupInvitationPublic(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    group_id: int
    group_name: str
    inviter_id: int
    inviter_display_name: str
    target_id: int
    status: str
    created_at: datetime


class GroupInvitationResponse(BaseModel):
    status: str = Field(pattern=r"^(ACCEPTED|DECLINED)$")


class GroupPublic(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    owner_id: int
    name: str
    members: list[FriendUserPublic] = []


class GroupActivityCreate(BaseModel):
    title: str = Field(min_length=1, max_length=160)
    description: Optional[str] = None
    duration_minutes: int = Field(default=60, ge=15, le=1440)
    min_participants: int = Field(default=2, ge=2, le=100)
    participant_mode: str = Field(default="MINIMUM", pattern=r"^(MINIMUM|EXACT)$")
    deadline_at: datetime
    time_rule: str = Field(pattern=r"^(FIXED|EARLIEST|PEAK)$")
    fixed_start_at: Optional[datetime] = None
    fixed_end_at: Optional[datetime] = None
    window_start_at: Optional[datetime] = None
    window_end_at: Optional[datetime] = None


class GroupActivityPublic(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    activity_code: str
    group_id: int
    creator_id: int
    creator_display_name: str = ""
    title: str
    description: Optional[str] = None
    duration_minutes: int
    min_participants: int
    participant_mode: str
    deadline_at: datetime
    time_rule: str
    fixed_start_at: Optional[datetime] = None
    fixed_end_at: Optional[datetime] = None
    window_start_at: Optional[datetime] = None
    window_end_at: Optional[datetime] = None
    status: str
    proposed_start_at: Optional[datetime] = None
    proposed_end_at: Optional[datetime] = None
    round: int
    participants: list[FriendUserPublic] = []
    pending_confirmation_ids: list[int] = []
    confirmed_count: int = 0
    pending_count: int = 0
    declined_count: int = 0
    confirmed_participant_ids: list[int] = []


class GroupActivityResponse(BaseModel):
    status: str = Field(pattern=r"^(CONFIRMED|DECLINED)$")


class AvailabilityBlockInput(BaseModel):
    date: str = Field(pattern=r"^\d{4}-\d{2}-\d{2}$")
    start_time: str = Field(pattern=r"^\d{2}:\d{2}$")
    end_time: str = Field(pattern=r"^\d{2}:\d{2}$")
    status: str = Field(pattern=r"^(HARD|FREE|FLEXIBLE)$")


class AvailabilityUpdate(BaseModel):
    blocks: list[AvailabilityBlockInput] = Field(default_factory=list, max_length=1000)


class AvailabilityPublic(AvailabilityBlockInput):
    model_config = ConfigDict(from_attributes=True)


class LoginRequest(BaseModel):
    account: str = Field(min_length=1)
    password: str = Field(min_length=1, max_length=128)


class AiAccessVerifyRequest(BaseModel):
    code: str = Field(min_length=1, max_length=128)


class AiAccessVerifyResponse(BaseModel):
    enabled: bool
    access_token: str
    expires_in: int


class AuthResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    user: UserPublic


class SessionPublic(BaseModel):
    session_key: str
    created_at: datetime
    expires_at: datetime
    is_current: bool = False


class EventCreate(BaseModel):
    title: str = Field(min_length=1, max_length=160)
    description: Optional[str] = None
    start_at: datetime
    end_at: datetime
    category: str = "工作"
    status: str = "HARD"
    flexible_tail_minutes: int = Field(default=0, ge=0, le=60)


class EventPublic(EventCreate):
    model_config = ConfigDict(from_attributes=True)
    id: int
    user_id: int
    updated_at: datetime


class InviteCreate(BaseModel):
    receiver_id: int
    title: str = Field(min_length=1, max_length=160)
    description: Optional[str] = None
    start_at: datetime
    end_at: datetime


class InviteResponse(BaseModel):
    status: str = Field(pattern=r"^(ACCEPTED|DECLINED|CANCELLED)$")


class MatchRequest(BaseModel):
    receiver_id: int
    duration_minutes: int = Field(ge=15, le=1440)
    from_date: str = Field(pattern=r"^\d{4}-\d{2}-\d{2}$")
    days: int = Field(default=7, ge=1, le=31)
    window_start_date: Optional[str] = Field(default=None, pattern=r"^\d{4}-\d{2}-\d{2}$")
    window_end_date: Optional[str] = Field(default=None, pattern=r"^\d{4}-\d{2}-\d{2}$")
    window_start_time: Optional[str] = Field(default=None, pattern=r"^\d{2}:\d{2}$")
    window_end_time: Optional[str] = Field(default=None, pattern=r"^\d{2}:\d{2}$")


class MatchOption(BaseModel):
    start_at: datetime
    end_at: datetime
    match_type: str
    score: int


class InvitePublic(InviteCreate):
    model_config = ConfigDict(from_attributes=True)
    id: int
    sender_id: int
    sender_display_name: Optional[str] = None
    receiver_display_name: Optional[str] = None
    status: str
    updated_at: datetime


class SyncResponse(BaseModel):
    events: list[EventPublic]
    accepted_invites: list[InvitePublic]
    calendar_cache: list[dict]
    server_time: datetime
    server_version: str
