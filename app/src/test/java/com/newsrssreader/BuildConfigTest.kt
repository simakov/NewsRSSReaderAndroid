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
    fun `AppInfo versionTag is the tag format the updater compares against`() {
        // UpdateViewModel decides whether a GitHub release is newer by comparing its tag_name to
        // this string, so a mismatch in shape (a missing "v", a stray suffix) silently means
        // "there is always an update" — the one failure mode nobody reports, because the app
        // offering an update looks intentional.
        assertEquals("v${BuildConfig.VERSION_NAME}", AppInfo.versionTag)
        assertTrue(AppInfo.versionTag.matches(Regex("""v\d+\.\d+\.\d+""")))
    }

    @Test
    fun `AppInfo REPOSITORY_URL points at the repository`() {
        assertEquals("https://github.com/${AppInfo.REPOSITORY}", AppInfo.REPOSITORY_URL)
    }

    @Test
    fun `VERSION_CODE encodes VERSION_NAME`() {
        val (major, minor, patch) = BuildConfig.VERSION_NAME.split(".").map { it.toInt() }
        assertEquals(major * 10000 + minor * 100 + patch, BuildConfig.VERSION_CODE)
    }
}
