package com.example.qqclaudecode

import org.junit.Assert.assertEquals
import org.junit.Test

class FileLinksTest {
    @Test
    fun linksNameWithLine() {
        assertEquals("see <a href=\"qqfile:Foo.kt#L42\">Foo.kt:42</a> now", FileLinks.linkify("see Foo.kt:42 now"))
    }

    @Test
    fun linksRelativePath() {
        assertEquals(
            "<p><a href=\"qqfile:path/to/Foo.kt\">path/to/Foo.kt</a></p>",
            FileLinks.linkify("<p>path/to/Foo.kt</p>"),
        )
    }

    @Test
    fun linksInsideInlineCode() {
        assertEquals(
            "<code><a href=\"qqfile:Foo.kt#L7\">Foo.kt:7</a></code>",
            FileLinks.linkify("<code>Foo.kt:7</code>"),
        )
    }

    @Test
    fun skipsPreBlocksAndExistingLinks() {
        val html = "<pre><code>Foo.kt:42</code></pre><a href=\"x\">Bar.kt:1</a>"
        assertEquals(html, FileLinks.linkify(html))
    }

    @Test
    fun ignoresPlainWordsAndUrls() {
        val html = "<p>e.g. and/or 1.5 see https://example.com/a/b.kt</p>"
        assertEquals(html, FileLinks.linkify(html))
    }
}
