package cn.wcylab.tempo.data

import android.content.Context
import androidx.room.Database
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "cached_events", primaryKeys = ["ownerId", "id"])
data class CachedEvent(
    val ownerId: String,
    val id: String,
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
    val status: String,
    val ownerId: String
)

@Entity(tableName = "cached_calendar_days", primaryKeys = ["ownerId", "date"])
data class CachedCalendarDay(
    val ownerId: String,
    val date: String,
    val solarLabel: String,
    val lunarLabel: String,
    val festival: String?,
    val solarTerm: String?
)

@Dao
interface OfflineCalendarDao {
    @Query("SELECT * FROM cached_events WHERE ownerId = :ownerId ORDER BY start")
    suspend fun events(ownerId: String): List<CachedEvent>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceEvents(items: List<CachedEvent>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveEvent(item: CachedEvent)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveEvents(items: List<CachedEvent>)

    @Query("DELETE FROM cached_events WHERE id = :id AND ownerId = :ownerId")
    suspend fun deleteEvent(id: String, ownerId: String)

    @Query("DELETE FROM cached_events")
    suspend fun clearEvents()

    @Query("SELECT * FROM cached_invites WHERE ownerId = :ownerId AND status = 'ACCEPTED'")
    suspend fun acceptedInvites(ownerId: String): List<CachedInvite>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceInvites(items: List<CachedInvite>)

    @Query("SELECT * FROM cached_calendar_days WHERE ownerId = :ownerId ORDER BY date")
    suspend fun calendarDays(ownerId: String): List<CachedCalendarDay>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceCalendarDays(items: List<CachedCalendarDay>)
}

@Database(entities = [CachedEvent::class, CachedInvite::class, CachedCalendarDay::class], version = 2, exportSchema = false)
abstract class TempoDatabase : RoomDatabase() {
    abstract fun offlineCalendarDao(): OfflineCalendarDao

    companion object {
        @Volatile private var instance: TempoDatabase? = null

        fun get(context: Context): TempoDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, TempoDatabase::class.java, "tempo-offline.db")
                .addMigrations(MIGRATION_1_2).build().also { instance = it }
        }

        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE cached_events_new (ownerId TEXT NOT NULL, id TEXT NOT NULL, title TEXT NOT NULL, start TEXT NOT NULL, end TEXT NOT NULL, category TEXT NOT NULL, status TEXT NOT NULL, flexibleTailMinutes INTEGER NOT NULL, PRIMARY KEY(ownerId, id))")
                db.execSQL("INSERT INTO cached_events_new SELECT ownerId, id, title, start, end, category, status, flexibleTailMinutes FROM cached_events")
                db.execSQL("DROP TABLE cached_events")
                db.execSQL("ALTER TABLE cached_events_new RENAME TO cached_events")
                db.execSQL("ALTER TABLE cached_invites ADD COLUMN ownerId TEXT NOT NULL DEFAULT 'guest'")
                db.execSQL("CREATE TABLE cached_calendar_days_new (ownerId TEXT NOT NULL, date TEXT NOT NULL, solarLabel TEXT NOT NULL, lunarLabel TEXT NOT NULL, festival TEXT, solarTerm TEXT, PRIMARY KEY(ownerId, date))")
                db.execSQL("INSERT INTO cached_calendar_days_new SELECT 'guest', date, solarLabel, lunarLabel, festival, solarTerm FROM cached_calendar_days")
                db.execSQL("DROP TABLE cached_calendar_days")
                db.execSQL("ALTER TABLE cached_calendar_days_new RENAME TO cached_calendar_days")
            }
        }
    }
}
