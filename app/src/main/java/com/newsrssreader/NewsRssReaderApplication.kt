package com.newsrssreader

import android.app.Application
import com.newsrssreader.data.store.BookmarkStore
import com.newsrssreader.data.store.ReadStateStore

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
    }
}
