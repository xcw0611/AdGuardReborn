package com.adblocker.core

import com.adblocker.core.filter.DomainTrie
import com.adblocker.core.filter.FilterResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DomainTrieTest {

    private lateinit var trie: DomainTrie

    @Before
    fun setup() {
        trie = DomainTrie()
    }

    @Test
    fun testExactDomainMatch() {
        trie.addRule("ad.example.com", isWildcard = false, isWhitelist = false, rawRule = "ad.example.com")

        val match1 = trie.match("ad.example.com")
        assertTrue(match1 is FilterResult.Blocked)
        assertEquals("ad.example.com", (match1 as FilterResult.Blocked).matchedRule)

        // Subdomain should not be matched when isWildcard = false
        val matchSub = trie.match("sub.ad.example.com")
        assertTrue(matchSub is FilterResult.Passed)

        // Parent domain should not be matched
        val matchParent = trie.match("example.com")
        assertTrue(matchParent is FilterResult.Passed)
    }

    @Test
    fun testWildcardSubdomainMatch() {
        // ||doubleclick.net^ blocks doubleclick.net and all its subdomains
        trie.addRule("doubleclick.net", isWildcard = true, isWhitelist = false, rawRule = "||doubleclick.net^")

        val matchBase = trie.match("doubleclick.net")
        assertTrue(matchBase is FilterResult.Blocked)

        val matchSub = trie.match("ad.doubleclick.net")
        assertTrue(matchSub is FilterResult.Blocked)

        val matchDeep = trie.match("pixel.tracking.ad.doubleclick.net")
        assertTrue(matchDeep is FilterResult.Blocked)

        // Different domain ending with same name should not match
        val matchOther = trie.match("mydoubleclick.net")
        assertTrue(matchOther is FilterResult.Passed)
    }

    @Test
    fun testWhitelistOverride() {
        // Block all bad.com
        trie.addRule("bad.com", isWildcard = true, isWhitelist = false, rawRule = "||bad.com^")
        // Whitelist ok.bad.com
        trie.addRule("ok.bad.com", isWildcard = true, isWhitelist = true, rawRule = "@@||ok.bad.com^")

        val matchBlocked = trie.match("ads.bad.com")
        assertTrue(matchBlocked is FilterResult.Blocked)

        val matchWhitelisted = trie.match("ok.bad.com")
        assertTrue(matchWhitelisted is FilterResult.Whitelisted)

        val matchWhitelistedSub = trie.match("sub.ok.bad.com")
        assertTrue(matchWhitelistedSub is FilterResult.Whitelisted)
    }

    @Test
    fun testCaseInsensitiveAndDots() {
        trie.addRule("Ads.Google.COM.", isWildcard = true, isWhitelist = false, rawRule = "||Ads.Google.COM.^")

        val match = trie.match("ADS.google.com")
        assertTrue(match is FilterResult.Blocked)

        val matchSub = trie.match("tracker.ads.google.com.")
        assertTrue(matchSub is FilterResult.Blocked)
    }
}
