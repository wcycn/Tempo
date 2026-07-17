package com.hutong.calendar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.hutong.calendar.data.MockContentDataSource
import com.hutong.calendar.data.PendingInvite

class ContentViewModel : ViewModel() {
    private val dataSource = MockContentDataSource()
    val friends = dataSource.getFriends()
    var pendingInvites by mutableStateOf(dataSource.getPendingInvites())
        private set
    val groups = dataSource.getGroups()

    fun addInvite(invite: PendingInvite) { dataSource.saveInvite(invite); pendingInvites = dataSource.getPendingInvites() }
    fun respondToInvite(id: String) { dataSource.removeInvite(id); pendingInvites = dataSource.getPendingInvites() }
}
