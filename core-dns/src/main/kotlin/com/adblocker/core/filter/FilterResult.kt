package com.adblocker.core.filter

sealed class FilterResult {
    data class Blocked(val matchedRule: String, val reason: String = "Ad/Tracker Blocklist") : FilterResult()
    data class Whitelisted(val matchedRule: String) : FilterResult()
    object Passed : FilterResult()

    val isBlocked: Boolean get() = this is Blocked
    val isWhitelisted: Boolean get() = this is Whitelisted
}
