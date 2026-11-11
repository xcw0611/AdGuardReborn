package com.adblocker.core

import com.adblocker.core.filter.RuleParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleParserTest {

    @Test
    fun testHostsFormat() {
        val rule1 = RuleParser.parseLine("0.0.0.0 ad.doubleclick.net")
        assertNotNull(rule1)
        assertEquals("ad.doubleclick.net", rule1!!.domain)
        assertEquals(false, rule1.isWildcard)
        assertEquals(false, rule1.isWhitelist)

        val rule2 = RuleParser.parseLine("127.0.0.1   tracker.example.com")
        assertNotNull(rule2)
        assertEquals("tracker.example.com", rule2!!.domain)

        // Ignore localhost
        val localhost = RuleParser.parseLine("127.0.0.1 localhost")
        assertNull(localhost)
    }

    @Test
    fun testAdblockFormat() {
        val rule1 = RuleParser.parseLine("||adservice.google.com^")
        assertNotNull(rule1)
        assertEquals("adservice.google.com", rule1!!.domain)
        assertTrue(rule1.isWildcard)
        assertEquals(false, rule1.isWhitelist)

        val rule2 = RuleParser.parseLine("@@||analytics.google.com^")
        assertNotNull(rule2)
        assertEquals("analytics.google.com", rule2!!.domain)
        assertTrue(rule2.isWildcard)
        assertTrue(rule2.isWhitelist)
    }

    @Test
    fun testIgnoreCommentsAndCosmetics() {
        assertNull(RuleParser.parseLine("# This is a comment"))
        assertNull(RuleParser.parseLine("! Another comment"))
        assertNull(RuleParser.parseLine("   "))
        assertNull(RuleParser.parseLine("example.com##.ad-banner"))
        assertNull(RuleParser.parseLine("example.com#?#div.sponsored"))
    }
}
