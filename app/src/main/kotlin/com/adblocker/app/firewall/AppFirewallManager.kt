package com.adblocker.app.firewall

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class InstalledApp(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    val isBypassedFromVpn: Boolean = false,
    val isInternetBlocked: Boolean = false
)

class AppFirewallManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("app_firewall_prefs", Context.MODE_PRIVATE)

    private val _appsList = MutableStateFlow<List<InstalledApp>>(emptyList())
    val appsList: StateFlow<List<InstalledApp>> = _appsList.asStateFlow()

    private val bypassedPackages = HashSet<String>()
    private val blockedPackages = HashSet<String>()

    init {
        loadPersistedRules()
    }

    private fun loadPersistedRules() {
        val bypassedSet = prefs.getStringSet("bypassed_apps", emptySet()) ?: emptySet()
        val blockedSet = prefs.getStringSet("blocked_apps", emptySet()) ?: emptySet()
        bypassedPackages.addAll(bypassedSet)
        blockedPackages.addAll(blockedSet)
    }

    suspend fun refreshApps() = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val selfPkg = context.packageName

        val result = installed
            .filter { it.packageName != selfPkg }
            .map { appInfo ->
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val label = try {
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    appInfo.packageName
                }
                InstalledApp(
                    packageName = appInfo.packageName,
                    appName = label,
                    isSystemApp = isSystem,
                    isBypassedFromVpn = bypassedPackages.contains(appInfo.packageName),
                    isInternetBlocked = blockedPackages.contains(appInfo.packageName)
                )
            }
            .sortedWith(compareBy({ it.isSystemApp }, { it.appName.lowercase() }))

        _appsList.value = result
    }

    fun setAppBypassed(packageName: String, bypassed: Boolean) {
        if (bypassed) {
            bypassedPackages.add(packageName)
        } else {
            bypassedPackages.remove(packageName)
        }
        prefs.edit().putStringSet("bypassed_apps", bypassedPackages).apply()
        updateMemoryApp(packageName) { it.copy(isBypassedFromVpn = bypassed) }
    }

    fun setAppInternetBlocked(packageName: String, blocked: Boolean) {
        if (blocked) {
            blockedPackages.add(packageName)
        } else {
            blockedPackages.remove(packageName)
        }
        prefs.edit().putStringSet("blocked_apps", blockedPackages).apply()
        updateMemoryApp(packageName) { it.copy(isInternetBlocked = blocked) }
    }

    private fun updateMemoryApp(packageName: String, transform: (InstalledApp) -> InstalledApp) {
        _appsList.value = _appsList.value.map {
            if (it.packageName == packageName) transform(it) else it
        }
    }

    fun isAppBlocked(packageName: String): Boolean = blockedPackages.contains(packageName)
    fun getBypassedApps(): Set<String> = HashSet(bypassedPackages)
}
