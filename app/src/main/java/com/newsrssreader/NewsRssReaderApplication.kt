package com.newsrssreader

import android.app.Application
import com.newsrssreader.data.store.BookmarkStore
import com.newsrssreader.data.store.ReadStateStore
import io.appmetrica.analytics.AppMetrica
import io.appmetrica.analytics.AppMetricaConfig

class NewsRssReaderApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Both stores are process-wide and have to be reachable from composables, so they are
        // bound to the application context once, here. Neither blocks startup: ReadStateStore
        // parses one small preferences string and defers its clean-up write to a background-
        // priority thread, and BookmarkStore reads its index asynchronously and publishes it as
        // state the screens are already collecting.
        ReadStateStore.init(this)
        BookmarkStore.init(this)

        // BUILD_CONFIG.APPMETRICA_API_KEY is empty on a fresh clone or CI runner (the key is a
        // per-developer secret kept out of the repo — see local.properties). Skip activation
        // rather than crash, matching how the release signing config degrades to unsigned.
        if (BuildConfig.APPMETRICA_API_KEY.isNotEmpty()) {
            val config = AppMetricaConfig.newConfigBuilder(BuildConfig.APPMETRICA_API_KEY).build()
            AppMetrica.activate(this, config)
        }
    }
}
