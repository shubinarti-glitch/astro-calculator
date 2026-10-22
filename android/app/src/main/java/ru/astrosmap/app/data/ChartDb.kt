package ru.astrosmap.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow
import ru.astrosmap.app.astro.BirthInput

/** Сохранённая карта. serverId — привязка к /api/profiles (заполняется синхронизацией). */
@Entity(tableName = "charts", indices = [androidx.room.Index(value = ["ownerKey", "clientId"], unique = true)])
data class ChartEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: Long? = null,
    @androidx.room.ColumnInfo(defaultValue = "'guest'") val ownerKey: String = TokenStore.GUEST,
    @androidx.room.ColumnInfo(defaultValue = "''") val clientId: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int,
    val lat: Double,
    val lng: Double,
    val tz: String,
    val city: String,
    val updatedAt: Long = System.currentTimeMillis(),
    /** Кэш ответа /api/natal?svg=0 (трактовки) и язык, на котором он получен. */
    val textsJson: String? = null,
    val textsLang: String? = null,
    /** Локальные правки ещё не доехали до сервера. */
    val dirty: Boolean = false,
    /** Удалена локально, ждёт удаления на сервере (скрыта из списка). */
    val pendingDelete: Boolean = false,
) {
    fun toBirthInput() = BirthInput(year, month, day, hour, minute, lat, lng, tz)
}

@Dao
interface RoomChartDao {
    @Query(
        "SELECT * FROM charts WHERE ownerKey = :owner AND pendingDelete = 0 AND (name LIKE '%'||:q||'%' OR city LIKE '%'||:q||'%') " +
            "ORDER BY updatedAt DESC",
    )
    fun search(q: String, owner: String = TokenStore.GUEST): Flow<List<ChartEntity>>

    @Query("SELECT * FROM charts WHERE ownerKey = :owner")
    suspend fun allOnce(owner: String = TokenStore.GUEST): List<ChartEntity>

    @Query("SELECT * FROM charts WHERE id = :id AND ownerKey = :owner")
    suspend fun byId(id: Long, owner: String = TokenStore.GUEST): ChartEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ChartEntity): Long

    @Query("DELETE FROM charts WHERE id = :id AND ownerKey = :owner")
    suspend fun delete(id: Long, owner: String = TokenStore.GUEST)

    @Query("UPDATE charts SET textsJson = :json, textsLang = :lang WHERE id = :id AND ownerKey = :owner")
    suspend fun updateTexts(id: Long, json: String, lang: String, owner: String = TokenStore.GUEST)

    @Query("UPDATE charts SET pendingDelete = 1 WHERE id = :id AND ownerKey = :owner")
    suspend fun markPendingDelete(id: Long, owner: String = TokenStore.GUEST)

    @androidx.room.Transaction
    suspend fun mergeServer(incoming: ChartEntity, expectedUpdatedAt: Long): Boolean {
        val current = byId(incoming.id, incoming.ownerKey) ?: return false
        if (current.dirty || current.pendingDelete || current.updatedAt != expectedUpdatedAt) return false
        val unchanged = current.name == incoming.name && current.toBirthInput() == incoming.toBirthInput() && current.city == incoming.city
        upsert(incoming.copy(clientId = current.clientId, updatedAt = current.updatedAt,
            textsJson = current.textsJson.takeIf { unchanged }, textsLang = current.textsLang.takeIf { unchanged }))
        return true
    }

    @Query("UPDATE charts SET serverId = :serverId, dirty = CASE WHEN updatedAt = :expectedUpdatedAt THEN 0 ELSE 1 END WHERE id = :id AND ownerKey = :owner")
    suspend fun markSynced(id: Long, serverId: Long, expectedUpdatedAt: Long, owner: String = TokenStore.GUEST): Int
}

@Database(entities = [ChartEntity::class], version = 4, exportSchema = false)
abstract class ChartDb : RoomDatabase() {
    abstract fun chartDao(): RoomChartDao

    companion object {
        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Ownership cannot be inferred safely from old server ids. Preserve all as
                // device guest charts; no automatic upload or reassignment on first login.
                db.execSQL("ALTER TABLE charts ADD COLUMN ownerKey TEXT NOT NULL DEFAULT 'guest'")
                db.execSQL("ALTER TABLE charts ADD COLUMN clientId TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE charts SET clientId = lower(hex(randomblob(16))) WHERE clientId = ''")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_charts_ownerKey_clientId ON charts(ownerKey, clientId)")
            }
        }

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE charts ADD COLUMN textsJson TEXT")
                db.execSQL("ALTER TABLE charts ADD COLUMN textsLang TEXT")
            }
        }
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE charts ADD COLUMN dirty INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE charts ADD COLUMN pendingDelete INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
