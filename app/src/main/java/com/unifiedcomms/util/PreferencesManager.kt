package com.unifiedcomms.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.plus
import java.lang.reflect.Type

class PreferencesManager private constructor(
    private val encryptedPrefs: SharedPreferences,
    private val gson: Gson
) {
    companion object {
        private const val PREFS_NAME = "unifiedcomms_prefs"
        @Volatile private var instance: PreferencesManager? = null

        fun initialize(context: Context) {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
                        val encryptedPrefs = EncryptedSharedPreferences.create(
                            context,
                            PREFS_NAME,
                            masterKey,
                            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                        )
                        instance = PreferencesManager(encryptedPrefs, Gson())
                    }
                }
            }
        }

        fun getInstance(): PreferencesManager = instance!!
    }

    fun putString(key: String, value: String) {
        encryptedPrefs.edit().putString(key, value).apply()
        if (key == "theme_mode") _themeMode.update { value }
    }

    fun getString(key: String, default: String = ""): String = encryptedPrefs.getString(key, default) ?: default

    fun putBoolean(key: String, value: Boolean) {
        encryptedPrefs.edit().putBoolean(key, value).apply()
    }

    fun getBoolean(key: String, default: Boolean = false): Boolean = encryptedPrefs.getBoolean(key, default)

    fun putInt(key: String, value: Int) {
        encryptedPrefs.edit().putInt(key, value).apply()
    }

    fun getInt(key: String, default: Int = 0): Int = encryptedPrefs.getInt(key, default)

    fun getSyncIntervalMinutes(default: Int = 15): Int {
        val raw = encryptedPrefs.getString("sync_interval_minutes", null)
        val parsed = raw?.toIntOrNull()
        return when (parsed) {
            15, 30, 60, 180, 360, 720, -1 -> parsed
            else -> default
        }
    }

    fun putSyncIntervalMinutes(value: Int) {
        encryptedPrefs.edit().putInt("sync_interval_minutes", value).apply()
    }

    fun putLong(key: String, value: Long) {
        encryptedPrefs.edit().putLong(key, value).apply()
    }

    fun getLong(key: String, default: Long = 0L): Long = encryptedPrefs.getLong(key, default)

    fun putStringSet(key: String, value: Set<String>) {
        encryptedPrefs.edit().putStringSet(key, value).apply()
    }

    fun getStringSet(key: String): Set<String>? = encryptedPrefs.getStringSet(key, emptySet())

    fun <T> putObject(key: String, value: T) {
        putString(key, gson.toJson(value))
    }

    fun <T> getObject(key: String, type: Type, default: T): T {
        val json = getString(key, "")
        return if (json.isNotEmpty()) gson.fromJson(json, type) else default
    }

    fun remove(key: String) {
        encryptedPrefs.edit().remove(key).apply()
    }

    fun clear() {
        encryptedPrefs.edit().clear().apply()
    }

    fun contains(key: String): Boolean = encryptedPrefs.contains(key)

    private val _themeMode = MutableStateFlow(getString("theme_mode", "system"))
    val themeModeFlow: Flow<String> = _themeMode.asStateFlow()

    fun putThemeMode(value: String) {
        putString("theme_mode", value)
    }
}

inline fun <reified T> PreferencesManager.getObject(key: String, default: T): T {
    val type = object : TypeToken<T>() {}.type
    return getObject(key, type, default)
}

object CoroutineProvider {
    private val mainScope = CoroutineScope(Dispatchers.Main + Job())
    private val ioScope = CoroutineScope(Dispatchers.IO + Job())
    private val defaultScope = CoroutineScope(Dispatchers.Default + Job())

    val main: CoroutineScope = mainScope
    val io: CoroutineScope = ioScope
    val default: CoroutineScope = defaultScope

    fun shutdown() {
        mainScope.coroutineContext[Job]?.cancel()
        ioScope.coroutineContext[Job]?.cancel()
        defaultScope.coroutineContext[Job]?.cancel()
    }
}