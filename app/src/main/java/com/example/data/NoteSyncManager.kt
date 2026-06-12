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

    private fun notesCollection() =
        firestore.collection("users").document(currentUserId!!).collection("notes")

    suspend fun syncNotes(): Result<Unit> {
        val userId = currentUserId
            ?: return Result.failure(Exception("User not authenticated"))

        return try {
            pushLocalNotes(userId)
            pullRemoteNotes()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            Result.failure(e)
        }
    }

    private suspend fun pushLocalNotes(userId: String) {
        val unsynced = repository.getUnsyncedNotes()
        for (note in unsynced) {
            val uuid = note.noteUuid.ifEmpty {
                java.util.UUID.randomUUID().toString()
            }
            val docRef = notesCollection().document(uuid)
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

            docRef.set(data.filterValues { it != null }).await()
            repository.markAsCloudSynced(note.id)
        }
    }

    private suspend fun pullRemoteNotes() {
        val snapshot = notesCollection().get().await()
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
                repository.insertNote(newNote)
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
                repository.insertNote(updated)
            }
        }
    }
}
