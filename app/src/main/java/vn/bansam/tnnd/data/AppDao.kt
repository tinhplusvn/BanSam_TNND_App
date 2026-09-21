package vn.bansam.tnnd.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Query("SELECT * FROM children ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Person>>
    @Insert suspend fun insert(p: Person): Long
    @Update suspend fun update(p: Person)
    @Delete suspend fun delete(p: Person)
    @Query("DELETE FROM children") suspend fun deleteAll()
    @Insert suspend fun insertAll(items: List<Person>)
    @Query("SELECT COUNT(*) FROM children") suspend fun count(): Int
}
