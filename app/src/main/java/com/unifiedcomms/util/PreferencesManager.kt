package com.unifiedcomms.util

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.lang.reflect.Type
import java.security.KeyStore

object PreferencesManager {
    private const val PREFS_NAME = "unifiedcomms_prefs"
    private var instance: PreferencesManager? = null
    private lateinit var encryptedPrefs: SharedPreferences
    private val gson = Gson()

    fun initialize(context: Context) {
        if (instance == null) {
            instance = PreferencesManager(context)
        }
    }

    fun getInstance(): PreferencesManager = instance!!

    private constructor(context: Context) {
        val masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        encryptedPrefs = EncryptedSharedPreferences.create(
            PREFS_NAME,
            masterKey,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun putString(key: String, value: String) {
        encryptedPrefs.edit().putString(key, value).apply()
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

    fun putLong(key: String, value: Long) {
        encryptedPrefs.edit().putLong(key, value).apply()
    }

    fun getLong(key: String, default: Long = 0L): Long = encryptedPrefs.getLong(key, default)

    fun putStringSet(key: String, value: Set<String>) {
        encryptedPrefs.edit().putStringSet(key, value).apply()
    }

    fun getStringSet(key: String): Set<String>? = encryptedPrefs.getStringSet(key)

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
}

object CoroutineProvider {
    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val defaultScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    val main: CoroutineScope = mainScope
    val io: CoroutineScope = ioScope
    val default: CoroutineScope = defaultScope

    fun shutdown() {
        mainScope.coroutineContext.cancelChildren()
        ioScope.coroutineContext.cancelChildren()
        defaultScope.coroutineContext.cancelChildren()
    }
}

inline fun <reified T> PreferencesManager.getObject(key: String, default: T): T {
    val type = object : TypeToken<T>() {}.type
    return getObject(key, type, default)
}