package com.newsrssreader.data.network

import org.junit.Assert.assertEquals
import org.junit.Test

class LentaFeedServiceTest {
    @Test
    fun `categories map matches iOS source count and keys`() {
        // Verified directly against LentaFeedService.swift's `categories` dictionary
        // (russia/world/ussr/economics/forces/science/culture/sport/media/style/travel/
        // life/realty/wellness/pobeda80): it has 15 entries, matching CLAUDE.md's "15
        // predefined categories". The implementation plan's test comment claimed this was
        // corrected to 14, but the plan's own categories map (copied verbatim from the iOS
        // source) lists all 15 keys above, and a grep count of the Swift dictionary confirms
        // 15. Asserting 14 here would be factually wrong and would require deleting a real,
        // functioning category (e.g. "pobeda80" or "wellness") to force the map to match —
        // that would be a product regression, not a fix. Keeping 15.
        assertEquals(15, LentaFeedService.categories.size)
        assertEquals("Россия", LentaFeedService.categories["russia"])
        assertEquals("Победа", LentaFeedService.categories["pobeda80"])
    }
}
