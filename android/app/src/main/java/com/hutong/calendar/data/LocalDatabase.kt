package com.hutong.calendar.data

import android.content.Context
import androidx.room.Database
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "cached_events")
data class CachedEvent(
    @androidx.room.PrimaryKey val id: String,
    val ownerId: String,
    val title: String,
    val start: String,
    val end: String,
    val category: String,
    val status: String,
    val flexibleTailMinutes: Int
)

@Entity(tableName = "cached_invites")
data class CachedInvite(
    @androidx.room.PrimaryKey val id: String,
    val title: String,
    val inviterId: String,
    val inviteeId: String,
    val proposedStart: String,
    val proposedEnd: String,
    val status: String
)

@Entity(tableName = "cached_calendar_days")
data class CachedCalendarDay(
    @androidx.room.PrimaryKey val date: String,
    val solarLabel: String,
    val lunarLabel: String,
    val festival: String?,
    val solarTerm: String?
)

@Dao
interface OfflineCalendarDao {
    @Query("SELECT * FROM cached_events ORDER BY start")
    suspend fun events(): List<CachedEvent>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceEvents(items: List<CachedEvent>)

    @Query("DELETE FROM cached_events")
    suspend fun clearEvents()

    @Query("SELECT * FROM cached_invites WHERE status = 'ACCEPTED'")
    suspend fun acceptedInvites(): List<CachedInvite>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceInvites(items: List<CachedInvite>)

    @Query("SELECT * FROM cached_calendar_days ORDER BY date")
    suspend fun calendarDays(): List<CachedCalendarDay>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceCalendarDays(items: List<CachedCalendarDay>)
}

@Database(entities = [CachedEvent::class, CachedInvite::class, CachedCalendarDay::class], version = 1, exportSchema = false)
abstract class TempoDatabase : RoomDatabase() {
    abstract fun offlineCalendarDao(): OfflineCalendarDao

    companion object {
        @Volatile private var instance: TempoDatabase? = null

        fun get(context: Context): TempoDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, TempoDatabase::class.java, "tempo-offline.db").build().also { instance = it }
        }
    }
}

