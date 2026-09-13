package com.newsrssreader.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Direct unit tests for [SimpleHtmlParser]'s tokenizer/DOM/entity/CSS-selector engine.
 *
 * This is the highest-risk hand-rolled code in the port (it must be resilient to malformed
 * HTML), so it gets its own coverage in addition to the indirect exercise it gets via
 * [LentaArticleParserTest]'s realistic article fixtures.
 */
class SimpleHtmlParserTest {

    // --- Malformed markup resilience ---------------------------------------------------------

    @Test
    fun `unterminated comment does not hang and parses to EOF gracefully`() {
        val html = "<div>a<!-- this comment is never closed"
        val doc = SimpleHtmlParser.parse(html)

        val div = doc.select("div").first()
        assertEquals("a", div.text())
    }

    @Test
    fun `unterminated tag near end of input does not crash`() {
        val html = "<div>text<"
        val doc = SimpleHtmlParser.parse(html)

        val div = doc.select("div").first()
        assertEquals("text", div.text())
    }

    @Test
    fun `bare less-than at very end of input does not crash`() {
        val html = "<p>hello</p><"
        val doc = SimpleHtmlParser.parse(html)

        val p = doc.select("p").first()
        assertEquals("hello", p.text())
    }

    // --- Attribute parsing ---------------------------------------------------------------------

    @Test
    fun `unquoted double and single quoted attribute values all parse correctly`() {
        val html = """<img a=unquoted.jpg b="double.jpg" c='single.jpg'>"""
        val doc = SimpleHtmlParser.parse(html)

        val img = doc.select("img").first()
        assertEquals("unquoted.jpg", img.attr("a"))
        assertEquals("double.jpg", img.attr("b"))
        assertEquals("single.jpg", img.attr("c"))
    }

    @Test
    fun `valueless bare attribute with no equals sign parses as empty value and does not swallow the next attribute`() {
        val html = """<input type=checkbox checked value="1" data-x='2'>"""
        val doc = SimpleHtmlParser.parse(html)

        val input = doc.select("input").first()
        assertEquals("checkbox", input.attr("type"))
        assertEquals("", input.attr("checked"))
        assertEquals("1", input.attr("value"))
        assertEquals("2", input.attr("data-x"))
    }

    // --- Self-closing tags -----------------------------------------------------------------

    @Test
    fun `self-closing non-void tag is not pushed onto the stack and accepts no children`() {
        val html = "<div/><p>after</p>"
        val doc = SimpleHtmlParser.parse(html)

        val div = doc.select("div").first()
        assertTrue(div.children().isEmpty())

        // "after" must belong to <p>, not have been swallowed as a child of the self-closed <div>.
        val p = doc.select("p").first()
        assertEquals("after", p.text())
        assertTrue(div.select("p").isEmpty())
    }

    // --- Close-tag matching ------------------------------------------------------------------

    @Test
    fun `mismatched overlapping close tag auto-closes everything above the matched tag`() {
        val html = "<div><span>a</div><p>after</p>"
        val doc = SimpleHtmlParser.parse(html)

        val div = doc.select("div").first()
        val span = div.children().first()
        assertEquals("span", span.tag)
        assertEquals("a", span.text())

        // <p> must be a sibling of <div> at the root, not nested inside the unclosed <span>.
        val p = doc.select("p").first()
        assertEquals("after", p.text())
        assertTrue(div.select("p").isEmpty())
    }

    @Test
    fun `close tag with no matching open anywhere is a silent no-op`() {
        val html = "<div>a</span>b</div>"
        val doc = SimpleHtmlParser.parse(html)

        val div = doc.select("div").first()
        // Both text nodes remain children of <div> since the stray </span> didn't pop anything.
        assertEquals("a b", div.text())
    }

    // --- Entity decoding -----------------------------------------------------------------------

    @Test
    fun `decodes all six named entities`() {
        assertEquals("&", SimpleHtmlParser.decodeEntities("&amp;"))
        assertEquals("<", SimpleHtmlParser.decodeEntities("&lt;"))
        assertEquals(">", SimpleHtmlParser.decodeEntities("&gt;"))
        assertEquals("\"", SimpleHtmlParser.decodeEntities("&quot;"))
        assertEquals("'", SimpleHtmlParser.decodeEntities("&apos;"))
        assertEquals(" ", SimpleHtmlParser.decodeEntities("&nbsp;"))
    }

    @Test
    fun `decodes decimal and hex numeric entities`() {
        assertEquals("©", SimpleHtmlParser.decodeEntities("&#169;"))
        assertEquals("©", SimpleHtmlParser.decodeEntities("&#x00A9;"))
    }

    @Test
    fun `unrecognized entity passes through literally unchanged`() {
        assertEquals("&foo;", SimpleHtmlParser.decodeEntities("&foo;"))
    }

    // --- script/style raw-text slurping --------------------------------------------------------

    @Test
    fun `script content is captured as raw text without entity decoding`() {
        val inner = "if (a < b) { var x = 1; } // &amp;"
        val html = "<script>$inner</script><p>after</p>"
        val doc = SimpleHtmlParser.parse(html)

        val script = doc.select("script").first()
        assertEquals(inner, script.html())

        // The "<" inside the script body must not have been parsed as a real tag, and parsing
        // must correctly resume after the script closes.
        val p = doc.select("p").first()
        assertEquals("after", p.text())
    }

    @Test
    fun `style content is captured as raw text without entity decoding`() {
        val inner = ".a > .b::before { content: \"&nbsp;\"; }"
        val html = "<style>$inner</style><p>after</p>"
        val doc = SimpleHtmlParser.parse(html)

        val style = doc.select("style").first()
        assertEquals(inner, style.html())

        val p = doc.select("p").first()
        assertEquals("after", p.text())
    }

    // --- select() selector forms ----------------------------------------------------------------

    @Test
    fun `select supports bare tag, class, tag-class, attr, attr-value, attr-prefix and comma union`() {
        val html = """
            <div class="a b">one</div>
            <span class="a">two</span>
            <p data-x="foo">three</p>
            <p data-x="bar">four</p>
        """.trimIndent()
        val doc = SimpleHtmlParser.parse(html)

        // bare tag
        assertEquals(2, doc.select("p").size)

        // .class
        assertEquals(2, doc.select(".a").size)

        // tag.class
        val spanA = doc.select("span.a")
        assertEquals(1, spanA.size)
        assertEquals("two", spanA.first().text())

        // [attr]
        assertEquals(2, doc.select("[data-x]").size)

        // [attr=value]
        val exact = doc.select("[data-x=foo]")
        assertEquals(1, exact.size)
        assertEquals("three", exact.first().text())

        // [attr^=value]
        val prefix = doc.select("[data-x^=fo]")
        assertEquals(1, prefix.size)
        assertEquals("three", prefix.first().text())

        // comma-separated union
        val union = doc.select("div, span")
        assertEquals(2, union.size)
    }

    // --- text() flattening ----------------------------------------------------------------------

    @Test
    fun `text flattens nested inline tags with single spaces and collapses multiple spaces`() {
        val html = "<p>Hello <b>bold</b>   world</p>"
        val doc = SimpleHtmlParser.parse(html)

        val p = doc.select("p").first()
        assertEquals("Hello bold world", p.text())
    }

    @Test
    fun `text joins across line breaks with a single space and no raw newlines`() {
        val html = "<p>Line1\n<span>Line2</span>\nLine3</p>"
        val doc = SimpleHtmlParser.parse(html)

        val p = doc.select("p").first()
        assertEquals("Line1 Line2 Line3", p.text())
        assertTrue(!p.text().contains("\n"))
    }
}
