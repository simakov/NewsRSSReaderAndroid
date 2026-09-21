package com.newsrssreader

import android.app.Application
import io.appmetrica.analytics.AppMetrica
import io.appmetrica.analytics.AppMetricaConfig

class NewsRssReaderApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // BUILD_CONFIG.APPMETRICA_API_KEY is empty on a fresh clone or CI runner (the key is a
        // per-developer secret kept out of the repo — see local.properties). Skip activation
        // rather than crash, matching how the release signing config degrades to unsigned.
        if (BuildConfig.APPMETRICA_API_KEY.isNotEmpty()) {
            val config = AppMetricaConfig.newConfigBuilder(BuildConfig.APPMETRICA_API_KEY).build()
            AppMetrica.activate(this, config)
        }
    }
}
