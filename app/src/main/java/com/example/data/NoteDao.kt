package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY isPinned DESC, updatedAt DESC")
    fun getAllNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    fun getNoteById(id: Long): Flow<Note?>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getNoteByIdSync(id: Long): Note?

    @Query("SELECT * FROM notes WHERE noteUuid = :uuid LIMIT 1")
    suspend fun getNoteByUuid(uuid: String): Note?

    @Query("SELECT * FROM notes WHERE cloudSynced = 0")
    suspend fun getUnsyncedNotes(): List<Note>

    @Query("UPDATE notes SET cloudSynced = 1 WHERE id = :id")
    suspend fun markAsCloudSynced(id: Long)

    @Query("UPDATE notes SET isPinned = NOT isPinned, updatedAt = :now, cloudSynced = 0 WHERE id = :id")
    suspend fun togglePin(id: Long, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM notes WHERE title LIKE '%' || :query || '%' OR body LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%'")
    fun searchNotes(query: String): Flow<List<Note>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: Note): Long

    @Delete
    suspend fun deleteNote(note: Note)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNoteById(id: Long)

    @Query("DELETE FROM notes WHERE (TRIM(title) = '' OR title IS NULL) AND (TRIM(body) = '' OR body IS NULL)")
    suspend fun deleteEmptyNotes()

    @Transaction
    suspend fun syncRemoteNotes(notes: List<Note>) {
        for (note in notes) {
            insertNote(note)
        }
    }
}
