package com.adblocker.app

import android.app.Application
import com.adblocker.app.data.db.AppDatabase
import com.adblocker.app.data.repository.QueryLogRepository
import com.adblocker.app.data.repository.SettingsRepository
import com.adblocker.app.firewall.AppFirewallManager
import com.adblocker.app.vpn.VpnStateHolder
import com.adblocker.core.filter.FilterListManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AdBlockerApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var database: AppDatabase
        private set

    lateinit var queryLogRepository: QueryLogRepository
        private set

    lateinit var filterListManager: FilterListManager
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var firewallManager: AppFirewallManager
        private set

    override fun onCreate() {
        super.onCreate()

        database = AppDatabase.getInstance(this)
        queryLogRepository = QueryLogRepository(database.queryLogDao(), appScope)
        filterListManager = FilterListManager()
        settingsRepository = SettingsRepository(this)
        firewallManager = AppFirewallManager(this)

        loadDefaultRules()
    }

    private fun loadDefaultRules() {
        appScope.launch(Dispatchers.IO) {
            try {
                assets.open("preset_rules.txt").use { input ->
                    filterListManager.loadRulesFromStream(input)
                }
            } catch (e: Exception) {
                // If asset file missing or fails, populate standard base ad-blocking rules
                populateEmergencyBaseRules()
            }
            VpnStateHolder.updateRulesCount(filterListManager.rulesCount)
        }
    }

    private fun populateEmergencyBaseRules() {
        val baseRules = listOf(
            "||doubleclick.net^",
            "||googleadservices.com^",
            "||admob.com^",
            "||pagead2.googlesyndication.com^",
            "||unityads.unity3d.com^",
            "||appllovin.com^",
            "||vungle.com^",
            "||inmobi.com^",
            "||mobads.baidu.com^",
            "||pos.baidu.com^",
            "||cpro.baidu.com^",
            "||adnet.qq.com^",
            "||e.qq.com^",
            "||gdt.qq.com^",
            "||pgdt.gtimg.cn^",
            "||pangolin-sdk-toutiao.com^",
            "||pglstatp-toutiao.com^",
            "||union.toutiao.com^",
            "||ads.kuaishou.com^",
            "||adx.xiaomi.com^",
            "||log.ad.xiaomi.com^",
            "||adlog.vivo.com.cn^",
            "||adx.ads.oppomobile.com^",
            "||metrics.icloud.com^",
            "||iadsdk.apple.com^"
        )
        for (r in baseRules) {
            val parsed = com.adblocker.core.filter.RuleParser.parseLine(r) ?: continue
            filterListManager.addCustomBlockDomain(parsed.domain)
        }
    }
}
