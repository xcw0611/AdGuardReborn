package com.adblocker.core.filter

/**
 * Inverted Domain Trie for high-performance sub-millisecond domain rule matching.
 *
 * Suffix-oriented matching: "ad.doubleclick.net" is stored/traversed as:
 * ROOT -> "net" -> "doubleclick" -> "ad"
 *
 * This allows O(L) matching for both exact and wildcard subdomain rules.
 */
class DomainTrie {

    private class Node {
        val children = HashMap<String, Node>()
        var isWildcardBlock = false
        var isExactBlock = false
        var isWhitelist = false
        var matchedRule: String? = null
    }

    private val root = Node()
    var ruleCount: Int = 0
        private set

    fun clear() {
        root.children.clear()
        ruleCount = 0
    }

    /**
     * Add a block or whitelist rule.
     * @param domain Domain string (e.g., "doubleclick.net")
     * @param isWildcard If true, blocks domain and ALL subdomains (e.g., "||example.com^")
     * @param isWhitelist If true, overrides any block rule (e.g., "@@||example.com^")
     * @param rawRule The original rule string
     */
    fun addRule(domain: String, isWildcard: Boolean, isWhitelist: Boolean, rawRule: String) {
        val cleanDomain = domain.trim().trimEnd('.').lowercase()
        if (cleanDomain.isEmpty()) return

        val labels = cleanDomain.split('.').reversed()
        var current = root

        for (label in labels) {
            current = current.children.computeIfAbsent(label) { Node() }
        }

        if (isWhitelist) {
            current.isWhitelist = true
            current.matchedRule = rawRule
        } else if (isWildcard) {
            current.isWildcardBlock = true
            current.matchedRule = rawRule
        } else {
            current.isExactBlock = true
            current.matchedRule = rawRule
        }
        ruleCount++
    }

    /**
     * Matches a query domain against loaded rules.
     * Whitelist rules have absolute priority over block rules.
     */
    fun match(domain: String): FilterResult {
        val cleanDomain = domain.trim().trimEnd('.').lowercase()
        if (cleanDomain.isEmpty()) return FilterResult.Passed

        val labels = cleanDomain.split('.').reversed()
        var current = root
        var lastWildcardBlock: Node? = null
        var lastExactMatch: Node? = null
        var lastWhitelist: Node? = null

        for ((index, label) in labels.withIndex()) {
            val child = current.children[label] ?: break
            current = child

            if (current.isWhitelist) {
                lastWhitelist = current
            }
            if (current.isWildcardBlock) {
                lastWildcardBlock = current
            }
            if (index == labels.lastIndex && current.isExactBlock) {
                lastExactMatch = current
            }
        }

        // 1. Whitelist always takes priority!
        if (lastWhitelist != null) {
            return FilterResult.Whitelisted(lastWhitelist.matchedRule ?: "Whitelist")
        }

        // 2. Exact match
        if (lastExactMatch != null) {
            return FilterResult.Blocked(lastExactMatch.matchedRule ?: "Exact Block")
        }

        // 3. Wildcard block match
        if (lastWildcardBlock != null) {
            return FilterResult.Blocked(lastWildcardBlock.matchedRule ?: "Wildcard Block")
        }

        return FilterResult.Passed
    }
}
