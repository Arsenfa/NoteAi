package com.example.data

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiService {
    private const val TAG = "GeminiService"
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private var currentCall: Call? = null

    fun cancelCurrentRequest() {
        currentCall?.cancel()
        currentCall = null
    }

    private fun getApiKey(): String {
        return BuildConfig.GEMINI_API_KEY
    }

    suspend fun generateContent(prompt: String, systemInstruction: String? = null, apiKey: String? = null): String = withContext(Dispatchers.IO) {
        val key = if (!apiKey.isNullOrEmpty()) apiKey else getApiKey()
        if (key.isEmpty() || key == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "API Key is missing or placeholder!")
            return@withContext "API Key belum diatur. Buka Settings untuk menambahkan Gemini API key kamu."
        }

        // Using prompt-friendly default recommended model (gemini-3.5-flash)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"
        val mediaType = "application/json; charset=utf-8".toMediaType()

        try {
            val requestJson = JSONObject()
            val contentsArray = JSONArray()
            val contentObj = JSONObject()
            val partsArray = JSONArray()
            val partObj = JSONObject()
            partObj.put("text", prompt)
            partsArray.put(partObj)
            contentObj.put("parts", partsArray)
            contentsArray.put(contentObj)
            requestJson.put("contents", contentsArray)

            if (systemInstruction != null) {
                val systemInstructionObj = JSONObject()
                val systemPartsArray = JSONArray()
                val systemPartObj = JSONObject()
                systemPartObj.put("text", systemInstruction)
                systemPartsArray.put(systemPartObj)
                systemInstructionObj.put("parts", systemPartsArray)
                requestJson.put("systemInstruction", systemInstructionObj)
            }

            val requestBody = requestJson.toString().toRequestBody(mediaType)
            val request = Request.Builder()
                .url(url)
                .addHeader("x-goog-api-key", key)
                .post(requestBody)
                .build()

            currentCall = client.newCall(request)
            currentCall!!.execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "HTTP error: ${response.code}")
                    val errorMessage = when (response.code) {
                        429 -> "AI rate limit reached. Please wait a moment and try again."
                        401 -> "Invalid Gemini API key. Please check your key in Settings."
                        403 -> "Gemini API quota exceeded. Check your Google AI Studio usage."
                        500, 503 -> "Gemini server error. Try again in a few seconds."
                        else -> "AI request failed (HTTP ${response.code})"
                    }
                    return@withContext errorMessage
                }

                val responseBody = response.body?.string() ?: return@withContext "Empty response"
                val responseJson = JSONObject(responseBody)
                val candidates = responseJson.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)

                    // Check for safety filter blocks
                    val finishReason = firstCandidate.optString("finishReason", "")
                    if (finishReason == "SAFETY") {
                        return@withContext "Response blocked by safety filter. Please try rephrasing your request."
                    }

                    val content = firstCandidate.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        return@withContext parts.getJSONObject(0).optString("text", "No text response")
                    }
                }
                return@withContext "Failed to parse response"
            }
        } catch (e: Exception) {
            if (e.message?.contains("Canceled") == true) {
                Log.i(TAG, "Request was canceled")
            } else {
                Log.e(TAG, "Exception during call", e)
            }
            return@withContext "An error occurred: ${e.localizedMessage}"
        } finally {
            currentCall = null
        }
    }

    suspend fun analyzeImage(bitmapBase64: String, mimeType: String, prompt: String, apiKey: String? = null): String = withContext(Dispatchers.IO) {
        val key = if (!apiKey.isNullOrEmpty()) apiKey else getApiKey()
        if (key.isEmpty() || key == "MY_GEMINI_API_KEY") {
            return@withContext "API Key belum diatur. Buka Settings untuk menambahkan Gemini API key kamu."
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"
        val mediaType = "application/json; charset=utf-8".toMediaType()

        try {
            val requestJson = JSONObject()
            val contentsArray = JSONArray()
            val contentObj = JSONObject()
            val partsArray = JSONArray()

            // Part 1: Text prompt
            val textPart = JSONObject()
            textPart.put("text", prompt)
            partsArray.put(textPart)

            // Part 2: Image
            val imagePart = JSONObject()
            val inlineData = JSONObject()
            inlineData.put("mimeType", mimeType)
            inlineData.put("data", bitmapBase64)
            imagePart.put("inlineData", inlineData)
            partsArray.put(imagePart)

            contentObj.put("parts", partsArray)
            contentsArray.put(contentObj)
            requestJson.put("contents", contentsArray)

            val requestBody = requestJson.toString().toRequestBody(mediaType)
            val request = Request.Builder()
                .url(url)
                .addHeader("x-goog-api-key", key)
                .post(requestBody)
                .build()

            currentCall = client.newCall(request)
            currentCall!!.execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "HTTP error: ${response.code}")
                    val errorMessage = when (response.code) {
                        429 -> "AI rate limit reached. Please wait a moment and try again."
                        401 -> "Invalid Gemini API key. Please check your key in Settings."
                        403 -> "Gemini API quota exceeded. Check your Google AI Studio usage."
                        500, 503 -> "Gemini server error. Try again in a few seconds."
                        else -> "AI request failed (HTTP ${response.code})"
                    }
                    return@withContext errorMessage
                }

                val responseBody = response.body?.string() ?: return@withContext "Empty response"
                val responseJson = JSONObject(responseBody)
                val candidates = responseJson.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)

                    // Check for safety filter blocks
                    val finishReason = firstCandidate.optString("finishReason", "")
                    if (finishReason == "SAFETY") {
                        return@withContext "Image analysis blocked by safety filter. The image may contain sensitive content."
                    }

                    val content = firstCandidate.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        return@withContext parts.getJSONObject(0).optString("text", "")
                    }
                }
                return@withContext "Failed to parse response"
            }
        } catch (e: Exception) {
            if (e.message?.contains("Canceled") == true) {
                Log.i(TAG, "Request was canceled")
            } else {
                Log.e(TAG, "Exception during image analysis", e)
            }
            return@withContext "An error occurred: ${e.localizedMessage}"
        } finally {
            currentCall = null
        }
    }
}
