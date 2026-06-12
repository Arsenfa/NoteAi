package com.example.data

import kotlinx.coroutines.flow.Flow

class NoteRepository(private val noteDao: NoteDao) {
    val allNotes: Flow<List<Note>> = noteDao.getAllNotes()

    fun getNoteById(id: Long): Flow<Note?> = noteDao.getNoteById(id)

    suspend fun getNoteByIdSync(id: Long): Note? = noteDao.getNoteByIdSync(id)

    suspend fun getNoteByUuid(uuid: String): Note? = noteDao.getNoteByUuid(uuid)

    suspend fun getUnsyncedNotes(): List<Note> = noteDao.getUnsyncedNotes()

    suspend fun markAsCloudSynced(id: Long) = noteDao.markAsCloudSynced(id)

    suspend fun togglePin(id: Long) = noteDao.togglePin(id)

    fun searchNotes(query: String): Flow<List<Note>> = noteDao.searchNotes(query)

    suspend fun insertNote(note: Note): Long = noteDao.insertNote(note)

    suspend fun deleteNote(note: Note) = noteDao.deleteNote(note)

    suspend fun deleteNoteById(id: Long) = noteDao.deleteNoteById(id)
}
