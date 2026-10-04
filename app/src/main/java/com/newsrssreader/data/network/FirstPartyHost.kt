package com.newsrssreader.data.network

import java.net.URI

/**
 * True for `lenta.ru` and anything under it (`icdn.lenta.ru`, `www.lenta.ru`), and nothing else.
 * The dot in the suffix is what keeps `evil-lenta.ru` out.
 *
 * One rule for two places: the feed parser drops items that link elsewhere, and the reader web
 * view refuses every request to a host that fails it, so the app's claim of using the network
 * only for Lenta.ru holds for both.
 */
internal fun isFirstPartyHost(host: String?): Boolean {
    val h = host?.lowercase() ?: return false
    return h == "lenta.ru" || h.endsWith(".lenta.ru")
}

/** [isFirstPartyHost] for a whole URL; a string that isn't one has no host and so fails. */
internal fun isFirstPartyUrl(url: String): Boolean =
    isFirstPartyHost(runCatching { URI(url).host }.getOrNull())
