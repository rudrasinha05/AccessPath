package com.accesspath.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// One versioned aggregate makes reports, restrictions and their audit history atomic.
// Graph references are validated by MapValidator before this aggregate can be persisted.
@Entity(tableName = "campus_state")
data class StateRow(@PrimaryKey val id: Int = 1, val revision: Long, val payload: String)
@Dao interface StateDao {
    @Query("SELECT * FROM campus_state WHERE id = 1") fun observe(): Flow<StateRow?>
    @Query("SELECT * FROM campus_state WHERE id = 1") suspend fun get(): StateRow?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(row: StateRow)
    @Query("UPDATE campus_state SET payload = :payload, revision = :next WHERE id = 1 AND revision = :expected")
    suspend fun compareAndSet(payload: String, next: Long, expected: Long): Int
}
@Database(entities = [StateRow::class], version = 1, exportSchema = true)
abstract class AccessDatabase : RoomDatabase() { abstract fun stateDao(): StateDao }
