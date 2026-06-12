package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val tags: String = "", // Comma-separated tags, e.g. "Work,Meeting"
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val audioDuration: String? = null,
    val voiceTranscript: String? = null,
    val imageUrl: String? = null,
    val checklistJson: String? = null // JSON Array of items: [{"text":"Task1","checked":true}]
)
