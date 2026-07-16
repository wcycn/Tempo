from datetime import datetime

from pydantic import BaseModel, ConfigDict, EmailStr, Field


class UserPublic(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    username: str
    email: EmailStr
    display_name: str


class RegisterRequest(BaseModel):
    username: str = Field(min_length=3, max_length=40, pattern=r"^[A-Za-z0-9_]+$")
    email: EmailStr
    password: str = Field(min_length=8, max_length=128)
    display_name: str = Field(min_length=1, max_length=80)


class LoginRequest(BaseModel):
    account: str = Field(min_length=1)
    password: str = Field(min_length=1, max_length=128)


class AuthResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    user: UserPublic


class EventCreate(BaseModel):
    title: str = Field(min_length=1, max_length=160)
    description: str | None = None
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
    description: str | None = None
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

