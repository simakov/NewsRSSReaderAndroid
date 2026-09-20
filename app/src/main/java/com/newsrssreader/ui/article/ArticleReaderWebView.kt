package com.newsrssreader.ui.article

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.newsrssreader.ui.theme.AppTheme

/**
 * The fallback renderer for articles the parser couldn't turn into content blocks: the page itself,
 * loaded in a [WebView] and stripped down to a reader view.
 *
 * "Reader mode" here is done in the page rather than by another parser pass: once the page has
 * finished loading, [READER_SCRIPT] pulls the article body (and the `<h1>`) out of the live DOM,
 * makes it the entire document, and applies a stylesheet built from the app's own design tokens.
 * Everything else - Lenta.ru's header, navigation, footer, banners, embedded players, the
 * related-material carousels - simply stops existing.
 *
 * Doing it against the *live* DOM is what makes this a useful fallback at all: the article body is
 * found by a chain of selectors, but if every one of them misses, the script leaves the page alone
 * and the user still gets the readable-but-plain site rather than a blank screen. That is the one
 * case where the original markup survives, so the stylesheet is only injected when extraction
 * actually succeeded.
 *
 * The view is kept invisible until the script has run, so the reader view is the first thing the
 * user sees instead of a flash of the full site.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ArticleReaderWebView(url: String, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val background = colors.blackInversed
    val script = remember(url, colors) {
        READER_SCRIPT.replace("__CSS__", readerCss(colors.blackInversed, colors.black, colors.mutedGray, colors.red))
    }

    // Set once the injection has run; until then the raw site is on screen and shouldn't be.
    var ready by remember(url) { mutableStateOf(false) }
    // What we asked the WebView to load, as opposed to `webView.url`, which becomes whatever the
    // site redirected to - comparing against that would reload the page on every recomposition.
    val requested = remember { arrayOfNulls<String>(1) }

    Box(modifier = modifier.background(background)) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    setBackgroundColor(AndroidColor.TRANSPARENT)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // The reader stylesheet lays the text out for the viewport width itself, so
                    // the page must not additionally be zoomed out to fit a desktop layout.
                    settings.loadWithOverviewMode = false
                    settings.useWideViewPort = false
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            // Re-runs after in-page navigation too (a link tapped inside the
                            // reader), so following a link stays in reader mode.
                            view.evaluateJavascript(script) { ready = true }
                        }
                    }
                }
            },
            update = { webView ->
                if (requested[0] != url) {
                    requested[0] = url
                    ready = false
                    webView.loadUrl(url)
                }
            },
            onRelease = { it.destroy() },
            modifier = Modifier.fillMaxSize(),
        )

        if (!ready) {
            // Plain paper rather than a spinner: the reader view drops into place the moment the
            // script runs, and a spinner for that would flash more than it would inform.
            Box(modifier = Modifier.fillMaxSize().background(background))
        }
    }
}

/** `#RRGGBB` for use in the injected CSS. */
private fun Color.toCssHex(): String = String.format("#%06X", toArgb() and 0xFFFFFF)

private fun readerCss(background: Color, text: Color, muted: Color, accent: Color): String {
    val bg = background.toCssHex()
    val fg = text.toCssHex()
    val mutedHex = muted.toCssHex()
    val accentHex = accent.toCssHex()
    return """
        html, body { background: $bg !important; color: $fg !important; margin: 0; padding: 0; }
        #nrr-reader { max-width: 720px; margin: 0 auto; padding: 8px 16px 48px;
            font-family: Roboto, system-ui, sans-serif; font-size: 17px; line-height: 1.55; color: $fg; }
        #nrr-reader h1 { font-size: 25px; line-height: 1.25; margin: 8px 0 16px; }
        #nrr-reader h2, #nrr-reader h3 { font-size: 19px; line-height: 1.3; margin: 24px 0 8px; }
        #nrr-reader p { margin: 0 0 16px; }
        #nrr-reader a { color: $accentHex; }
        #nrr-reader img, #nrr-reader video { display: block; max-width: 100%; height: auto;
            border-radius: 8px; margin: 0 0 8px; }
        #nrr-reader figure { margin: 0 0 16px; }
        #nrr-reader figcaption, #nrr-reader .picture__caption { font-size: 13px; color: $mutedHex; }
        #nrr-reader blockquote, #nrr-reader .box-quote { margin: 0 0 16px; padding-left: 12px;
            border-left: 3px solid $accentHex; }
        #nrr-reader iframe, #nrr-reader .box-inline-topic { display: none; }
    """.trimIndent()
}

/**
 * Extracts the article body into `#nrr-reader` and styles it. `__CSS__` is substituted before
 * evaluation. Written as ES5 so it runs on the older WebView builds still shipping on API 29-era
 * devices.
 */
private const val READER_SCRIPT = """
(function () {
  var selectors = ['.topic-body__content', '.topic-body', '[itemprop="articleBody"]',
                   '.content-body', 'article'];
  var body = null;
  for (var i = 0; i < selectors.length && !body; i++) {
    body = document.querySelector(selectors[i]);
  }
  if (!body) { return 'no-body'; }

  var heading = document.querySelector('h1');
  var titleHtml = heading ? '<h1>' + heading.innerHTML + '</h1>' : '';
  var reader = document.createElement('div');
  reader.id = 'nrr-reader';
  reader.innerHTML = titleHtml + body.innerHTML;
  document.body.innerHTML = '';
  document.body.appendChild(reader);

  var junk = reader.querySelectorAll(
    'script, noscript, iframe, form, button, [class*="banner"], [class*="adv"], [id*="adfox"]');
  for (var j = 0; j < junk.length; j++) {
    if (junk[j].parentNode) { junk[j].parentNode.removeChild(junk[j]); }
  }

  var style = document.createElement('style');
  style.textContent = `__CSS__`;
  document.head.appendChild(style);
  return 'ok';
})();
"""
