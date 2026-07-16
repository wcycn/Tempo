from __future__ import annotations

from datetime import datetime
from typing import Optional

from pydantic import BaseModel, ConfigDict, EmailStr, Field


class UserPublic(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    account_id: int
    username: str
    email: EmailStr
    display_name: str
    phone: Optional[str] = None
    hobbies: Optional[str] = None
    signature: Optional[str] = None


class RegisterRequest(BaseModel):
    username: str = Field(min_length=3, max_length=40, pattern=r"^\S+$")
    email: EmailStr
    password: str = Field(min_length=8, max_length=128)
    display_name: str = Field(min_length=1, max_length=80)


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


class FriendRequestCreate(BaseModel):
    friend_id: int


class FriendshipPublic(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    user_id: int
    friend_id: int
    status: str
    friend: FriendUserPublic


class LoginRequest(BaseModel):
    account: str = Field(min_length=1)
    password: str = Field(min_length=1, max_length=128)


class AuthResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    user: UserPublic


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
    status: str = Field(pattern=r"^(ACCEPTED|DECLINED)$")


class InvitePublic(InviteCreate):
    model_config = ConfigDict(from_attributes=True)
    id: int
    sender_id: int
    status: str
    updated_at: datetime


class SyncResponse(BaseModel):
    events: list[EventPublic]
    accepted_invites: list[InvitePublic]
    calendar_cache: list[dict]
    server_time: datetime
