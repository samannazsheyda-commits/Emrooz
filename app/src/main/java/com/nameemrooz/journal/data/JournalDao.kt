package com.nameemrooz.journal.data

import androidx.room.*
import com.nameemrooz.journal.model.JournalEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface JournalDao {
    @Query("SELECT * FROM journal_entries WHERE archived = 0 ORDER BY createdAt DESC")
    fun active(): Flow<List<JournalEntry>>

    @Query("SELECT * FROM journal_entries WHERE archived = 1 ORDER BY createdAt DESC")
    fun archived(): Flow<List<JournalEntry>>

    @Query("SELECT * FROM journal_entries WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): JournalEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: JournalEntry): Long

    @Update suspend fun update(entry: JournalEntry)
    @Delete suspend fun delete(entry: JournalEntry)
}
