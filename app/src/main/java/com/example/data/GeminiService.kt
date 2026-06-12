package com.example.data

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    private fun getApiKey(): String {
        return BuildConfig.GEMINI_API_KEY
    }

    suspend fun generateContent(prompt: String, systemInstruction: String? = null): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "API Key is missing or placeholder!")
            return@withContext "API Key is missing or placeholder! Please configure it in your AI Studio user secrets."
        }

        // Using prompt-friendly default recommended model (gemini-3.5-flash)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
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
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorString = response.body?.string() ?: "Unknown error"
                    Log.e(TAG, "Unsuccessful response: ${response.code} - $errorString")
                    return@withContext "Error ${response.code}: $errorString"
                }

                val responseBody = response.body?.string() ?: return@withContext "Empty response"
                val responseJson = JSONObject(responseBody)
                val candidates = responseJson.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val content = firstCandidate.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        return@withContext parts.getJSONObject(0).optString("text", "No text response")
                    }
                }
                return@withContext "Failed to parse response: $responseBody"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during call", e)
            return@withContext "An error occurred: ${e.localizedMessage}"
        }
    }

    suspend fun analyzeImage(bitmapBase64: String, mimeType: String, prompt: String): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "API Key is missing or placeholder! Please configure it in your AI Studio user secrets."
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
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
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorString = response.body?.string() ?: "Unknown error"
                    return@withContext "Error ${response.code}: $errorString"
                }

                val responseBody = response.body?.string() ?: return@withContext "Empty response"
                val responseJson = JSONObject(responseBody)
                val candidates = responseJson.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val content = firstCandidate.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        return@withContext parts.getJSONObject(0).optString("text", "")
                    }
                }
                return@withContext "Failed to parse response: $responseBody"
            }
        } catch (e: Exception) {
            return@withContext "An error occurred: ${e.localizedMessage}"
        }
    }
}
