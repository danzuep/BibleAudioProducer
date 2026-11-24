package io.github.danzuep.bibleaudioproducer.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class GuideTokenLinkTest {
    @Test
    fun survivingWordsKeepTheirCardAndInsertedWordsAreUnlinked() {
        val original = listOf("in" to 0, "the" to 1, "beginning" to 2)
        val linked = linkGuideTokens(original, listOf("in", "very", "the", "beginning"))
        assertEquals(listOf("in" to 0, "very" to null, "the" to 1, "beginning" to 2), linked)
    }

    @Test
    fun removedWordsDropTheirLinks() {
        val linked = linkGuideTokens(listOf("a" to 0, "b" to 1, "c" to 2), listOf("a", "c"))
        assertEquals(listOf("a" to 0, "c" to 2), linked)
    }
}
