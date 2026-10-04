package com.newsrssreader.ui.article

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderRequestPolicyTest {

    @Test
    fun `lenta ru and its subdomains are first party`() {
        assertTrue(isFirstPartyHost("lenta.ru"))
        assertTrue(isFirstPartyHost("www.lenta.ru"))
        assertTrue(isFirstPartyHost("icdn.lenta.ru"))
    }

    @Test
    fun `host comparison ignores case`() {
        assertTrue(isFirstPartyHost("ICDN.Lenta.RU"))
    }

    @Test
    fun `the trackers found on a live article page are third party`() {
        listOf(
            "mc.yandex.ru", "mc.webvisor.org", "yandex.ru", "tns-counter.ru",
            "counter.rambler.ru", "ssp.rambler.ru", "top-fwz1.mail.ru", "img.championat.com",
        ).forEach { assertFalse(it, isFirstPartyHost(it)) }
    }

    @Test
    fun `lookalike hosts are third party`() {
        assertFalse(isFirstPartyHost("evil-lenta.ru"))
        assertFalse(isFirstPartyHost("lenta.ru.evil.com"))
        assertFalse(isFirstPartyHost("lenta.news"))
        assertFalse(isFirstPartyHost("notlenta.ru"))
    }

    @Test
    fun `missing or empty host is third party`() {
        assertFalse(isFirstPartyHost(null))
        assertFalse(isFirstPartyHost(""))
    }
}
