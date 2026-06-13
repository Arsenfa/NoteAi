package com.example.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class NoteSyncManager(
    private val repository: NoteRepository
) {
    companion object {
        private const val TAG = "NoteSyncManager"
    }

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val currentUserId: String?
        get() = auth.currentUser?.uid

    private fun notesCollection(userId: String) =
        firestore.collection("users").document(userId).collection("notes")

    suspend fun syncNotes(): Result<Unit> {
        val userId = currentUserId
            ?: return Result.failure(Exception("User not authenticated"))

        return try {
            pushLocalNotes(userId)
            pullRemoteNotes(userId)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            Result.failure(e)
        }
    }

    suspend fun deleteNoteFromCloud(noteUuid: String): Result<Unit> {
        val userId = currentUserId
            ?: return Result.failure(Exception("User not authenticated"))

        return try {
            notesCollection(userId).document(noteUuid).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Cloud delete failed", e)
            Result.failure(e)
        }
    }

    private suspend fun pushLocalNotes(userId: String) {
        // Force token refresh before sync to prevent silent failures after 1-hour expiry
        auth.currentUser?.getIdToken(true)?.await()

        val unsynced = repository.getUnsyncedNotes()
        for (note in unsynced) {
            try {
                pushNoteWithRetry(userId, note)
                repository.markAsCloudSynced(note.id)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync note ${note.id} after retries", e)
                // Continue with next note, will retry on next sync
            }
        }
    }

    private suspend fun pushNoteWithRetry(userId: String, note: Note, maxRetries: Int = 3) {
        val uuid = note.noteUuid.ifEmpty {
            java.util.UUID.randomUUID().toString()
        }
        val docRef = notesCollection(userId).document(uuid)
        val data = mutableMapOf<String, Any?>(
            "noteUuid" to uuid,
            "title" to note.title,
            "body" to note.body,
            "tags" to note.tags,
            "createdAt" to note.createdAt,
            "updatedAt" to note.updatedAt,
            "isPinned" to note.isPinned
        )
        note.audioDuration?.let { data["audioDuration"] = it }
        note.voiceTranscript?.let { data["voiceTranscript"] = it }
        note.imageUrl?.let { data["imageUrl"] = it }
        note.checklistJson?.let { data["checklistJson"] = it }

        var lastException: Exception? = null
        for (attempt in 1..maxRetries) {
            try {
                docRef.set(data.filterValues { it != null }).await()
                return // Success, exit retry loop
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Retry attempt $attempt/$maxRetries for note ${note.id}", e)
                if (attempt < maxRetries) {
                    // Exponential backoff: 1s, 2s, 4s
                    kotlinx.coroutines.delay(1000L * (1 shl (attempt - 1)))
                }
            }
        }
        throw lastException ?: Exception("Unknown error during retry")
    }

    private suspend fun pullRemoteNotes(userId: String) {
        // Force token refresh before sync to prevent silent failures after 1-hour expiry
        auth.currentUser?.getIdToken(true)?.await()

        val snapshot = notesCollection(userId).get().await()
        val notesToSync = mutableListOf<Note>()

        for (doc in snapshot.documents) {
            val remoteUuid = doc.getString("noteUuid") ?: doc.id
            val remoteUpdatedAt = doc.getLong("updatedAt") ?: 0L

            val localNote = repository.getNoteByUuid(remoteUuid)

            if (localNote == null) {
                val newNote = Note(
                    noteUuid = remoteUuid,
                    title = doc.getString("title") ?: "",
                    body = doc.getString("body") ?: "",
                    tags = doc.getString("tags") ?: "",
                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                    updatedAt = remoteUpdatedAt,
                    isPinned = doc.getBoolean("isPinned") ?: false,
                    audioDuration = doc.getString("audioDuration"),
                    voiceTranscript = doc.getString("voiceTranscript"),
                    imageUrl = doc.getString("imageUrl"),
                    checklistJson = doc.getString("checklistJson"),
                    cloudSynced = true
                )
                notesToSync.add(newNote)
            } else if (remoteUpdatedAt > localNote.updatedAt) {
                val updated = localNote.copy(
                    title = doc.getString("title") ?: localNote.title,
                    body = doc.getString("body") ?: localNote.body,
                    tags = doc.getString("tags") ?: localNote.tags,
                    updatedAt = remoteUpdatedAt,
                    isPinned = doc.getBoolean("isPinned") ?: localNote.isPinned,
                    audioDuration = doc.getString("audioDuration") ?: localNote.audioDuration,
                    voiceTranscript = doc.getString("voiceTranscript") ?: localNote.voiceTranscript,
                    imageUrl = doc.getString("imageUrl") ?: localNote.imageUrl,
                    checklistJson = doc.getString("checklistJson") ?: localNote.checklistJson,
                    cloudSynced = true
                )
                notesToSync.add(updated)
            }
        }

        // Use transactional insert for atomic sync
        if (notesToSync.isNotEmpty()) {
            repository.syncRemoteNotes(notesToSync)
        }
    }
}
