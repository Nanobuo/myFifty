package com.nanobuo.myfifty.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode(val label: String) { SYSTEM("Sistema"), LIGHT("Chiaro"), DARK("Scuro") }

data class SupabaseConfig(val url: String, val anonKey: String) {
    companion object {
        /** URL e chiave sono inseriti nell'APK al momento della build (vedi supabase.properties). */
        fun fromBuild(): SupabaseConfig? {
            val url = normalizeUrl(com.nanobuo.myfifty.BuildConfig.SUPABASE_URL)
            val key = com.nanobuo.myfifty.BuildConfig.SUPABASE_ANON_KEY.trim().trim('"')
            return if (url.isBlank() || key.isBlank()) null else SupabaseConfig(url, key)
        }

        /**
         * Tiene solo schema e host: "xyz.supabase.co/rest/v1/" -> "https://xyz.supabase.co".
         * (supabase-kt rifiuta URL che contengono /rest/v1, /auth/v1, ...)
         */
        fun normalizeUrl(raw: String): String {
            val s = raw.trim().trim('"').trim()
            if (s.isEmpty()) return ""
            val scheme = if (s.startsWith("http://")) "http://" else "https://"
            val host = s.substringAfter("://").substringBefore('/').substringBefore('?').trim()
            return if (host.isEmpty()) "" else scheme + host
        }
    }
}

private val Context.dataStore by preferencesDataStore("myfifty_settings")

class SettingsStore(private val context: Context) {
    private val kTheme = stringPreferencesKey("theme")
    private val kHousehold = stringPreferencesKey("household_id")
    private val kLastUpdateCheck = longPreferencesKey("last_update_check")

    val theme: Flow<ThemeMode> = context.dataStore.data.map { p ->
        p[kTheme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }

    val householdId: Flow<String?> = context.dataStore.data.map { it[kHousehold] }

    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[kTheme] = mode.name }

    val lastUpdateCheck: Flow<Long> = context.dataStore.data.map { it[kLastUpdateCheck] ?: 0L }

    suspend fun setLastUpdateCheck(millis: Long) = context.dataStore.edit { it[kLastUpdateCheck] = millis }

    suspend fun setHouseholdId(id: String?) = context.dataStore.edit {
        if (id == null) it.remove(kHousehold) else it[kHousehold] = id
    }
}
