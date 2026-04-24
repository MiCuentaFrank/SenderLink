package com.senderlink.app.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.senderlink.app.model.Route
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

    private val gson = Gson()
    private const val MAX_RECENTS = 20

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
    // RUTAS RECIENTES (objetos Route)
    // ==========================================

    suspend fun getRecentRoutes(context: Context): List<Route> {
        return try {
            val json = loadRecentsJson(context)
            val type = object : TypeToken<List<Route>>() {}.type
            gson.fromJson<List<Route>>(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun addRecentRoute(context: Context, route: Route) {
        try {
            val current = getRecentRoutes(context).toMutableList()
            // Eliminar si ya existe (para moverlo al inicio)
            current.removeAll { it.id == route.id }
            // Añadir al inicio
            current.add(0, route)
            // Limitar tamaño
            val trimmed = current.take(MAX_RECENTS)
            saveRecentsJson(context, gson.toJson(trimmed))
        } catch (_: Exception) {
            // Silenciar errores de serialización
        }
    }

    // ==========================================
    // LIMPIAR CACHE
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