package com.newsrssreader.data.parser

/**
 * Lightweight, hand-rolled HTML DOM + tokenizer + tiny CSS-selector engine.
 *
 * Ported from the iOS `SimpleHTMLParser.swift` (NewsRSSReaderShared), which itself replaced
 * SwiftSoup to keep the app's dependency footprint minimal. This is intentionally NOT a
 * general-purpose/standards-compliant HTML parser: it supports exactly the subset of markup
 * and CSS selectors that Lenta.ru's article pages use.
 */

/** A node in the DOM tree: either a text node or an element. */
sealed class HtmlNode

class HtmlTextNode(val text: String) : HtmlNode()

class HtmlElement(
    val tag: String,
    val attributes: Map<String, String> = emptyMap(),
) : HtmlNode() {
    val childNodes: MutableList<HtmlNode> = mutableListOf()
    var parent: HtmlElement? = null

    fun attr(name: String): String = attributes[name.lowercase()] ?: ""

    fun hasClass(cls: String): Boolean =
        attr("class").split(" ").contains(cls)

    /** Direct element children only (not recursive). */
    fun children(): List<HtmlElement> = childNodes.filterIsInstance<HtmlElement>()

    /**
     * Recursively collects all descendant text-node contents (trimmed, non-empty only),
     * joins with a single space, collapses runs of 2+ spaces to one, and trims the result.
     */
    fun text(): String {
        val parts = mutableListOf<String>()
        collectText(this, parts)
        val joined = parts.joinToString(" ")
        return joined.replace(Regex("  +"), " ").trim()
    }

    private fun collectText(element: HtmlElement, out: MutableList<String>) {
        for (node in element.childNodes) {
            when (node) {
                is HtmlTextNode -> {
                    val trimmed = node.text.trim()
                    if (trimmed.isNotEmpty()) out.add(trimmed)
                }
                is HtmlElement -> collectText(node, out)
            }
        }
    }

    /** Reconstructs inner HTML — used only so `<script>` contents can be read back as raw text. */
    fun html(): String {
        val sb = StringBuilder()
        for (node in childNodes) {
            when (node) {
                is HtmlTextNode -> sb.append(node.text)
                is HtmlElement -> sb.append(node.outerHtml())
            }
        }
        return sb.toString()
    }

    private fun outerHtml(): String {
        val attrsStr = attributes.entries.joinToString(" ") { (k, v) -> "$k=\"$v\"" }
        val openTag = if (attrsStr.isEmpty()) tag else "$tag $attrsStr"
        return if (tag in VOID_TAGS && childNodes.isEmpty()) {
            "<$openTag/>"
        } else {
            "<$openTag>${html()}</$tag>"
        }
    }

    /** CSS-selector-equivalent query: recursive descendant search, document order. */
    fun select(css: String): List<HtmlElement> {
        val selectors = parseSelectorGroup(css)
        val results = mutableListOf<HtmlElement>()
        selectDescendants(this, selectors, results)
        return results
    }

    private fun selectDescendants(
        element: HtmlElement,
        selectors: List<SimpleSelector>,
        out: MutableList<HtmlElement>,
    ) {
        for (child in element.children()) {
            if (selectors.any { it.matches(child) }) {
                out.add(child)
            }
            // Always recurse regardless of match, so nested matches are all found.
            selectDescendants(child, selectors, out)
        }
    }

    companion object {
        val VOID_TAGS = setOf(
            "area", "base", "br", "col", "embed", "hr", "img", "input",
            "link", "meta", "param", "source", "track", "wbr",
        )
    }
}

/** Wraps a synthetic root element; select() delegates to it. */
class HtmlDocument(private val root: HtmlElement) {
    fun select(css: String): List<HtmlElement> = root.select(css)
}

/** A single simple selector: optional tag, optional class, optional attribute condition. */
private class SimpleSelector(
    val tag: String?,
    val className: String?,
    val attrName: String?,
    val attrValue: String?,
    val attrPrefixMatch: Boolean,
) {
    fun matches(element: HtmlElement): Boolean {
        if (tag != null && tag.isNotEmpty() && element.tag != tag) return false
        if (className != null && !element.hasClass(className)) return false
        if (attrName != null) {
            val actual = element.attr(attrName)
            if (attrValue != null) {
                if (attrPrefixMatch) {
                    if (!actual.startsWith(attrValue)) return false
                } else {
                    if (actual != attrValue) return false
                }
            } else {
                // presence-only check
                if (actual.isEmpty()) return false
            }
        }
        return true
    }
}

private fun parseSelectorGroup(css: String): List<SimpleSelector> =
    css.split(",").map { parseSimpleSelector(it.trim()) }

private fun parseSimpleSelector(raw: String): SimpleSelector {
    val bracketStart = raw.indexOf('[')
    val bracketEnd = raw.lastIndexOf(']')

    val tagClassPart: String
    var attrName: String? = null
    var attrValue: String? = null
    var attrPrefixMatch = false

    if (bracketStart >= 0 && bracketEnd > bracketStart) {
        tagClassPart = raw.substring(0, bracketStart)
        val inside = raw.substring(bracketStart + 1, bracketEnd)
        when {
            inside.contains("^=") -> {
                val parts = inside.split("^=", limit = 2)
                attrName = parts[0].trim()
                attrValue = parts[1].trim().trim('"', '\'')
                attrPrefixMatch = true
            }
            inside.contains("=") -> {
                val parts = inside.split("=", limit = 2)
                attrName = parts[0].trim()
                attrValue = parts[1].trim().trim('"', '\'')
            }
            else -> {
                attrName = inside.trim()
                attrValue = null
            }
        }
    } else {
        tagClassPart = raw
    }

    var tag: String? = null
    var className: String? = null
    val dotIdx = tagClassPart.indexOf('.')
    if (dotIdx >= 0) {
        val tagPart = tagClassPart.substring(0, dotIdx).trim()
        tag = if (tagPart.isEmpty()) null else tagPart
        className = tagClassPart.substring(dotIdx + 1).trim()
    } else {
        val trimmed = tagClassPart.trim()
        tag = if (trimmed.isEmpty()) null else trimmed
    }

    return SimpleSelector(
        tag = tag,
        className = className,
        attrName = attrName,
        attrValue = attrValue,
        attrPrefixMatch = attrPrefixMatch,
    )
}

object SimpleHtmlParser {
    private val VOID_TAGS = HtmlElement.VOID_TAGS
    private val RAW_TEXT_TAGS = setOf("script", "style")

    fun parse(html: String): HtmlDocument {
        val root = HtmlElement("html")
        val stack = mutableListOf(root)
        val chars = html
        val len = chars.length
        var i = 0

        while (i < len) {
            val c = chars[i]
            if (c == '<') {
                when {
                    chars.startsWith("<!--", i) -> {
                        val end = chars.indexOf("-->", i + 4)
                        i = if (end >= 0) end + 3 else len
                    }
                    chars.startsWith("<!", i) -> {
                        val end = chars.indexOf('>', i)
                        i = if (end >= 0) end + 1 else len
                    }
                    chars.startsWith("</", i) -> {
                        var j = i + 2
                        val nameStart = j
                        while (j < len && !chars[j].isWhitespace() && chars[j] != '>') j++
                        val name = chars.substring(nameStart, j).lowercase()
                        // skip past '>'
                        while (j < len && chars[j] != '>') j++
                        if (j < len) j++
                        closeTag(stack, name)
                        i = j
                    }
                    else -> {
                        // Open tag
                        var j = i + 1
                        val nameStart = j
                        while (j < len && !chars[j].isWhitespace() && chars[j] != '>' && chars[j] != '/') j++
                        val tagName = chars.substring(nameStart, j).lowercase()

                        val attributes = mutableMapOf<String, String>()
                        var selfClosing = false

                        loop@ while (j < len) {
                            // skip whitespace
                            while (j < len && chars[j].isWhitespace()) j++
                            if (j >= len) break@loop
                            when {
                                chars[j] == '>' -> {
                                    j++
                                    break@loop
                                }
                                chars[j] == '/' -> {
                                    selfClosing = true
                                    j++
                                }
                                else -> {
                                    val attrNameStart = j
                                    while (j < len && chars[j] != '=' && chars[j] != '>' &&
                                        chars[j] != '/' && !chars[j].isWhitespace()
                                    ) j++
                                    val attrName = chars.substring(attrNameStart, j)
                                    // skip whitespace before possible '='
                                    var k = j
                                    while (k < len && chars[k].isWhitespace()) k++
                                    if (k < len && chars[k] == '=') {
                                        k++
                                        while (k < len && chars[k].isWhitespace()) k++
                                        var value: String
                                        if (k < len && (chars[k] == '"' || chars[k] == '\'')) {
                                            val quote = chars[k]
                                            k++
                                            val valStart = k
                                            while (k < len && chars[k] != quote) k++
                                            value = chars.substring(valStart, k)
                                            if (k < len) k++ // skip closing quote
                                        } else {
                                            val valStart = k
                                            while (k < len && !chars[k].isWhitespace() && chars[k] != '>' && chars[k] != '/') k++
                                            value = chars.substring(valStart, k)
                                        }
                                        if (attrName.isNotEmpty()) {
                                            attributes[attrName.lowercase()] = decodeEntities(value)
                                        }
                                        j = k
                                    } else {
                                        if (attrName.isNotEmpty()) {
                                            attributes[attrName.lowercase()] = ""
                                        }
                                        j = k
                                    }
                                }
                            }
                        }

                        val element = HtmlElement(tagName, attributes)
                        val top = stack.last()
                        element.parent = top
                        top.childNodes.add(element)

                        if (!selfClosing && tagName !in VOID_TAGS) {
                            if (tagName in RAW_TEXT_TAGS) {
                                // Slurp raw text until closing tag, no entity decoding.
                                val closeMarker = "</$tagName"
                                val idx = indexOfIgnoreCase(chars, closeMarker, j)
                                val rawEnd = if (idx >= 0) idx else len
                                val rawText = chars.substring(j, rawEnd)
                                if (rawText.isNotEmpty()) {
                                    element.childNodes.add(HtmlTextNode(rawText))
                                }
                                var k = if (idx >= 0) idx else len
                                // advance past the closing tag itself
                                val gt = chars.indexOf('>', k)
                                k = if (gt >= 0) gt + 1 else len
                                j = k
                                // element already appended; don't push onto stack (immediately "popped")
                            } else {
                                stack.add(element)
                            }
                        }
                        i = j
                    }
                }
            } else {
                val textStart = i
                while (i < len && chars[i] != '<') i++
                val rawText = chars.substring(textStart, i)
                val decoded = decodeEntities(rawText)
                if (decoded.trim().isNotEmpty()) {
                    stack.last().childNodes.add(HtmlTextNode(decoded))
                }
            }
        }

        return HtmlDocument(root)
    }

    private fun closeTag(stack: MutableList<HtmlElement>, name: String) {
        for (idx in stack.size - 1 downTo 1) {
            if (stack[idx].tag == name) {
                while (stack.size > idx) stack.removeAt(stack.size - 1)
                return
            }
        }
        // no match found anywhere: silent no-op
    }

    private fun indexOfIgnoreCase(haystack: String, needle: String, from: Int): Int {
        val lowerNeedle = needle.lowercase()
        val lowerHaystack = haystack.lowercase()
        return lowerHaystack.indexOf(lowerNeedle, from)
    }

    private val NAMED_ENTITIES = mapOf(
        "amp" to "&",
        "lt" to "<",
        "gt" to ">",
        "quot" to "\"",
        "apos" to "'",
        "nbsp" to " ",
    )

    fun decodeEntities(input: String): String {
        val sb = StringBuilder()
        var i = 0
        val len = input.length
        while (i < len) {
            val c = input[i]
            if (c == '&') {
                val semi = input.indexOf(';', i + 1)
                if (semi in (i + 1)..(i + 11)) {
                    val body = input.substring(i + 1, semi)
                    val resolved = resolveEntity(body)
                    if (resolved != null) {
                        sb.append(resolved)
                        i = semi + 1
                        continue
                    }
                }
                sb.append('&')
                i++
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    private fun resolveEntity(body: String): String? {
        NAMED_ENTITIES[body]?.let { return it }
        if (body.startsWith("#x") || body.startsWith("#X")) {
            val hex = body.substring(2)
            val code = hex.toIntOrNull(16) ?: return null
            return runCatching { String(Character.toChars(code)) }.getOrNull()
        }
        if (body.startsWith("#")) {
            val dec = body.substring(1)
            val code = dec.toIntOrNull() ?: return null
            return runCatching { String(Character.toChars(code)) }.getOrNull()
        }
        return null
    }
}
