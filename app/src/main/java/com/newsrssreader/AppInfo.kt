package com.newsrssreader

/**
 * Who this build is and where it came from. One place, because three things need to agree: the
 * "О программе" row in the drawer links to the repository, [com.newsrssreader.data.network
 * .UpdateCheckService] asks that same repository's GitHub API for the latest release, and
 * [com.newsrssreader.ui.update.UpdateViewModel] compares the tag it gets back against this build.
 */
object AppInfo {
    /** `owner/name` — the one spelling of the repository in the app. */
    const val REPOSITORY = "simakov/NewsRSSReaderAndroid"

    const val REPOSITORY_URL = "https://github.com/$REPOSITORY"

    /**
     * The release tag this build corresponds to, e.g. `"v1.6.0"`. `versionName` is a plain
     * literal in `build.gradle.kts` (F-Droid regex-scans it and cannot run Gradle code), bumped
     * by the `release` skill in the commit its tag points at, and Android convention leaves the
     * `v` off it — so the tag is that with the `v` put back.
     */
    val versionTag: String get() = "v${BuildConfig.VERSION_NAME}"
}
