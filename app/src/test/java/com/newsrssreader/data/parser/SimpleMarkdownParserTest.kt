package com.newsrssreader.data.parser

import com.newsrssreader.data.parser.SimpleMarkdownParser.Block
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SimpleMarkdownParserTest {

    // A verbatim excerpt of the v1.4.0 release notes — the exact shape this parser exists to
    // render, so a regression here would be a regression in the real "Что нового" popup.
    private val realNotes = """
        ## Что нового

        ### Исправлено
        - Приложение больше не вылетает при пролистывании ленты «Все».
        - В просмотре фото изображение теперь двигается ровно за пальцем.

        ### Улучшено
        - **Приложение похудело в 14 раз** — файл уменьшился с 18 МБ до 1.2 МБ.

        **Full Changelog**: https://github.com/simakov/NewsRSSReaderAndroid/compare/v1.3.0...v1.4.0
    """.trimIndent()

    @Test
    fun `parses the real release notes into headings, bullets and a paragraph`() {
        val blocks = SimpleMarkdownParser.parse(realNotes)

        assertEquals(
            listOf(
                "Heading(2)", "Heading(3)", "Bullet", "Bullet",
                "Heading(3)", "Bullet", "Paragraph",
            ),
            blocks.map {
                when (it) {
                    is Block.Heading -> "Heading(${it.level})"
                    is Block.Bullet -> "Bullet"
                    is Block.Paragraph -> "Paragraph"
                }
            },
        )
        assertEquals("Что нового", SimpleMarkdownParser.plainText(listOf(blocks[0])))
    }

    @Test
    fun `bold runs inside a bullet become their own spans`() {
        val bullet = SimpleMarkdownParser.parse("- **Жирное начало** и обычный хвост")
            .single() as Block.Bullet

        assertEquals(listOf(true, false), bullet.spans.map { it.bold })
        assertEquals("Жирное начало", bullet.spans[0].text)
        assertEquals(" и обычный хвост", bullet.spans[1].text)
    }

    @Test
    fun `consecutive lines join into one paragraph and a blank line starts a new one`() {
        val blocks = SimpleMarkdownParser.parse("first line\nsecond line\n\nnext block")

        assertEquals(2, blocks.size)
        assertEquals("first line second line", SimpleMarkdownParser.plainText(listOf(blocks[0])))
        assertEquals("next block", SimpleMarkdownParser.plainText(listOf(blocks[1])))
    }

    @Test
    fun `italic, code and links are recognised`() {
        val spans = SimpleMarkdownParser.parseInline("a *i* `c` [label](https://x.dev) b")

        assertTrue(spans.single { it.italic }.text == "i")
        assertTrue(spans.single { it.code }.text == "c")
        val link = spans.single { it.link != null }
        assertEquals("label", link.text)
        assertEquals("https://x.dev", link.link)
    }

    @Test
    fun `underscores inside a word are literal, not emphasis`() {
        val spans = SimpleMarkdownParser.parseInline("build_apk_sh stays intact")

        assertEquals("build_apk_sh stays intact", spans.joinToString("") { it.text })
        assertNull(spans.firstOrNull { it.italic })
    }

    @Test
    fun `unterminated markers are kept as literal text`() {
        val spans = SimpleMarkdownParser.parseInline("2 * 3 is not **emphasis")

        assertEquals("2 * 3 is not **emphasis", spans.joinToString("") { it.text })
    }

    @Test
    fun `plainText strips every marker`() {
        val plain = SimpleMarkdownParser.plainText(
            SimpleMarkdownParser.parse("- **bold** and `code`\n- plain"),
        )

        assertEquals("bold and code plain", plain)
    }

    @Test
    fun `blank notes produce no blocks`() {
        assertEquals(emptyList<Block>(), SimpleMarkdownParser.parse("   \n\n  "))
    }
}
