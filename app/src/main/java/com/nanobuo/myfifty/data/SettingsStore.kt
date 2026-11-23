package com.nanobuo.myfifty.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nanobuo.myfifty.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode(val label: String) { SYSTEM("Sistema"), LIGHT("Chiaro"), DARK("Scuro") }

data class SupabaseConfig(val url: String, val anonKey: String)

private val Context.dataStore by preferencesDataStore("myfifty_settings")

class SettingsStore(private val context: Context) {
    private val kTheme = stringPreferencesKey("theme")
    private val kUrl = stringPreferencesKey("supabase_url")
    private val kKey = stringPreferencesKey("supabase_key")
    private val kHousehold = stringPreferencesKey("household_id")

    val theme: Flow<ThemeMode> = context.dataStore.data.map { p ->
        p[kTheme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }

    /** Config da BuildConfig se presente, altrimenti quella inserita nell'app. */
    val supabaseConfig: Flow<SupabaseConfig?> = context.dataStore.data.map { p ->
        val url = p[kUrl].orEmpty().ifBlank { BuildConfig.SUPABASE_URL }
        val key = p[kKey].orEmpty().ifBlank { BuildConfig.SUPABASE_ANON_KEY }
        if (url.isBlank() || key.isBlank()) null else SupabaseConfig(url.trim().trimEnd('/'), key.trim())
    }

    val householdId: Flow<String?> = context.dataStore.data.map { it[kHousehold] }

    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[kTheme] = mode.name }

    suspend fun setSupabaseConfig(url: String, key: String) = context.dataStore.edit {
        it[kUrl] = url.trim(); it[kKey] = key.trim()
    }

    suspend fun clearSupabaseConfig() = context.dataStore.edit { it.remove(kUrl); it.remove(kKey); it.remove(kHousehold) }

    suspend fun setHouseholdId(id: String?) = context.dataStore.edit {
        if (id == null) it.remove(kHousehold) else it[kHousehold] = id
    }
}
