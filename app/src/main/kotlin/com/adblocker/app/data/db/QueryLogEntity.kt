package com.adblocker.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.adblocker.core.model.QueryLog

@Entity(tableName = "query_logs")
data class QueryLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val domain: String,
    val queryType: String,
    val isBlocked: Boolean,
    val matchedRule: String?,
    val upstreamName: String,
    val latencyMs: Long,
    val appPackageName: String?,
    val appName: String?
) {
    fun toModel(): QueryLog = QueryLog(
        id = id,
        timestamp = timestamp,
        domain = domain,
        queryType = queryType,
        isBlocked = isBlocked,
        matchedRule = matchedRule,
        upstreamName = upstreamName,
        latencyMs = latencyMs,
        appPackageName = appPackageName,
        appName = appName
    )

    companion object {
        fun fromModel(model: QueryLog): QueryLogEntity = QueryLogEntity(
            id = model.id,
            timestamp = model.timestamp,
            domain = model.domain,
            queryType = model.queryType,
            isBlocked = model.isBlocked,
            matchedRule = model.matchedRule,
            upstreamName = model.upstreamName,
            latencyMs = model.latencyMs,
            appPackageName = model.appPackageName,
            appName = model.appName
        )
    }
}
