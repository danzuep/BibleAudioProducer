package io.github.danzuep.bibleaudioproducer.workbench

import kotlin.test.Test
import kotlin.test.assertEquals

class VerseWorkflowTest {
    private fun word(surface: String, lemma: String, pos: String, grammaticalCase: String = "") =
        VerseWord(surface, lemma, pos, "", grammaticalCase, "", surface)

    @Test
    fun onboardingStartsJohnThenAdvancesInVerseOrder() {
        val references = listOf("JHN 1:1", "JHN 1:2", "JHN 3:16")
        assertEquals("JHN 1:1", VerseWorkflow.nextReference(references, "JHN 3:16", true))
        assertEquals("JHN 1:2", VerseWorkflow.nextReference(references, "JHN 1:1", false))
        assertEquals(null, VerseWorkflow.nextReference(references, "missing", false))
        assertEquals(null, VerseWorkflow.nextReference(references, "JHN 3:16", false))
    }

    @Test
    fun fullVersePlaybackIncludesProperNamesWithoutChangingLegacyPlayback() {
        val words = listOf(word("Jesus", "Jesus", "proper_noun"), word("said", "say", "verb"))
        assertEquals(listOf("said"), VerseWorkflow.spokenWords(words, emptyMap(), null, emptySet()).map { it.surface })
        assertEquals(words, VerseWorkflow.spokenWords(words, emptyMap(), null, emptySet(), includeProper = true))
    }

    @Test
    fun wordsWithoutAnyGlossAreDisabledButStillInOriginalText() {
        val words = listOf(
            word("ὁ", "ὁ", "det", "nominative").copy(gloss = ""),
            word("λόγος", "λόγος", "noun", "nominative"),
            word("τό", "ὁ", "det").copy(gloss = "", mandarin = "这")
        )
        assertEquals(listOf("λόγος", "τό"), VerseWorkflow.spokenWords(words, emptyMap(), null, emptySet()).map { it.surface })
        val restored = mutableSetOf<String>().also { words[0].withDisabled(it, false) }
        assertEquals(false, words[0].disabled(restored))
        words[0].withDisabled(restored, true)
        assertEquals(true, words[0].disabled(restored))
        assertEquals("ὁ λόγος τό", VerseWorkflow.originalText(AudioVerse("John 1:1", words)))
    }

    @Test
    fun learnsChunkOrderAndRespectsPerFormOmissions() {
        val words = listOf(
            word("ὁ", "ὁ", "det", "nominative"),
            word("λόγος", "λόγος", "noun", "nominative"),
            word("ἦν", "εἰμί", "verb"),
            word("τὸν", "ὁ", "det", "accusative"),
            word("θεόν", "θεός", "noun", "accusative")
        )
        val chunks = VerseWorkflow.chunks(words)
        assertEquals(listOf("NP.nom", "V", "NP.acc"), chunks.map { it.slot })
        val rules = VerseWorkflow.learnRules(listOf(chunks to listOf(2, 0, 1)))
        assertEquals(listOf(2, 0, 1), VerseWorkflow.order(chunks, rules, null))
        assertEquals(
            listOf("θεόν", "ὁ", "λόγος", "ἦν"),
            VerseWorkflow.spokenWords(words, rules, null, setOf("τὸν")).map { it.surface }
        )
        assertEquals(listOf(2, 0, 1), VerseWorkflow.order(chunks, rules, listOf(0, 0, 2)))
    }

    @Test
    fun ordersCardsByFirstChapterAndFrequency() {
        val cards = listOf(
            RecordingCard("JHN", "b", "", 1),
            RecordingCard("JHN", "a", "", 1),
            RecordingCard("JHN", "c", "", 1)
        )
        val verses = listOf(
            AudioVerse("JHN 1:1", listOf(word("a", "a", "noun"), word("b", "b", "noun"), word("a", "a", "noun"))),
            AudioVerse("JHN 2:1", listOf(word("c", "c", "noun")))
        )
        assertEquals(listOf("a", "b", "c"), VerseWorkflow.chapterOrder(cards, verses).map { it.lemma })
    }

    @Test
    fun explicitVerseOrderReordersIndividualWords() {
        val words = listOf(
            word("ἐν", "ἐν", "prep"),
            word("ἀρχῇ", "ἀρχή", "noun", "dative"),
            word("ἦν", "εἰμί", "verb")
        )
        assertEquals(
            listOf("ἦν", "ἐν", "ἀρχῇ"),
            VerseWorkflow.spokenWords(words, emptyMap(), null, emptySet(), listOf(2, 0, 1)).map { it.surface }
        )
    }

    @Test
    fun recordingCardsKeepDisplayOrderAndFullSourcePositions() {
        val repeated = word("same", "same", "noun")
        val omitted = word("particle", "particle", "conj").copy(gloss = "")
        val words = listOf(repeated, omitted, repeated, word("verb", "verb", "verb"))
        val cards = VerseWorkflow.recordingWords(words, listOf(3, 2, 1, 0), emptySet())
        assertEquals(listOf(3, 2, 0), cards.map { it.originalIndex })
        assertEquals(listOf("verb", "same", "same"), cards.map { it.word.surface })
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            VerseWorkflow.recordingWords(words, listOf(3, 2, 0, 0), emptySet())
        }
    }

    @Test
    fun missingSyntaxKeepsOriginalOrderForEveryMode() {
        val words = listOf(
            word("ἐν", "ἐν", "prep"),
            word("ἀρχῇ", "ἀρχή", "noun", "dative"),
            word("ἦν", "εἰμί", "verb"),
            word("ὁ", "ὁ", "det", "nominative"),
            word("λόγος", "λόγος", "noun", "nominative"),
            word("τὸν", "ὁ", "det", "accusative"),
            word("θεόν", "θεός", "noun", "accusative")
        )
        for (mode in WordOrderMode.entries) {
            assertEquals(words.indices.toList(), VerseWorkflow.modeOrder(AudioVerse("JHN 1:1", words), mode))
        }
    }

    @Test
    fun onlyAJohnPresetSelectionUpdatesTheProjectDefault() {
        val previous = WordOrderMode.JAPANESE_SOV.name
        assertEquals(WordOrderMode.ENGLISH_SVO.name,
            VerseWorkflow.selectedDefault("JHN 3:16", WordOrderMode.ENGLISH_SVO, previous))
        for (mode in listOf(WordOrderMode.GREEK, WordOrderMode.CUSTOM, null)) {
            assertEquals(previous, VerseWorkflow.selectedDefault("JHN 3:16", mode, previous))
        }
        assertEquals(previous, VerseWorkflow.selectedDefault("JHN 1:1", WordOrderMode.ENGLISH_SVO, previous))
        for (name in listOf(null, "GREEK", "CUSTOM", "obsolete")) {
            assertEquals(null, VerseWorkflow.defaultMode(name))
        }
        assertEquals(WordOrderMode.JAPANESE_SOV, VerseWorkflow.defaultMode(previous))
    }

    @Test
    fun defaultOrderAppliesWithoutReplacingSavedWordOrChunkOrders() {
        val verse = MaculaXml.parse("""
            <book><wg class="np" rule="NpAdjp"><w ref="JHN 1:1!1" class="noun" english="noun">noun</w>
              <w ref="JHN 1:1!2" class="adj" english="adj">adj</w></wg></book>
        """.trimIndent()).single()
        val words = verse.words
        val state = AudioProjectState(defaultWordOrder = "ENGLISH_SVO")
        val order = VerseWorkflow.effectiveWordOrder(verse, state)
        assertEquals(listOf(1, 0), order)
        assertEquals(listOf("adj", "noun"),
            VerseWorkflow.spokenWords(words, emptyMap(), null, emptySet(), order).map { it.surface })
        assertEquals(listOf(0, 1),
            VerseWorkflow.effectiveWordOrder(verse, state.copy(wordOrders = mapOf(verse.reference to listOf(0, 1)))))
        assertEquals(null, VerseWorkflow.effectiveWordOrder(
            verse, state.copy(seedOrders = mapOf(verse.reference to listOf(0)))
        ))
        assertEquals(null, VerseWorkflow.effectiveWordOrder(verse, state.copy(defaultWordOrder = "GREEK")))
    }
}
