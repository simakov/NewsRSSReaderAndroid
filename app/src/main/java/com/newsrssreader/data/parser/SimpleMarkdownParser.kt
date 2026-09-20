package com.newsrssreader.data.parser

/**
 * A deliberately tiny Markdown subset parser, used only for GitHub release notes shown in the
 * "Что нового" popup. It is hand-rolled for the same reason the RSS and article parsers are —
 * this project keeps production dependencies to OkHttp + Coil, and a full CommonMark
 * implementation would be orders of magnitude more code than the handful of constructs the
 * release notes actually use.
 *
 * Supported (verified against every release published so far, v1.0.0–v1.4.0):
 * - ATX headings `#`..`######`
 * - bullet items `- `, `* `, `+ `
 * - blank-line-separated paragraphs (consecutive lines are joined with a space, as in Markdown)
 * - inline `**bold**` / `__bold__`, `*italic*` / `_italic_`, `` `code` ``, `[text](url)`
 * - backslash escapes
 *
 * Everything else (tables, block quotes, images, nested lists, setext headings, ordered lists)
 * degrades to plain paragraph text rather than being rendered as literal markup noise.
 */
object SimpleMarkdownParser {

    data class Span(
        val text: String,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val code: Boolean = false,
        val link: String? = null,
    )

    sealed interface Block {
        val spans: List<Span>

        /** `level` is the number of leading `#` characters, clamped to 1..6. */
        data class Heading(val level: Int, override val spans: List<Span>) : Block
        data class Paragraph(override val spans: List<Span>) : Block
        data class Bullet(override val spans: List<Span>) : Block
    }

    private val headingRegex = Regex("^(#{1,6})\\s+(.*)$")
    private val bulletRegex = Regex("^\\s{0,3}[-*+]\\s+(.*)$")

    fun parse(markdown: String): List<Block> {
        val blocks = mutableListOf<Block>()
        val paragraph = mutableListOf<String>()

        fun flushParagraph() {
            if (paragraph.isNotEmpty()) {
                blocks += Block.Paragraph(parseInline(paragraph.joinToString(" ")))
                paragraph.clear()
            }
        }

        // Normalise CRLF so a Windows-authored release body doesn't leave stray \r in the text.
        markdown.replace("\r\n", "\n").replace('\r', '\n').split('\n').forEach { rawLine ->
            val line = rawLine.trim()
            val heading = headingRegex.find(line)
            val bullet = bulletRegex.find(rawLine.trimEnd())
            when {
                line.isEmpty() -> flushParagraph()
                heading != null -> {
                    flushParagraph()
                    // Trailing `#`s (`## Title ##`) are decoration in ATX headings, not content.
                    val text = heading.groupValues[2].trimEnd('#').trim()
                    blocks += Block.Heading(heading.groupValues[1].length, parseInline(text))
                }
                bullet != null -> {
                    flushParagraph()
                    blocks += Block.Bullet(parseInline(bullet.groupValues[1].trim()))
                }
                else -> paragraph += line
            }
        }
        flushParagraph()
        return blocks
    }

    /**
     * Flattens parsed blocks back to plain, marker-free text — used for the short teaser shown
     * in the update banner above the "Что нового" link. Headings are dropped by the caller, not
     * here, so this stays a general-purpose "strip the markup" helper.
     */
    fun plainText(blocks: List<Block>): String =
        blocks.joinToString(" ") { block -> block.spans.joinToString("") { it.text } }.trim()

    internal fun parseInline(text: String): List<Span> {
        val out = mutableListOf<Span>()
        parseInline(text, Span("", bold = false, italic = false), out)
        return out
    }

    private fun parseInline(text: String, style: Span, out: MutableList<Span>) {
        val buf = StringBuilder()

        fun flush() {
            if (buf.isNotEmpty()) {
                out += style.copy(text = buf.toString())
                buf.clear()
            }
        }

        var i = 0
        while (i < text.length) {
            val c = text[i]
            val delimiterAllowed = c != '_' || !isWordChar(text.getOrNull(i - 1))
            when {
                c == '\\' && i + 1 < text.length -> {
                    buf.append(text[i + 1])
                    i += 2
                }

                c == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end < 0) {
                        buf.append(c); i++
                    } else {
                        flush()
                        out += style.copy(text = text.substring(i + 1, end), code = true)
                        i = end + 1
                    }
                }

                (c == '*' || c == '_') && text.getOrNull(i + 1) == c && delimiterAllowed -> {
                    val end = findClosing(text, "$c$c", i + 2)
                    if (end < 0) {
                        buf.append(c); i++
                    } else {
                        flush()
                        parseInline(text.substring(i + 2, end), style.copy(bold = true), out)
                        i = end + 2
                    }
                }

                (c == '*' || c == '_') && delimiterAllowed -> {
                    val end = findClosing(text, c.toString(), i + 1)
                    if (end < 0) {
                        buf.append(c); i++
                    } else {
                        flush()
                        parseInline(text.substring(i + 1, end), style.copy(italic = true), out)
                        i = end + 1
                    }
                }

                c == '[' -> {
                    val close = text.indexOf(']', i + 1)
                    val paren = if (close >= 0 && text.getOrNull(close + 1) == '(') {
                        text.indexOf(')', close + 2)
                    } else {
                        -1
                    }
                    if (paren < 0) {
                        buf.append(c); i++
                    } else {
                        flush()
                        val label = text.substring(i + 1, close)
                        val url = text.substring(close + 2, paren).trim()
                        parseInline(label, style.copy(link = url), out)
                        i = paren + 1
                    }
                }

                else -> {
                    buf.append(c); i++
                }
            }
        }
        flush()
    }

    /**
     * Finds the closing `token` for an emphasis run that starts at [contentStart], applying
     * CommonMark's flanking rule in its simplified form: the run may not open on whitespace and
     * may not close on it. That is what keeps arithmetic like "2 * 3 * 4" and a lone dangling
     * asterisk from silently italicising half a sentence. Returns -1 when there is no valid
     * closer, in which case the caller keeps the marker as literal text.
     */
    private fun findClosing(text: String, token: String, contentStart: Int): Int {
        if (contentStart >= text.length || text[contentStart].isWhitespace()) return -1
        var from = contentStart + 1
        while (from <= text.length - token.length) {
            val end = text.indexOf(token, from)
            if (end < 0) return -1
            if (!text[end - 1].isWhitespace()) return end
            from = end + token.length
        }
        return -1
    }

    // `_` is only an emphasis marker at a word boundary, so identifiers like `build_apk` or
    // `snake_case_name` in release notes stay intact instead of turning into italics.
    private fun isWordChar(c: Char?): Boolean = c != null && (c.isLetterOrDigit() || c == '_')
}
