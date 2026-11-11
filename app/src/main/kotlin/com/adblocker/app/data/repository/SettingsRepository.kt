package com.adblocker.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.adblocker.core.resolver.UpstreamConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "adblocker_settings")

enum class BlockingMode {
    NXDOMAIN,
    NULL_IP // 0.0.0.0
}

class SettingsRepository(private val context: Context) {

    private val KEY_UPSTREAM_ID = stringPreferencesKey("upstream_id")
    private val KEY_CUSTOM_UPSTREAM_URL = stringPreferencesKey("custom_upstream_url")
    private val KEY_BLOCKING_MODE = stringPreferencesKey("blocking_mode")
    private val KEY_AUTO_START_BOOT = booleanPreferencesKey("auto_start_boot")
    private val KEY_SUBSCRIPTION_URLS = stringPreferencesKey("subscription_urls")

    val selectedUpstreamId: Flow<String> = context.dataStore.data.map {
        it[KEY_UPSTREAM_ID] ?: UpstreamConfig.DEFAULT.id
    }

    val customUpstreamUrl: Flow<String> = context.dataStore.data.map {
        it[KEY_CUSTOM_UPSTREAM_URL] ?: ""
    }

    val blockingMode: Flow<BlockingMode> = context.dataStore.data.map {
        val name = it[KEY_BLOCKING_MODE] ?: BlockingMode.NXDOMAIN.name
        try { BlockingMode.valueOf(name) } catch (e: Exception) { BlockingMode.NXDOMAIN }
    }

    val autoStartOnBoot: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_AUTO_START_BOOT] ?: false
    }

    val subscriptionUrls: Flow<List<String>> = context.dataStore.data.map {
        val raw = it[KEY_SUBSCRIPTION_URLS] ?: ""
        if (raw.isEmpty()) {
            listOf(
                "https://adguardteam.github.io/HostlistsRegistry/assets/filter_1.txt",
                "https://raw.githubusercontent.com/privacy-protection-tools/anti-AD/master/anti-ad-domains.txt"
            )
        } else {
            raw.split("\n").filter { line -> line.isNotBlank() }
        }
    }

    suspend fun setUpstreamId(id: String) {
        context.dataStore.edit { it[KEY_UPSTREAM_ID] = id }
    }

    suspend fun setCustomUpstreamUrl(url: String) {
        context.dataStore.edit { it[KEY_CUSTOM_UPSTREAM_URL] = url }
    }

    suspend fun setBlockingMode(mode: BlockingMode) {
        context.dataStore.edit { it[KEY_BLOCKING_MODE] = mode.name }
    }

    suspend fun setAutoStartOnBoot(enable: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_START_BOOT] = enable }
    }

    suspend fun setSubscriptionUrls(urls: List<String>) {
        context.dataStore.edit { it[KEY_SUBSCRIPTION_URLS] = urls.joinToString("\n") }
    }
}
