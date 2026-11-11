package com.adblocker.core.filter

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.StringReader
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Thread-safe filter list manager and rule repository.
 */
class FilterListManager {

    private val trie = DomainTrie()
    private val customBlacklist = CopyOnWriteArrayList<String>()
    private val customWhitelist = CopyOnWriteArrayList<String>()

    val rulesCount: Int
        get() = trie.ruleCount

    @Synchronized
    fun loadRules(reader: BufferedReader) {
        reader.useLines { lines ->
            lines.forEach { line ->
                val parsed = RuleParser.parseLine(line)
                if (parsed != null) {
                    trie.addRule(
                        domain = parsed.domain,
                        isWildcard = parsed.isWildcard,
                        isWhitelist = parsed.isWhitelist,
                        rawRule = parsed.rawRule
                    )
                }
            }
        }
    }

    fun loadRulesFromString(rulesContent: String) {
        BufferedReader(StringReader(rulesContent)).use { loadRules(it) }
    }

    fun loadRulesFromStream(inputStream: InputStream) {
        BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { loadRules(it) }
    }

    @Synchronized
    fun addCustomBlockDomain(domain: String) {
        val clean = domain.trim().lowercase()
        if (clean.isNotEmpty() && !customBlacklist.contains(clean)) {
            customBlacklist.add(clean)
            trie.addRule(clean, isWildcard = true, isWhitelist = false, rawRule = "User: ||$clean^")
        }
    }

    @Synchronized
    fun addCustomWhitelistDomain(domain: String) {
        val clean = domain.trim().lowercase()
        if (clean.isNotEmpty() && !customWhitelist.contains(clean)) {
            customWhitelist.add(clean)
            trie.addRule(clean, isWildcard = true, isWhitelist = true, rawRule = "User: @@||$clean^")
        }
    }

    fun getCustomBlacklist(): List<String> = customBlacklist
    fun getCustomWhitelist(): List<String> = customWhitelist

    fun checkDomain(domain: String): FilterResult {
        return trie.match(domain)
    }

    @Synchronized
    fun clear() {
        trie.clear()
        customBlacklist.clear()
        customWhitelist.clear()
    }
}
