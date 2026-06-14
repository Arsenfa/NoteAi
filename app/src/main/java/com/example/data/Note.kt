package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notes",
    indices = [
        Index(value = ["noteUuid"], unique = true),
        Index(value = ["tags"]),
        Index(value = ["isPinned"]),
        Index(value = ["createdAt"])
    ]
)
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteUuid: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val body: String,
    val tags: String = "", // Comma-separated tags, e.g. "Work,Meeting"
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val audioDuration: String? = null,
    val voiceTranscript: String? = null,
    val imageUrl: String? = null,
    val checklistJson: String? = null, // JSON Array of items: [{"text":"Task1","checked":true}]
    val cloudSynced: Boolean = false
)
