package io.github.danzuep.bibleaudioproducer.workbench

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MaculaXmlTest {
    private val xml = """
        <book><sentence><wg class="cl">
          <w ref="JHN 1:1!1" xml:id="v1" class="verb" role="v" lemma="λέγω"
             english="say" gloss="says" mandarin="" chinese="说" morph="V">λέγει</w>
          <wg class="np" role="s"><w ref="JHN 1:1!2" class="noun">A</w>
            <w ref="JHN 1:1!3" class="conj" lemma="καί">and</w>
            <w ref="JHN 1:1!4" class="noun">B</w></wg>
          <wg class="pp" role="adv"><w ref="JHN 1:1!5" class="prep">in</w>
            <wg class="np"><w ref="JHN 1:1!6" class="noun">place</w></wg></wg>
          <wg class="np" role="o"><w ref="JHN 1:1!7" head="true" class="noun">head</w>
            <wg class="adjp"><w ref="JHN 1:1!8" class="adj">modifier</w></wg></wg>
          <w ref="JHN 1:1!9" class="conj" lemma="γάρ">for</w>
        </wg></sentence></book>
    """.trimIndent()

    @Test
    fun parsesWordsAndPreservesInheritedRolesAndMetadata() {
        val verse = MaculaXml.parse(xml).single()
        val word = verse.words.first()
        assertEquals("JHN 1:1", verse.reference)
        assertEquals("v1", word.id)
        assertEquals("λέγει", word.surface)
        assertEquals("λέγω", word.lemma)
        assertEquals("verb", word.pos)
        assertEquals("v", word.role)
        assertEquals("says", word.gloss)
        assertEquals("says", word.sourceGloss)
        assertEquals("说", word.mandarin)
        assertEquals("V", word.morphology)
        assertEquals(listOf("s", "s", "s"), verse.words.slice(1..3).map { it.role })
        assertEquals(true, verse.words[6].head)
    }

    @Test
    fun reordersWholeGroupsBottomUpForEachProfile() {
        val verse = MaculaXml.parse(xml).single()
        assertEquals(listOf(8, 1, 2, 3, 0, 7, 6, 4, 5), VerseWorkflow.modeOrder(verse, WordOrderMode.ENGLISH_SVO))
        assertEquals(listOf(8, 1, 2, 3, 4, 5, 0, 7, 6), VerseWorkflow.modeOrder(verse, WordOrderMode.MANDARIN_SADVVO))
        assertEquals(listOf(8, 1, 2, 3, 5, 4, 7, 6, 0), VerseWorkflow.modeOrder(verse, WordOrderMode.JAPANESE_SOV))
        assertEquals(listOf(8, 0, 1, 2, 3, 6, 7, 4, 5), VerseWorkflow.modeOrder(verse, WordOrderMode.ARABIC_VSO))
        assertEquals(verse.words.indices.toList(), VerseWorkflow.modeOrder(verse, WordOrderMode.GREEK))
        assertEquals(listOf("s", "s", "s"),
            VerseWorkflow.reorder(verse, WordOrderMode.ENGLISH_SVO).slice(1..3).map { it.role })
    }

    @Test
    fun frontingDiscontinuousPostpositiveDoesNotSplitNestedClause() {
        val verse = MaculaXml.parse("""
            <book><wg class="cl">
              <w ref="JHN 1:1!2" class="conj" lemma="γάρ">for</w>
              <wg class="cl"><w ref="JHN 1:1!1" role="adv">thus</w>
                <w ref="JHN 1:1!3" role="v">verb</w>
                <wg class="cl" role="o"><w ref="JHN 1:1!4" role="v">inner verb</w>
                  <wg class="np" role="s"><w ref="JHN 1:1!5">inner subject</w></wg>
                </wg>
              </wg>
            </wg></book>
        """.trimIndent()).single()
        assertEquals(listOf("thus", "for", "verb", "inner verb", "inner subject"), verse.words.map { it.surface })
        assertEquals(listOf(1, 0, 2, 4, 3), VerseWorkflow.modeOrder(verse, WordOrderMode.ENGLISH_SVO))
    }

    @Test
    fun postpositiveInUnclassifiedCoordinateGroupIsFronted() {
        val verse = MaculaXml.parse("""
            <book><wg type="group"><w ref="JHN 1:1!2" class="conj" lemma="δέ">but</w>
              <wg class="cl"><w ref="JHN 1:1!1" role="s">subject</w>
                <w ref="JHN 1:1!3" role="v">verb</w></wg>
            </wg></book>
        """.trimIndent()).single()
        assertEquals(listOf(1, 0, 2), VerseWorkflow.modeOrder(verse, WordOrderMode.ENGLISH_SVO))
    }

    @Test
    fun crossingVerseGroupsAreNotReorderedAndDuplicatesAreRejected() {
        val xml = """
            <book><wg class="cl"><w ref="JHN 1:1!1" role="v">v</w>
              <w ref="JHN 1:2!1" role="s">s</w></wg>
              <wg class="cl"><w ref="JHN 1:2!2" role="v">v</w>
                <w ref="JHN 1:2!3" role="s">s</w></wg>
            </book>
        """.trimIndent()
        val verses = MaculaXml.parse(xml)
        assertEquals(listOf(0), VerseWorkflow.modeOrder(verses[0], WordOrderMode.ENGLISH_SVO))
        assertEquals(listOf(0, 2, 1), VerseWorkflow.modeOrder(verses[1], WordOrderMode.ENGLISH_SVO))
        assertFailsWith<IllegalArgumentException> {
            MaculaXml.parse("""<book><w ref="JHN 1:1!1"/><w ref="JHN 1:1!1"/></book>""")
        }
    }

    @Test
    fun explicitHeadDoesNotInvertCoordinatedNouns() {
        val verse = MaculaXml.parse("""
            <book><wg class="cl"><w ref="JHN 1:1!1" role="v">verb</w>
              <wg class="np" role="s"><w ref="JHN 1:1!2" head="true">A</w>
                <wg><w ref="JHN 1:1!3" class="conj" lemma="καί">and</w>
                  <w ref="JHN 1:1!4">B</w></wg></wg>
            </wg></book>
        """.trimIndent()).single()
        assertEquals(listOf(1, 2, 3, 0), VerseWorkflow.modeOrder(verse, WordOrderMode.ENGLISH_SVO))
    }

    @Test
    fun nestedHeadMovesWholeModifierBeforeItsConstituent() {
        val verse = MaculaXml.parse("""
            <book><wg class="np"><wg class="np"><w ref="JHN 1:1!1" class="det">the</w>
              <w ref="JHN 1:1!2" class="noun" head="true">head</w></wg>
              <wg class="adjp"><w ref="JHN 1:1!3">very</w><w ref="JHN 1:1!4">good</w></wg>
            </wg></book>
        """.trimIndent()).single()
        assertEquals(listOf(2, 3, 0, 1), VerseWorkflow.modeOrder(verse, WordOrderMode.JAPANESE_SOV))
    }

    @Test
    fun sourceSurfaceIsUsedAsRecordingKey() {
        val word = MaculaXml.parse("""
            <book><w ref="JHN 8:4!9" lemma="ἐπί">ἐπ</w></book>
        """.trimIndent()).single().words.single()
        assertEquals("ἐπ", word.surface)
        assertEquals("ἐπ", word.unspokenKey)
    }

    @Test
    fun nestedArticleAdjectiveMovesWithinNounPhrase() {
        val verse = MaculaXml.parse("""
            <book><wg class="cl"><w ref="JHN 1:1!1" role="v">verb</w>
              <wg class="np" role="o" rule="DetNP"><w ref="JHN 1:1!2" class="det">article</w>
                <wg class="np" rule="NPDetAdj"><w ref="JHN 1:1!3" class="noun">noun</w>
                  <wg class="np" rule="DetAdj"><w ref="JHN 1:1!4" class="det">article</w>
                    <w ref="JHN 1:1!5" class="adj">adjective</w></wg>
                </wg>
              </wg>
            </wg></book>
        """.trimIndent()).single()
        assertEquals(listOf(0, 3, 4, 1, 2), VerseWorkflow.modeOrder(verse, WordOrderMode.ENGLISH_SVO))
        assertEquals(listOf(3, 4, 1, 2), VerseWorkflow.modeOrder(verse, WordOrderMode.GERMAN_V2).filter { it != 0 })
    }

    @Test
    fun germanKeepsTheFrontedAdverbBeforeTheFiniteVerb() {
        val verse = MaculaXml.parse("""
            <book><wg class="cl"><w ref="JHN 1:1!2" class="conj" lemma="γάρ">for</w>
              <wg class="cl"><w ref="JHN 1:1!1" role="adv">thus</w>
                <w ref="JHN 1:1!3" role="v">loved</w>
                <wg class="np" role="s"><w ref="JHN 1:1!4">God</w></wg>
                <wg class="np" role="o"><w ref="JHN 1:1!5">world</w></wg>
                <wg class="cl" role="o" rule="sub-CL"><w ref="JHN 1:1!6">that</w>
                  <wg class="cl"><w ref="JHN 1:1!7" role="v">gave</w>
                    <wg class="np" role="o"><w ref="JHN 1:1!8">son</w></wg>
                    <wg class="cl" rule="sub-CL" role="adv">
                      <w ref="JHN 1:1!9">so that</w><wg class="cl">
                        <w ref="JHN 1:1!10" role="v">have</w>
                        <w ref="JHN 1:1!11" role="o">life</w></wg>
                    </wg>
                  </wg>
                </wg>
              </wg>
            </wg></book>
        """.trimIndent()).single()
        assertEquals(listOf(1, 0, 2, 3, 4, 5, 7, 6, 8, 10, 9),
            VerseWorkflow.modeOrder(verse, WordOrderMode.GERMAN_V2))
    }

    @Test
    fun asianAndSemiticPresetsKeepExtraposedClausesAndPreverbalAdverbsInPlace() {
        val verse = MaculaXml.parse("""
            <book><wg class="cl">
              <w ref="JHN 1:1!1" role="adv">thus</w>
              <w ref="JHN 1:1!2" role="v">gave</w>
              <w ref="JHN 1:1!3" role="s">subject</w>
              <wg class="np" role="o"><w ref="JHN 1:1!4">object</w></wg>
              <wg class="cl" role="adv" rule="sub-CL">
                <w ref="JHN 1:1!5">so that</w>
                <wg class="cl"><w ref="JHN 1:1!6" role="adv">not</w>
                  <w ref="JHN 1:1!7" role="v">perish</w>
                  <w ref="JHN 1:1!8" role="s">subject</w></wg>
              </wg>
            </wg></book>
        """.trimIndent()).single()
        assertEquals(listOf(2, 0, 1, 3, 4, 7, 5, 6),
            VerseWorkflow.modeOrder(verse, WordOrderMode.MANDARIN_SADVVO))
        assertEquals(listOf(2, 0, 3, 1, 4, 7, 5, 6),
            VerseWorkflow.modeOrder(verse, WordOrderMode.JAPANESE_SOV))
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 7),
            VerseWorkflow.modeOrder(verse, WordOrderMode.ARABIC_VSO))
    }

    @Test
    fun japaneseMovesAnObjectClauseAsAWholeBeforeTheVerb() {
        val verse = MaculaXml.parse("""
            <book><wg class="cl"><w ref="JHN 1:1!1" role="v">said</w>
              <w ref="JHN 1:1!2" role="s">subject</w>
              <wg class="cl" role="o"><w ref="JHN 1:1!3" role="s">inner subject</w>
                <w ref="JHN 1:1!4" role="v">came</w></wg>
            </wg></book>
        """.trimIndent()).single()
        assertEquals(listOf(1, 2, 3, 0),
            VerseWorkflow.modeOrder(verse, WordOrderMode.JAPANESE_SOV))
        assertEquals(listOf(1, 0, 2, 3),
            VerseWorkflow.modeOrder(verse, WordOrderMode.MANDARIN_SADVVO))
    }

    @Test
    fun john1v1EnglishOrderFollowsTheBsbAndKeepsTheSourceGloss() {
        val verse = MaculaXml.parse("""
<book><sentence>
<wg rule="Conj3CL" class="cl">
<wg class="cl" rule="P-VC-S">
<wg rule="PrepNp" class="pp" role="p">
<w ref="JHN 1:1!1" class="prep" lemma="ἐν" english="in" mandarin="" gloss="In [the]">Ἐν</w>
<w ref="JHN 1:1!2" class="noun" lemma="ἀρχή" english="beginning" mandarin="太初" gloss="beginning">ἀρχῇ</w>
</wg>
<w role="vc" ref="JHN 1:1!3" class="verb" lemma="εἰμί" english="was" mandarin="有" gloss="was">ἦν</w>
<wg class="np" rule="DetNP" role="s">
<w ref="JHN 1:1!4" class="det" lemma="ὁ" english="the" mandarin="" gloss="the">ὁ</w>
<w ref="JHN 1:1!5" class="noun" lemma="λόγος" english="word" mandarin="道" gloss="Word">λόγος</w>
</wg>
</wg>
<wg type="group">
<w ref="JHN 1:1!6" class="conj" lemma="καί" english="and" mandarin="而且" gloss="and">καὶ</w>
<wg class="cl" rule="S-VC-P">
<wg class="np" rule="DetNP" role="s">
<w ref="JHN 1:1!7" class="det" lemma="ὁ" english="the" mandarin="" gloss="the">ὁ</w>
<w ref="JHN 1:1!8" class="noun" lemma="λόγος" english="word" mandarin="道" gloss="Word">λόγος</w>
</wg>
<w role="vc" ref="JHN 1:1!9" class="verb" lemma="εἰμί" english="was" mandarin="在" gloss="was">ἦν</w>
<wg rule="PrepNp" class="pp" role="p">
<w ref="JHN 1:1!10" class="prep" lemma="πρός" english="with" mandarin="与~同" gloss="with">πρὸς</w>
<wg class="np" rule="DetNP">
<w ref="JHN 1:1!11" class="det" lemma="ὁ" english="" mandarin="" gloss="-">τὸν</w>
<w ref="JHN 1:1!12" class="noun" lemma="θεός" english="God" mandarin="神" gloss="God">θεόν</w>
</wg>
</wg>
</wg>
</wg>
<wg type="group">
<w ref="JHN 1:1!13" class="conj" lemma="καί" english="and" mandarin="而且" gloss="and">καὶ</w>
<wg class="cl" rule="P-VC-S">
<w role="p" ref="JHN 1:1!14" class="noun" lemma="θεός" english="God" mandarin="神" gloss="God">θεὸς</w>
<w role="vc" ref="JHN 1:1!15" class="verb" lemma="εἰμί" english="was" mandarin="是" gloss="was">ἦν</w>
<wg class="np" rule="DetNP" role="s">
<w ref="JHN 1:1!16" class="det" lemma="ὁ" english="the" mandarin="" gloss="the">ὁ</w>
<w ref="JHN 1:1!17" class="noun" lemma="λόγος" english="word" mandarin="道" gloss="Word">λόγος</w>
</wg>
</wg>
</wg>
</wg>
</sentence></book>
        """.trimIndent()).single()
        assertEquals("In [the]", verse.words[0].gloss)
        assertEquals("", verse.words[10].gloss)
        val english = VerseWorkflow.reorder(verse, WordOrderMode.ENGLISH_SVO).map { it.gloss }.filter { it.isNotEmpty() }
        assertEquals(
            "In [the] beginning was the Word and the Word was with God and the Word was God",
            english.joinToString(" ")
        )
    }
}
