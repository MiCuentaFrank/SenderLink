package com.senderlink.app.utils

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore(name = "home_datastore")

object HomeDataStore {
    private val KEY_RECENTS_JSON = stringPreferencesKey("recents_json")

    suspend fun saveRecentsJson(context: Context, json: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_RECENTS_JSON] = json
        }
    }

    suspend fun loadRecentsJson(context: Context): String {
        val prefs: Preferences = context.dataStore.data.first()
        return prefs[KEY_RECENTS_JSON] ?: "[]"
    }
}
