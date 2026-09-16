package com.newsrssreader

import org.junit.Assert.assertTrue
import org.junit.Test

class BuildConfigTest {
    @Test
    fun `GIT_TAG is never blank`() {
        assertTrue(BuildConfig.GIT_TAG.isNotBlank())
    }

    @Test
    fun `GIT_TAG matches the vMAJOR_MINOR_PATCH tag format`() {
        assertTrue(BuildConfig.GIT_TAG.matches(Regex("""v\d+\.\d+\.\d+""")))
    }
}
