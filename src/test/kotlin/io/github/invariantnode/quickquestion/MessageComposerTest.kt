package io.github.invariantnode.quickquestion

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageComposerTest {
    private val a = "```kt Foo.kt:1-2\nfun a()\n```"
    private val b = "```kt Foo.kt:9-12\nfun b()\n```"

    @Test
    fun firstMessageCarriesTheSelection() {
        assertEquals("$a\n\nWhat is this?", MessageComposer.compose("What is this?", a, null))
    }

    @Test
    fun unchangedSelectionIsNotRepeated() {
        assertEquals("And then?", MessageComposer.compose("And then?", a, a))
    }

    @Test
    fun newSelectionIsIncludedAgain() {
        assertEquals("$b\n\nAnd this?", MessageComposer.compose("And this?", b, a))
    }

    @Test
    fun noSelectionSendsOnlyTheQuestion() {
        assertEquals("Hi", MessageComposer.compose("Hi", null, a))
    }
}
