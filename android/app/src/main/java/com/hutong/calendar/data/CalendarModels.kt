package com.hutong.calendar.data

data class CalendarEvent(
    val id: String,
    val ownerId: String,
    val title: String,
    val start: String,
    val end: String,
    val category: String,
    val status: EventStatus,
    val flexibleTailMinutes: Int = 0
)

enum class EventStatus { HARD, FREE, FLEXIBLE, PENDING }

data class UserProfile(
    val id: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val email: String? = null,
    val accountId: String = id,
    val phone: String? = null,
    val hobbies: String? = null,
    val signature: String? = null
)

data class Invite(
    val id: String,
    val title: String,
    val inviterId: String,
    val inviteeId: String,
    val proposedStart: String,
    val proposedEnd: String,
    val status: InviteStatus = InviteStatus.PENDING
)

enum class InviteStatus { PENDING, ACCEPTED, DECLINED, EXPIRED, WITHDRAWN }
