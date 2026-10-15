package com.aika.game

import android.content.Context
import androidx.room.*

/** Single-row save: highest level finished, unlocked spells bitmask, total crystals. */
@Entity(tableName = "progress")
data class Progress(
    @PrimaryKey val id: Int = 1,
    val levelsDone: Int = 0,
    val spellMask: Int = 0,
    val crystals: Int = 0
)

@Dao
interface ProgressDao {
    @Query("SELECT * FROM progress WHERE id = 1")
    suspend fun get(): Progress?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(p: Progress)
}

@Database(entities = [Progress::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): ProgressDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(c: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(c.applicationContext, AppDb::class.java, "aika.db")
                .build().also { inst = it }
        }
    }
}
