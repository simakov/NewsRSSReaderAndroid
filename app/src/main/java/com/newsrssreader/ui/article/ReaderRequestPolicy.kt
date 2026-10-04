package com.newsrssreader.ui.article

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.newsrssreader.data.network.isFirstPartyHost
import java.io.ByteArrayInputStream

/**
 * The reader's [WebViewClient]. The reader runs the site's own scripts, and a live article page
 * also pulls in Yandex Metrica/Webvisor, TNS, Rambler and Top.Mail.Ru counters; everything outside
 * [isFirstPartyHost] is refused. It blocks third-party requests, keeps first-party navigation inside
 * the reader, and sends every other link to the system browser.
 */
internal open class ReaderWebViewClient(private val context: Context) : WebViewClient() {

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        if (isFirstPartyHost(request.url.host)) return null
        return WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val uri = request.url
        if (uri.scheme == "https" && isFirstPartyHost(uri.host)) return false
        openExternally(uri)
        return true
    }

    private fun openExternally(uri: Uri) {
        // Only web links leave the app; `intent:`, `mailto:` and the like are dropped, not launched.
        if (uri.scheme != "https" && uri.scheme != "http") return
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
        }
    }
}
