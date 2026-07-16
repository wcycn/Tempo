package com.hutong.calendar.data

data class FriendSummary(val id: String, val name: String, val availability: String)
data class PendingInvite(val id: String, val title: String, val time: String, val inviter: String)
data class NoticeItem(val id: String, val title: String, val detail: String)
data class GroupSummary(val name: String, val activity: String, val detail: String)

/** 仅用于无后端时的界面预览，不包含持久化或数据库逻辑。 */
class MockContentDataSource {
    private val invites = mutableListOf<PendingInvite>()

    fun getFriends() = listOf(
        FriendSummary("user-002", "周子昂", "本周有 8 个空闲时段"),
        FriendSummary("user-003", "许知远", "有 3 个机动时段"),
        FriendSummary("user-004", "陈一一", "本周有 12 个空闲时段")
    )

    fun getPendingInvites(): List<PendingInvite> {
        if (invites.isEmpty()) invites.addAll(listOf(PendingInvite("invite-001", "共进晚餐", "07月14日 19:00 — 20:30", "周子昂"), PendingInvite("invite-002", "打羽毛球", "07月16日 18:30 — 20:00", "许知远")))
        return invites.toList()
    }

    fun saveInvite(invite: PendingInvite) { invites.removeAll { it.id == invite.id }; invites.add(invite) }
    fun removeInvite(id: String) { invites.removeAll { it.id == id } }

    fun getNotices() = listOf(
        NoticeItem("notice-001", "周子昂发来一条邀约", "晚餐 · 07月14日 19:00 — 20:30"),
        NoticeItem("notice-002", "周末羽毛球约局达到成团人数", "请确认拟定时间：07月18日 15:00"),
        NoticeItem("notice-003", "你的活动已同步", "外部日历新增 3 条硬性事务")
    )

    fun getGroups() = listOf(GroupSummary("羽毛球俱乐部", "周末羽毛球约局", "最低 4 人 · 响应截止还有 18:42:10"))
}
