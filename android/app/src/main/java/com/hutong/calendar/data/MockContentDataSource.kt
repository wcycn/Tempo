package com.hutong.calendar.data

data class FriendSummary(val id: String, val name: String, val availability: String)
data class PendingInvite(val id: String, val title: String, val time: String, val inviter: String, val receiver: String = "好友", val description: String? = null)
data class AcceptedInvite(val id: String, val title: String, val time: String, val counterpart: String, val startAt: String, val endAt: String, val description: String? = null)
data class GroupSummary(val name: String, val activity: String, val detail: String)

/** 仅用于无后端时的界面预览，不包含持久化或数据库逻辑。 */
class MockContentDataSource {
    private val invites = mutableListOf<PendingInvite>()

    fun getFriends() = emptyList<FriendSummary>()

    fun getPendingInvites(): List<PendingInvite> {
        return invites.toList()
    }

    fun saveInvite(invite: PendingInvite) { invites.removeAll { it.id == invite.id }; invites.add(invite) }
    fun removeInvite(id: String) { invites.removeAll { it.id == id } }

    fun getGroups() = emptyList<GroupSummary>()
}
