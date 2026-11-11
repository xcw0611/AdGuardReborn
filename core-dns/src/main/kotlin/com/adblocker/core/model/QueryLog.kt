package com.adblocker.core.model

data class QueryLog(
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val domain: String,
    val queryType: String = "A",
    val isBlocked: Boolean,
    val matchedRule: String? = null,
    val upstreamName: String,
    val latencyMs: Long,
    val appPackageName: String? = null,
    val appName: String? = null
)
