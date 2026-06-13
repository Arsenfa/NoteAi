package com.example.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GeminiService
import com.example.data.Note
import com.example.data.NoteRepository
import com.example.data.AIRepository
import com.example.data.NoteSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

import kotlinx.coroutines.ExperimentalCoroutinesApi

val Context.dataStore by preferencesDataStore(name = "settings")

@OptIn(ExperimentalCoroutinesApi::class)
class NoteViewModel(private val repository: NoteRepository, private val context: Context) : ViewModel() {

    private val syncManager = NoteSyncManager(repository)
    val isSyncing = MutableStateFlow(false)
    val lastSyncError = MutableStateFlow<String?>(null)

    companion object {
        val OFFLINE_MODE_KEY = booleanPreferencesKey("offline_mode")
        val APP_THEME_KEY = stringPreferencesKey("app_theme")
        val DATE_FORMAT_KEY = stringPreferencesKey("date_format")
        val SELECTED_MODEL_KEY = stringPreferencesKey("selected_model")
        val CURRENT_LANGUAGE_KEY = stringPreferencesKey("current_language")
        val GEMINI_API_KEY_PREF = stringPreferencesKey("gemini_api_key")
    }

    // Onboarding / Navigation state
    var currentScreen = MutableStateFlow("splash")
    var completedOnboarding = MutableStateFlow(false)
    var isLoggedIn = MutableStateFlow(false)

    // UI state
    val allNotes = repository.allNotes.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val searchQuery = MutableStateFlow("")
    val filterChip = MutableStateFlow("All") // "All", "Action items", "Meeting notes", "Ideas", "Today"
    val isGridView = MutableStateFlow(false)

    // Filtered Notes
    val filteredNotes = combine(allNotes, searchQuery, filterChip) { notes, query, chip ->
        var list = notes
        if (query.isNotEmpty()) {
            list = list.filter {
                it.title.contains(query, ignoreCase = true) ||
                        it.body.contains(query, ignoreCase = true) ||
                        it.tags.contains(query, ignoreCase = true)
            }
        }
        when (chip) {
            "Today" -> {
                val cal = Calendar.getInstance()
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                val startOfToday = cal.timeInMillis
                list = list.filter { it.updatedAt >= startOfToday }
            }
            "Action items" -> {
                list = list.filter { it.checklistJson?.isNotEmpty() == true || it.tags.contains("action", ignoreCase = true) || it.title.contains("action", ignoreCase = true) }
            }
            "Meeting notes" -> {
                list = list.filter { it.tags.contains("meeting", ignoreCase = true) || it.title.contains("meeting", ignoreCase = true) }
            }
            "Ideas" -> {
                list = list.filter { it.tags.contains("ideas", ignoreCase = true) || it.tags.contains("cooking", ignoreCase = true) || it.tags.contains("travel", ignoreCase = true) }
            }
        }
        list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active Note Editing
    val activeNoteId = MutableStateFlow<Long?>(null)
    val activeNote = activeNoteId.flatMapLatest { id ->
        if (id == null) flowOf(null) else repository.getNoteById(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Chat with AI on Active Note / Whole Library
    val chatHistory = MutableStateFlow<List<ChatMessage>>(emptyList())
    val isAiThinking = MutableStateFlow(false)

    private fun updateChatHistory(vararg newMessages: ChatMessage) {
        val current = chatHistory.value.toMutableList()
        current.addAll(newMessages)
        chatHistory.value = current
    }

    // Voice Dictator state
    val isRecording = MutableStateFlow(false)
    val recordingSeconds = MutableStateFlow(0)
    val liveTranscript = MutableStateFlow("")

    // OCR Whiteboard Scan state
    val ocrOutput = MutableStateFlow("")
    val isOcrScanning = MutableStateFlow(false)

    // Settings
    val isOfflineMode = MutableStateFlow(false)
    val appTheme = MutableStateFlow("Light") // "Light", "Dark"
    val dateFormatting = MutableStateFlow("DD/MM/YYYY")
    val selectedModel = MutableStateFlow("Gemini 3.5 Flash")
    val currentLanguage = MutableStateFlow("Bahasa Indonesia") // "Bahasa Indonesia", "English"
    val geminiApiKey = MutableStateFlow("")

    init {
        // Load settings from Jetpack DataStore Preferences on initialization
        viewModelScope.launch {
            context.dataStore.data.collect { preferences ->
                isOfflineMode.value = preferences[OFFLINE_MODE_KEY] ?: false
                appTheme.value = preferences[APP_THEME_KEY] ?: "Light"
                dateFormatting.value = preferences[DATE_FORMAT_KEY] ?: "DD/MM/YYYY"
                selectedModel.value = preferences[SELECTED_MODEL_KEY] ?: "Gemini 3.5 Flash"
                currentLanguage.value = preferences[CURRENT_LANGUAGE_KEY] ?: "Bahasa Indonesia"
                geminiApiKey.value = preferences[GEMINI_API_KEY_PREF] ?: ""
            }
        }

        // Seed default template notes on first run if database is empty (guarantees safe run)
        viewModelScope.launch {
            allNotes.first { notes ->
                if (notes.isEmpty() && !isSeeding) {
                    isSeeding = true
                    seedDefaultNotes()
                    true
                } else {
                    notes.isNotEmpty()
                }
            }
        }

        // Initial cloud sync on startup (only if logged in)
        viewModelScope.launch {
            isLoggedIn.filter { it }.firstOrNull()?.let {
                triggerSync()
            }
        }
    }

    private var isSeeding = false

    fun setOfflineMode(enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[OFFLINE_MODE_KEY] = enabled
            }
            isOfflineMode.value = enabled
            val message = if (enabled) "Offline mode enabled" else "Offline mode disabled"
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    fun setAppTheme(theme: String) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[APP_THEME_KEY] = theme
            }
            appTheme.value = theme
        }
    }

    fun setLanguage(language: String) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[CURRENT_LANGUAGE_KEY] = language
            }
            currentLanguage.value = language
            
            try {
                if (context is android.app.Activity) {
                    context.recreate()
                }
            } catch (e: Exception) {
                Log.e("NoteViewModel", "Failed to recreate activity", e)
            }
        }
    }

    fun setDateFormat(format: String) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[DATE_FORMAT_KEY] = format
            }
            dateFormatting.value = format
        }
    }

    fun setSelectedModel(model: String) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[SELECTED_MODEL_KEY] = model
            }
            selectedModel.value = model
        }
    }

    fun setGeminiApiKey(key: String) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[GEMINI_API_KEY_PREF] = key
            }
            geminiApiKey.value = key
        }
    }

    private suspend fun seedDefaultNotes() {
        val cal = Calendar.getInstance()

        // 1. Q4 planning meeting
        cal.set(2026, Calendar.JUNE, 12, 10, 0)
        val q4Plan = Note(
            title = "Q4 planning meeting",
            body = "Discussed budget and roadmap for the next quarter. Focus on core growth, sustainable architecture transition, and hiring. Key decisions:\n\n- Finalize Q4 roadmap\n- Send invite to design team\n- Book venue for offsite\n\nParticipants: Sarah, James, Maria, Chen.",
            tags = "work,meeting,q4",
            checklistJson = """[
                {"text":"Finalize Q4 roadmap","checked":true},
                {"text":"Send invite to design team","checked":true},
                {"text":"Book venue for offsite","checked":false}
            ]""".trimIndent(),
            createdAt = cal.timeInMillis,
            updatedAt = cal.timeInMillis,
            audioDuration = "1:24"
        )
        repository.insertNote(q4Plan)

        // 2. Recipe - butter chicken
        cal.set(2026, Calendar.JUNE, 11, 18, 0)
        val recipe = Note(
            title = "Recipe — butter chicken",
            body = "Add 500g chicken, yogurt, garam masala, chili powder, lemon, garlic, ginger. Marinate for 1 hour. Pan-fry chicken. Sauce: melt butter, simmer onions, puree tomatoes, heavy cream, cashews, honey.",
            tags = "cooking,ideas",
            createdAt = cal.timeInMillis,
            updatedAt = cal.timeInMillis
        )
        repository.insertNote(recipe)

        // 3. Book notes: Atomic Habits
        cal.set(2026, Calendar.JUNE, 10, 8, 30)
        val bookNotes = Note(
            title = "Book notes: Atomic Habits",
            body = "Small habits make a big difference. 1% better every day. Focus on systems rather than goals. Build identity-based habits instead of outcome-based habits.\n\nFour Laws of Behavior Change:\n1. Make it obvious\n2. Make it attractive\n3. Make it easy\n4. Make it satisfying.",
            tags = "ideas,reading",
            createdAt = cal.timeInMillis,
            updatedAt = cal.timeInMillis
        )
        repository.insertNote(bookNotes)

        // 4. Travel ideas - Japan
        cal.set(2026, Calendar.JUNE, 9, 14, 15)
        val travelJapan = Note(
            title = "Travel ideas — Japan",
            body = "Tokyo, Kyoto, Osaka itinerary suggestions. Book the Kyoto temple tour for Tuesday morning. Explore Shibuya, Shinjuku, try local ramen, visit Fushimi Inari shrine.",
            tags = "travel,personal,ideas",
            checklistJson = """[
                {"text":"Book Kyoto temple tour","checked":false},
                {"text":"Review Shibuya highlights","checked":true}
            ]""".trimIndent(),
            createdAt = cal.timeInMillis,
            updatedAt = cal.timeInMillis
        )
        repository.insertNote(travelJapan)

        // 5. Office Refresh
        cal.set(2026, Calendar.JUNE, 8, 10, 0)
        val officeRefresh = Note(
            title = "Office Refresh",
            body = "Inspiration from the local gallery visit today. Simple minimalist layouts, soft textures, natural wood finishes, dried eucalyptus in standard white ceramic vases.",
            tags = "ideas,project",
            createdAt = cal.timeInMillis,
            updatedAt = cal.timeInMillis,
            // Pre-hotlinked thumbnail
            imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuBVlaUTQsqrj2Fr0SsS5_K2y4Gnssx7_BHIeBHqYBY7FLiLah9mPhghAFVWTY77_iJ2L8T2PSlcbqQ7tawPlXlAeXDdzRGyXDHnBYdHRh42wYfHMlgacgy0vA-8Bz36UZ_W1xMcegZ-2Yfj8qn41eqbqvOFRiSYRDBTON4s7KRpXJ3QdprMxqnyBBdCVR6Q1ABneGcWFr2p0TPMpYPXjEggthlNE-EwYDXvqdZRO18n4OmytACPgAejWdhClCdO0VtYs4vj_9EwpOgF"
        )
        repository.insertNote(officeRefresh)

        // 6. Quick Thought
        cal.set(2026, Calendar.JUNE, 7, 9, 0)
        val quickThought = Note(
            title = "Quick Thought",
            body = "Minimalism is the ultimate sophistication.",
            tags = "quote,personal",
            createdAt = cal.timeInMillis,
            updatedAt = cal.timeInMillis
        )
        repository.insertNote(quickThought)
    }

    // Navigation triggers
    fun selectActiveNote(noteId: Long?) {
        activeNoteId.value = noteId
        chatHistory.value = emptyList() // clear chat on active note change
        if (noteId != null) {
            currentScreen.value = "editor"
        }
    }

    fun navigateTo(screen: String) {
        // Auto-cleanup: if leaving editor with an empty note, delete it
        if (currentScreen.value == "editor" && screen != "editor") {
            val currentId = activeNoteId.value
            if (currentId != null) {
                viewModelScope.launch {
                    try {
                        val note = repository.getNoteByIdSync(currentId)
                        if (note != null && note.title.isBlank() && note.body.isBlank()) {
                            repository.deleteNoteById(currentId)
                        }
                    } finally {
                        currentScreen.value = screen
                    }
                }
                return
            }
        }
        currentScreen.value = screen
    }

    fun triggerSync() {
        if (isOfflineMode.value) return
        val firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (firebaseUser == null || firebaseUser.isAnonymous) {
            // Guest/anonymous users: skip sync silently, no error banner
            return
        }
        viewModelScope.launch {
            isSyncing.value = true
            lastSyncError.value = null
            syncManager.syncNotes().fold(
                onSuccess = { isSyncing.value = false },
                onFailure = {
                    lastSyncError.value = it.message
                    isSyncing.value = false
                }
            )
        }
    }

    fun toggleNotePin(noteId: Long) {
        viewModelScope.launch {
            repository.togglePin(noteId)
        }
        triggerSync()
    }

    // CRUD Note operations
    fun createEmptyNote() {
        viewModelScope.launch {
            val emptyNote = Note(
                title = "",
                body = "",
                tags = "ideas",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            val newId = repository.insertNote(emptyNote)
            selectActiveNote(newId)
        }
        triggerSync()
    }

    fun updateActiveNote(title: String, body: String, tags: String, checklistJson: String? = null) {
        val currentId = activeNoteId.value ?: return
        viewModelScope.launch {
            val current = repository.getNoteByIdSync(currentId) ?: return@launch
            val updated = current.copy(
                title = title,
                body = body,
                tags = tags,
                checklistJson = checklistJson,
                updatedAt = System.currentTimeMillis(),
                cloudSynced = false
            )
            repository.insertNote(updated)
        }
        triggerSync()
    }

    fun deleteActiveNote() {
        val currentId = activeNoteId.value ?: return
        viewModelScope.launch {
            val note = repository.getNoteByIdSync(currentId)
            note?.let {
                if (it.noteUuid.isNotEmpty()) {
                    syncManager.deleteNoteFromCloud(it.noteUuid)
                }
            }
            repository.deleteNoteById(currentId)
            selectActiveNote(null)
            navigateTo("home_list")
        }
    }

    fun deleteEmptyNotes() {
        viewModelScope.launch {
            repository.deleteEmptyNotes()
        }
    }

    // Checklist toggles
    fun toggleChecklistItem(noteId: Long, index: Int) {
        viewModelScope.launch {
            val note = repository.getNoteByIdSync(noteId) ?: return@launch
            val jsonStr = note.checklistJson ?: return@launch
            try {
                val array = JSONArray(jsonStr)
                if (index in 0 until array.length()) {
                    val obj = array.getJSONObject(index)
                    val currentChecked = obj.optBoolean("checked", false)
                    obj.put("checked", !currentChecked)
                    val updatedNote = note.copy(
                        checklistJson = array.toString(),
                        updatedAt = System.currentTimeMillis()
                    )
                    repository.insertNote(updatedNote)
                }
            } catch (e: Exception) {
                Log.e("NoteViewModel", "Failed to toggle checklist item", e)
            }
        }
    }

    // Gemini Integrations (Real API calls!)
    fun speakToAI(userMessage: String) {
        if (userMessage.isBlank()) return
        updateChatHistory(ChatMessage(sender = "user", content = userMessage))

        isAiThinking.value = true

        viewModelScope.launch {
            // Construct context from active note
            val active = activeNote.value
            val contextPrompt = if (active != null) {
                "You are NoteAi, an intelligent offline-first assistant. The user is currently editing a note styled with 'Quiet Intelligence'.\nActive Note Title: ${active.title}\nActive Note Body: ${active.body}\nTags: ${active.tags}\n\nAnswer concisely based on this note."
            } else {
                val notes = allNotes.value.take(10).joinToString("\n---\n") { "${it.title}: ${it.body}" }
                "You are NoteAi, an intelligent assistant. Here is a summary of the user's weekly notes:\n$notes\n\nAnswering helpfully, concisely, and gracefully."
            }

            val systemInstruction = "Identify yourself simply as NoteAi of Quiet Intelligence. Keep answers clean, conversational, simple and structured using bullets or concise statements."
            val aiResponse = GeminiService.generateContent(
                prompt = "$contextPrompt\n\nUser Question: $userMessage",
                systemInstruction = systemInstruction,
                apiKey = geminiApiKey.value.ifEmpty { null }
            )

            updateChatHistory(ChatMessage(sender = "ai", content = aiResponse))
            isAiThinking.value = false
        }
    }

    fun summarizeActiveNote() {
        val active = activeNote.value ?: return
        isAiThinking.value = true
        viewModelScope.launch {
            val prompt = "Summarize this note in 2-3 extremely clear bullet points, pointing out final decisions:\nNote title: ${active.title}\nNote body: ${active.body}"
            val response = GeminiService.generateContent(prompt, apiKey = geminiApiKey.value.ifEmpty { null })

            updateChatHistory(
                ChatMessage(sender = "user", content = "Summarize this note"),
                ChatMessage(sender = "ai", content = response)
            )

            isAiThinking.value = false
        }
    }

    fun extractTasksFromActiveNote() {
        val active = activeNote.value ?: return
        isAiThinking.value = true
        viewModelScope.launch {
            val prompt = "Extract actionable items / to-do elements from this note. Format purely as a JSON Array of JSON Objects like: [{\"text\":\"Action item text\",\"checked\":false}] without any markdown wrapping (just raw payload)."
            val response = GeminiService.generateContent(prompt, apiKey = geminiApiKey.value.ifEmpty { null })
            try {
                // Parse checklist from generated content
                val cleanResponse = response.replace("```json", "").replace("```", "").trim()
                val array = JSONArray(cleanResponse)
                val updatedNote = active.copy(
                    checklistJson = array.toString(),
                    updatedAt = System.currentTimeMillis()
                )
                repository.insertNote(updatedNote)

                updateChatHistory(ChatMessage(sender = "ai", content = "I've extracted ${array.length()} tasks into your checklist! Check them off in your editor."))
            } catch (e: Exception) {
                // Return simple checklist if JSON fails
                val listPrompt = "List extracted tasks from this note as simple checklist lines, numbered."
                val textResponse = GeminiService.generateContent(listPrompt, apiKey = geminiApiKey.value.ifEmpty { null })
                updateChatHistory(ChatMessage(sender = "ai", content = "Here are the extracted items:\n\n$textResponse"))
            }
            isAiThinking.value = false
        }
    }

    fun autoTagActiveNote() {
        val active = activeNote.value ?: return
        viewModelScope.launch {
            val prompt = "Suggest exactly 3 lowercase, short single-word tags for this note content as a comma-separated list, e.g. 'ideas,cooking,travel'. Note content: ${active.body}"
            val response = GeminiService.generateContent(prompt, apiKey = geminiApiKey.value.ifEmpty { null })
            val cleanTags = response.trim().replace(" ", "")
            if (cleanTags.isNotEmpty() && !cleanTags.startsWith("Error") && !cleanTags.startsWith("API")) {
                val updated = active.copy(
                    tags = cleanTags,
                    updatedAt = System.currentTimeMillis()
                )
                repository.insertNote(updated)
            }
        }
    }

    fun generateTitleFromContent() {
        val active = activeNote.value ?: return
        if (active.body.isBlank()) return
        viewModelScope.launch {
            val prompt = "Generate a concise title of at most 4-5 words for this note body: ${active.body}. Return only the title text itself without enclosing quotes."
            val response = GeminiService.generateContent(prompt, apiKey = geminiApiKey.value.ifEmpty { null })
            val cleanTitle = response.trim()
            if (cleanTitle.isNotEmpty() && !cleanTitle.startsWith("Error")) {
                val updated = active.copy(
                    title = cleanTitle,
                    updatedAt = System.currentTimeMillis()
                )
                repository.insertNote(updated)
            }
        }
    }

    // Voice Dictator dictation
    private var voiceTimerJob: kotlinx.coroutines.Job? = null
    private val dictationBuffer = StringBuilder()

    fun startDictation() {
        isRecording.value = true
        recordingSeconds.value = 0
        liveTranscript.value = ""
        dictationBuffer.clear()
        voiceTimerJob = viewModelScope.launch(Dispatchers.Main) {
            while (isRecording.value) {
                delay(1000)
                recordingSeconds.value += 1
            }
        }
    }

    fun updateLiveTranscript(text: String) {
        if (text.isNotEmpty()) {
            if (dictationBuffer.isNotEmpty()) {
                dictationBuffer.append(" ")
            }
            dictationBuffer.append(text)
            liveTranscript.value = dictationBuffer.toString()
        }
    }

    fun appendPartialTranscript(text: String) {
        liveTranscript.value = if (dictationBuffer.isNotEmpty()) {
            "$dictationBuffer $text"
        } else {
            text
        }
    }

    fun stopDictation() {
        isRecording.value = false
        voiceTimerJob?.cancel()
        voiceTimerJob = null
    }

    fun saveDictationAsNote() {
        stopDictation()
        val transcript = liveTranscript.value.trim()
        if (transcript.isBlank()) return

        viewModelScope.launch {
            val newNote = Note(
                title = "Voice note - ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())}",
                body = transcript,
                tags = "ideas,voice",
                audioDuration = getDurationString(recordingSeconds.value),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            val newId = repository.insertNote(newNote)
            selectActiveNote(newId)
        }
    }

    fun getDurationString(seconds: Int): String {
        val min = seconds / 60
        val sec = seconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", min, sec)
    }

    // Real CameraX & Gemini Vision Integration
    val aiRepository = AIRepository()
    val capturedImagePath = MutableStateFlow<String?>(null)
    val imageOcrText = MutableStateFlow("")
    val imageDescription = MutableStateFlow("")
    val isVisionLoading = MutableStateFlow(false)

    fun processCapturedImage(bytes: ByteArray, localPath: String) {
        capturedImagePath.value = localPath
        imageOcrText.value = "Processing image text..."
        imageDescription.value = ""
        isVisionLoading.value = true
        
        viewModelScope.launch {
            val ocrResult = aiRepository.ocrImage(bytes, apiKey = geminiApiKey.value.ifEmpty { null })
            ocrResult.fold(
                onSuccess = {
                    imageOcrText.value = it
                },
                onFailure = {
                    imageOcrText.value = "Error: ${it.localizedMessage ?: "Failed to read text"}"
                }
            )
            isVisionLoading.value = false
        }
    }

    fun describeCapturedImage(bytes: ByteArray) {
        isVisionLoading.value = true
        imageDescription.value = "Generating description..."
        viewModelScope.launch {
            val result = aiRepository.describeImage(bytes, apiKey = geminiApiKey.value.ifEmpty { null })
            result.fold(
                onSuccess = {
                    imageDescription.value = it
                },
                onFailure = {
                    imageDescription.value = "Error: ${it.localizedMessage ?: "Failed to generate description"}"
                }
            )
            isVisionLoading.value = false
        }
    }

    fun searchRelatedNotes(bytes: ByteArray) {
        isVisionLoading.value = true
        viewModelScope.launch {
            val notesList = allNotes.value
            val result = aiRepository.visualSearch(bytes, notesList, apiKey = geminiApiKey.value.ifEmpty { null })
            result.fold(
                onSuccess = {
                    updateChatHistory(
                        ChatMessage(sender = "user", content = "Visual Search Notes"),
                        ChatMessage(sender = "ai", content = it)
                    )
                },
                onFailure = {
                    updateChatHistory(ChatMessage(sender = "ai", content = "Search failed: ${it.localizedMessage}"))
                }
            )
            isVisionLoading.value = false
        }
    }

    fun saveOcrImageAsNote() {
        val ocr = imageOcrText.value
        val path = capturedImagePath.value
        if (ocr.isBlank() || ocr.startsWith("Processing")) return
        viewModelScope.launch {
            val newNote = Note(
                title = "Vision Scan - ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}",
                body = ocr,
                tags = "project,work",
                imageUrl = path,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            val newId = repository.insertNote(newNote)
            selectActiveNote(newId)
        }
    }

    fun readImageTextFromUri(bytes: ByteArray, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val ocrResult = aiRepository.ocrImage(bytes, apiKey = geminiApiKey.value.ifEmpty { null })
            ocrResult.fold(
                onSuccess = { onResult(it) },
                onFailure = { onResult("Error: ${it.localizedMessage}") }
            )
        }
    }

    fun describeImageFromUri(bytes: ByteArray, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val descResult = aiRepository.describeImage(bytes, apiKey = geminiApiKey.value.ifEmpty { null })
            descResult.fold(
                onSuccess = { onResult(it) },
                onFailure = { onResult("Error: ${it.localizedMessage}") }
            )
        }
    }

    // Real Gemini Vision OCR Scanning
    fun scanWhiteboard(imageBytes: ByteArray) {
        isOcrScanning.value = true
        ocrOutput.value = "Scanning..."
        viewModelScope.launch {
            val result = aiRepository.ocrImage(imageBytes, apiKey = geminiApiKey.value.ifEmpty { null })
            result.fold(
                onSuccess = { ocrOutput.value = it },
                onFailure = { ocrOutput.value = "Scan failed: ${it.localizedMessage ?: "Unknown error"}" }
            )
            isOcrScanning.value = false
        }
    }

    fun saveOcrAsNote() {
        val content = ocrOutput.value
        if (content.isBlank() || content.startsWith("Scanning")) return
        viewModelScope.launch {
            val newNote = Note(
                title = "Whiteboard Scan",
                body = content,
                tags = "project,work",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            val newId = repository.insertNote(newNote)
            selectActiveNote(newId)
        }
    }
}

data class ChatMessage(
    val sender: String, // "user" or "ai"
    val content: String
)
