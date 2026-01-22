package com.senderlink.app.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 💾 HomeDataStore
 *
 * Almacenamiento local para:
 * - Rutas recientes (última visitadas)
 * - Rutas destacadas (caché)
 *
 * USA: DataStore de Android (reemplazo de SharedPreferences)
 */
object HomeDataStore {

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "home_cache")

    private val KEY_RECENTS = stringPreferencesKey("recent_routes_json")
    private val KEY_FEATURED = stringPreferencesKey("featured_routes_json")

    // ==========================================
    // 🕐 RUTAS RECIENTES
    // ==========================================

    suspend fun saveRecentsJson(context: Context, json: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_RECENTS] = json
        }
    }

    suspend fun loadRecentsJson(context: Context): String {
        return context.dataStore.data.map { preferences ->
            preferences[KEY_RECENTS] ?: "[]"
        }.first()
    }

    // ==========================================
    // ⭐ RUTAS DESTACADAS (CACHÉ)
    // ==========================================

    suspend fun saveFeaturedJson(context: Context, json: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_FEATURED] = json
        }
    }

    suspend fun loadFeaturedJson(context: Context): String {
        return context.dataStore.data.map { preferences ->
            preferences[KEY_FEATURED] ?: "[]"
        }.first()
    }

    // ==========================================
    // 🗑️ LIMPIAR CACHÉ
    // ==========================================

    suspend fun clearAll(context: Context) {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    suspend fun clearRecents(context: Context) {
        context.dataStore.edit { preferences ->
            preferences.remove(KEY_RECENTS)
        }
    }

    suspend fun clearFeatured(context: Context) {
        context.dataStore.edit { preferences ->
            preferences.remove(KEY_FEATURED)
        }
    }
}