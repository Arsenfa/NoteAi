package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import com.example.viewmodel.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale

class LocaleManager(private val context: Context) {
    companion object {
        val APP_LANGUAGE_KEY = stringPreferencesKey("app_language")
    }

    val appLanguageFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[APP_LANGUAGE_KEY] ?: "en"
    }

    suspend fun setLanguage(langCode: String) {
        val cleanCode = if (langCode.lowercase().contains("indonesian") || langCode.lowercase().contains("indonesia")) "in" else "en"
        context.dataStore.edit { preferences ->
            preferences[APP_LANGUAGE_KEY] = cleanCode
        }
        applyLocale(cleanCode)
    }

    fun applyLocale(langCode: String) {
        val cleanCode = if (langCode.lowercase().contains("indonesian") || langCode.lowercase().contains("indonesia")) "in" else "en"
        val locale = Locale(cleanCode)
        Locale.setDefault(locale)
        val config = context.resources.configuration
        config.setLocale(locale)
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }
}
