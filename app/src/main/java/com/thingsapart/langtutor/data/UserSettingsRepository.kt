package com.thingsapart.langtutor.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Create the DataStore instance using the preferencesDataStore delegate
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

class UserSettingsRepository(private val context: Context) {

    private object PreferencesKeys {
        val NATIVE_LANGUAGE_CODE = stringPreferencesKey("native_language_code")
    }

    val nativeLanguage: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.NATIVE_LANGUAGE_CODE]
        }

    suspend fun saveNativeLanguage(languageCode: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.NATIVE_LANGUAGE_CODE] = languageCode
        }
    }
}
