package com.adblocker.core.filter

object RuleParser {

    data class ParsedRule(
        val domain: String,
        val isWildcard: Boolean,
        val isWhitelist: Boolean,
        val rawRule: String
    )

    fun parseLine(line: String): ParsedRule? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null

        // Comment lines
        if (trimmed.startsWith('#') || trimmed.startsWith('!') || trimmed.startsWith('[')) {
            return null
        }

        // Ignore cosmetic/HTML injection rules (these require HTTPS MITM)
        if (trimmed.contains("##") || trimmed.contains("#?#") || trimmed.contains("###") || trimmed.contains("#%#")) {
            return null
        }

        // 1. Check for Adblock Whitelist (@@||domain^ or @@||domain)
        if (trimmed.startsWith("@@||")) {
            var domain = trimmed.substring(4)
            val caretIdx = domain.indexOf('^')
            if (caretIdx != -1) domain = domain.substring(0, caretIdx)
            val slashIdx = domain.indexOf('/')
            if (slashIdx != -1) domain = domain.substring(0, slashIdx)
            val pipeIdx = domain.indexOf('$')
            if (pipeIdx != -1) domain = domain.substring(0, pipeIdx)
            domain = cleanDomain(domain)
            return if (domain.isNotEmpty()) ParsedRule(domain, isWildcard = true, isWhitelist = true, rawRule = trimmed) else null
        }

        // 2. Check for Adblock Wildcard Block (||domain^ or ||domain)
        if (trimmed.startsWith("||")) {
            var domain = trimmed.substring(2)
            val caretIdx = domain.indexOf('^')
            if (caretIdx != -1) domain = domain.substring(0, caretIdx)
            val slashIdx = domain.indexOf('/')
            if (slashIdx != -1) domain = domain.substring(0, slashIdx)
            val pipeIdx = domain.indexOf('$')
            if (pipeIdx != -1) domain = domain.substring(0, pipeIdx)
            domain = cleanDomain(domain)
            return if (domain.isNotEmpty()) ParsedRule(domain, isWildcard = true, isWhitelist = false, rawRule = trimmed) else null
        }

        // 3. Check for standard /etc/hosts format: "0.0.0.0 example.com" or "127.0.0.1 example.com"
        val parts = trimmed.split(Regex("\\s+"))
        if (parts.size >= 2 && (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1" || parts[0] == "::1")) {
            val domain = cleanDomain(parts[1])
            if (domain.isNotEmpty() && domain != "localhost" && domain != "broadcasthost") {
                return ParsedRule(domain, isWildcard = false, isWhitelist = false, rawRule = trimmed)
            }
            return null
        }

        // 4. Wildcard prefix "*.domain.com"
        if (trimmed.startsWith("*.")) {
            val domain = cleanDomain(trimmed.substring(2))
            return if (domain.isNotEmpty()) ParsedRule(domain, isWildcard = true, isWhitelist = false, rawRule = trimmed) else null
        }

        // 5. Bare domain
        if (!trimmed.contains(' ') && !trimmed.contains('/') && trimmed.contains('.')) {
            val domain = cleanDomain(trimmed)
            return if (domain.isNotEmpty()) ParsedRule(domain, isWildcard = false, isWhitelist = false, rawRule = trimmed) else null
        }

        return null
    }

    private fun cleanDomain(raw: String): String {
        return raw.trim()
            .trimStart('.', '*')
            .trimEnd('.')
            .lowercase()
    }
}
