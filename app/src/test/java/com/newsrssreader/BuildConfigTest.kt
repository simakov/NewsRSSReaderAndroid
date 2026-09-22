package com.newsrssreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the version literals in build.gradle.kts. They are hand-bumped (by the `release` skill)
 * rather than computed, because F-Droid finds an app's version by regex-scanning that file at
 * each tag and cannot run Gradle code — so the cost of the literals is that nothing stops the
 * two from drifting apart, or from drifting away from the vMAJOR.MINOR.PATCH tag format the
 * updater compares against. These tests are that stop.
 */
class BuildConfigTest {
    @Test
    fun `VERSION_NAME matches the MAJOR_MINOR_PATCH format`() {
        assertTrue(BuildConfig.VERSION_NAME.matches(Regex("""\d+\.\d+\.\d+""")))
    }

    @Test
    fun `VERSION_CODE encodes VERSION_NAME`() {
        val (major, minor, patch) = BuildConfig.VERSION_NAME.split(".").map { it.toInt() }
        assertEquals(major * 10000 + minor * 100 + patch, BuildConfig.VERSION_CODE)
    }
}
