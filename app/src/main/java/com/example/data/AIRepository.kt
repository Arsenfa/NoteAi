package com.example.data

import android.util.Base64

class AIRepository {

    private fun isGeminiError(response: String): Boolean {
        return response.startsWith("API Key belum") ||
            response.startsWith("An error occurred:") ||
            response.startsWith("AI request failed") ||
            response.startsWith("Empty response") ||
            response.startsWith("Failed to parse") ||
            response.startsWith("AI rate limit") ||
            response.startsWith("Invalid Gemini") ||
            response.startsWith("Gemini API quota") ||
            response.startsWith("Gemini server error") ||
            response.startsWith("Response blocked") ||
            response.startsWith("Image analysis blocked")
    }

    suspend fun ocrImage(imageBytes: ByteArray, apiKey: String? = null): Result<String> {
        return try {
            val base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            val response = GeminiService.analyzeImage(
                bitmapBase64 = base64,
                mimeType = "image/jpeg",
                prompt = "Extract all text from this image. Return plain text, preserving structure.",
                apiKey = apiKey
            )
            if (isGeminiError(response)) {
                Result.failure(Exception(response))
            } else {
                Result.success(response)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun describeImage(imageBytes: ByteArray, apiKey: String? = null): Result<String> {
        return try {
            val base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            val response = GeminiService.analyzeImage(
                bitmapBase64 = base64,
                mimeType = "image/jpeg",
                prompt = "Describe what you see in this image in 2-3 sentences.",
                apiKey = apiKey
            )
            if (isGeminiError(response)) {
                Result.failure(Exception(response))
            } else {
                Result.success(response)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun visualSearch(imageBytes: ByteArray, notes: List<Note>, apiKey: String? = null): Result<String> {
        return try {
            val notesText = notes.joinToString("\n---\n") { "ID: ${it.id}\nTitle: ${it.title}\nBody: ${it.body}" }
            val base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            val response = GeminiService.analyzeImage(
                bitmapBase64 = base64,
                mimeType = "image/jpeg",
                prompt = "Which of these notes are related to this image?\n\nNotes List:\n$notesText",
                apiKey = apiKey
            )
            if (isGeminiError(response)) {
                Result.failure(Exception(response))
            } else {
                Result.success(response)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
