package com.example.data

import android.util.Base64

class AIRepository {

    suspend fun ocrImage(imageBytes: ByteArray): Result<String> {
        return try {
            val base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            val response = GeminiService.analyzeImage(
                bitmapBase64 = base64,
                mimeType = "image/jpeg",
                prompt = "Extract all text from this image. Return plain text, preserving structure."
            )
            if (response.startsWith("Error")) {
                Result.failure(Exception(response))
            } else {
                Result.success(response)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun describeImage(imageBytes: ByteArray): Result<String> {
        return try {
            val base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            val response = GeminiService.analyzeImage(
                bitmapBase64 = base64,
                mimeType = "image/jpeg",
                prompt = "Describe what you see in this image in 2-3 sentences."
            )
            if (response.startsWith("Error")) {
                Result.failure(Exception(response))
            } else {
                Result.success(response)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun visualSearch(imageBytes: ByteArray, notes: List<Note>): Result<String> {
        return try {
            val notesText = notes.joinToString("\n---\n") { "ID: ${it.id}\nTitle: ${it.title}\nBody: ${it.body}" }
            val base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            val response = GeminiService.analyzeImage(
                bitmapBase64 = base64,
                mimeType = "image/jpeg",
                prompt = "Which of these notes are related to this image?\n\nNotes List:\n$notesText"
            )
            if (response.startsWith("Error")) {
                Result.failure(Exception(response))
            } else {
                Result.success(response)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
