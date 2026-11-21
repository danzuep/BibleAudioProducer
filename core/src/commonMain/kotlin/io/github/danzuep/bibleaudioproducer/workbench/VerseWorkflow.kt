package io.github.danzuep.bibleaudioproducer.workbench

data class VerseWord(
    val surface: String,
    val lemma: String,
    val pos: String,
    val type: String,
    val grammaticalCase: String,
    val mood: String,
    val gloss: String,
    val role: String = "",
    val mandarin: String = "",
    val morphology: String = "",
    val reference: String = "",
    val id: String = "",
    val head: Boolean = false,
    val sourceGloss: String = ""
) {
    val proper: Boolean get() = type == "proper" || pos == "proper_noun"
    val unspokenKey: String get() = surface.ifEmpty { lemma }
    val glossless: Boolean get() = gloss.isBlank() && mandarin.isBlank()

    // Glossless words are disabled unless explicitly restored, which is recorded as a "+" marked key.
    fun disabled(unspoken: Set<String>) = when {
        unspokenKey in unspoken -> true
        "+$unspokenKey" in unspoken -> false
        else -> glossless
    }

    fun withDisabled(unspoken: MutableSet<String>, disabled: Boolean) {
        unspoken.remove(unspokenKey)
        unspoken.remove("+$unspokenKey")
        if (disabled) unspoken.add(unspokenKey) else if (glossless) unspoken.add("+$unspokenKey")
    }
}

data class AudioVerse(val reference: String, val words: List<VerseWord>, val syntax: SyntaxGroup? = null) {
    val chapter: Int get() = reference.substringAfter(' ').substringBefore(':').toInt()
}

data class VerseRecordingWord(val word: VerseWord, val originalIndex: Int)

data class GrammarChunk(val slot: String, val words: List<VerseWord>)
data class GrammarRule(val keep: Int = 0, val flip: Int = 0)

enum class WordOrderMode(
    val slots: List<String>?,
    val headFinal: Boolean = false,
    val postpositions: Boolean = false,
    val verbSecond: Boolean = false
) {
    GREEK(null),
    ENGLISH_SVO(listOf("CONJ", "S", "V", "IO", "O", "ADV")),
    GERMAN_V2(listOf("CONJ", "S", "V", "IO", "O", "ADV"), verbSecond = true),
    MANDARIN_SADVVO(listOf("CONJ", "S", "ADV", "V", "IO", "O"), headFinal = true),
    JAPANESE_SOV(listOf("S", "ADV", "IO", "O", "V", "CONJ"), headFinal = true, postpositions = true),
    ARABIC_VSO(listOf("CONJ", "V", "S", "O", "IO", "ADV")),
    CUSTOM(null)
}

object VerseWorkflow {
    /** Keep display order while retaining each card's position in the complete source verse. */
    fun recordingWords(words: List<VerseWord>, order: List<Int>, unspoken: Set<String>): List<VerseRecordingWord> {
        require(validOrder(order, words.size)) { "Invalid verse word order." }
        return order.filter { !words[it].disabled(unspoken) }.map { VerseRecordingWord(words[it], it) }
    }

    fun defaultMode(name: String?): WordOrderMode? =
        WordOrderMode.entries.firstOrNull { it.name == name && it != WordOrderMode.GREEK && it != WordOrderMode.CUSTOM }

    fun selectedDefault(reference: String, mode: WordOrderMode?, previous: String?): String? =
        if (reference == "JHN 3:16" && mode != null && defaultMode(mode.name) != null) mode.name else previous

    fun effectiveWordOrder(verse: AudioVerse, state: AudioProjectState): List<Int>? =
        state.wordOrders[verse.reference] ?: if (verse.reference in state.seedOrders) null
        else defaultMode(state.defaultWordOrder)?.let { modeOrder(verse, it) }

    private val nominal = setOf("noun", "pron", "det", "adj", "num")
    private val heads = setOf("noun", "pron", "num")
    private val cases = mapOf(
        "nominative" to "nom", "accusative" to "acc", "dative" to "dat",
        "genitive" to "gen", "vocative" to "voc"
    )

    fun nextReference(references: List<String>, current: String, onboarding: Boolean): String? {
        if (onboarding && current == "JHN 3:16") return references.firstOrNull { it == "JHN 1:1" }
        val index = references.indexOf(current)
        return if (index >= 0) references.getOrNull(index + 1) else null
    }

    fun chapterOrder(cards: List<RecordingCard>, verses: List<AudioVerse>): List<RecordingCard> {
        val byLemma = cards.associateBy { it.lemma }
        val counts = mutableMapOf<Int, MutableMap<String, Int>>()
        for (verse in verses) {
            val chapter = counts.getOrPut(verse.chapter) { linkedMapOf() }
            for (word in verse.words) {
                if (word.lemma in byLemma) chapter[word.lemma] = (chapter[word.lemma] ?: 0) + 1
            }
        }
        val placed = mutableSetOf<String>()
        val ordered = mutableListOf<RecordingCard>()
        for ((_, chapter) in counts.entries.sortedBy { it.key }) {
            for ((lemma, _) in chapter.entries.sortedByDescending { it.value }) {
                if (placed.add(lemma)) ordered.add(byLemma.getValue(lemma))
            }
        }
        return ordered + cards.filter { it.lemma !in placed }
    }

    fun chunks(words: List<VerseWord>): List<GrammarChunk> {
        data class Chunk(
            var slot: String,
            val words: MutableList<VerseWord>,
            var headed: Boolean,
            var grammaticalCase: String? = null
        )
        val chunks = mutableListOf<Chunk>()
        var open: Chunk? = null
        var last: Chunk? = null
        fun nounSlot(word: VerseWord) = "NP.${cases[word.grammaticalCase] ?: "x"}"
        fun add(slot: String, word: VerseWord, headed: Boolean): Chunk =
            Chunk(slot, mutableListOf(word), headed).also { chunks.add(it) }

        for (word in words) {
            when {
                word.pos == "verb" -> {
                    open = null
                    last = null
                    add(when (word.mood) {
                        "participle" -> "PTC"
                        "infinitive" -> "INF"
                        else -> "V"
                    }, word, true)
                }
                word.pos == "prep" -> {
                    last = null
                    open = add("PP", word, false)
                }
                word.pos in nominal -> {
                    val current = open
                    if (current != null && !current.headed) {
                        current.words.add(word)
                        if (word.pos in heads) {
                            current.headed = true
                            if (current.slot != "PP") current.slot = nounSlot(word)
                            last = current
                        }
                    } else if (word.pos == "adj" && last?.headed == true &&
                        word.grammaticalCase == last.grammaticalCase
                    ) {
                        last.words.add(word)
                    } else {
                        val next = add(nounSlot(word), word, word.pos in heads)
                        next.grammaticalCase = word.grammaticalCase
                        last = next
                        open = if (next.headed) null else next
                    }
                }
                else -> {
                    open = null
                    last = null
                    add(when (word.pos) {
                        "conj" -> "CONJ"
                        "adv" -> "ADV"
                        else -> "OTHER"
                    }, word, true)
                }
            }
        }
        return chunks.map { GrammarChunk(it.slot, it.words.toList()) }
    }

    fun learnRules(examples: List<Pair<List<GrammarChunk>, List<Int>>>): Map<String, GrammarRule> {
        val rules = linkedMapOf<String, GrammarRule>()
        for ((chunks, order) in examples) {
            require(validOrder(order, chunks.size))
            val clauses = clauses(chunks)
            val positions = order.withIndex().associate { it.value to it.index }
            for (i in chunks.indices) for (j in i + 1 until chunks.size) {
                if (clauses[i] != clauses[j]) continue
                val key = "${chunks[i].slot}|${chunks[j].slot}"
                val rule = rules[key] ?: GrammarRule()
                rules[key] = if (positions.getValue(i) < positions.getValue(j)) {
                    rule.copy(keep = rule.keep + 1)
                } else {
                    rule.copy(flip = rule.flip + 1)
                }
            }
        }
        return rules
    }

    fun order(chunks: List<GrammarChunk>, rules: Map<String, GrammarRule>, explicit: List<Int>?): List<Int> {
        if (explicit != null && validOrder(explicit, chunks.size)) return explicit
        val clauses = clauses(chunks)
        return clauses.distinct().flatMap { clause ->
            val members = chunks.indices.filter { clauses[it] == clause }
            fun keeps(i: Int, j: Int): Double {
                val rule = rules["${chunks[i].slot}|${chunks[j].slot}"] ?: return 1.0
                val total = rule.keep + rule.flip
                return if (total > 0) rule.keep.toDouble() / total else 1.0
            }
            fun score(i: Int) = members.sumOf { j ->
                when {
                    i == j -> 0.0
                    i < j -> keeps(i, j)
                    else -> 1.0 - keeps(j, i)
                }
            }
            members.sortedWith(compareByDescending<Int> { score(it) }.thenBy { it })
        }
    }

    fun spokenWords(
        words: List<VerseWord>, rules: Map<String, GrammarRule>,
        explicit: List<Int>?, unspoken: Set<String>, wordOrder: List<Int>? = null,
        includeProper: Boolean = false
    ): List<VerseWord> = orderedWordIndices(words, rules, wordOrder, explicit).map(words::get)
        .filter { (includeProper || !it.proper) && !it.disabled(unspoken) }

    fun originalText(verse: AudioVerse) = verse.words.joinToString(" ") { it.surface }

    fun orderedWordIndices(
        words: List<VerseWord>, rules: Map<String, GrammarRule>,
        wordOrder: List<Int>?, chunkOrder: List<Int>? = null
    ): List<Int> {
        if (wordOrder != null && validOrder(wordOrder, words.size)) return wordOrder
        val chunks = chunks(words)
        val remaining = words.indices.toMutableList()
        val chunkWordIndices = chunks.map { chunk ->
            chunk.words.map { word ->
                remaining.first { words[it] == word }.also { remaining.remove(it) }
            }
        }
        return order(chunks, rules, chunkOrder).flatMap { chunkWordIndices[it] }
    }

    fun modeOrder(verse: AudioVerse, mode: WordOrderMode): List<Int> =
        verse.syntax?.let { MaculaWordOrder.order(verse.words, it, mode) } ?: verse.words.indices.toList()

    fun reorder(verse: AudioVerse, mode: WordOrderMode): List<VerseWord> =
        modeOrder(verse, mode).map(verse.words::get)

    private fun validOrder(order: List<Int>, count: Int) =
        order.size == count && order.toSet().size == count && order.all { it in 0 until count }

    private fun clauses(chunks: List<GrammarChunk>): List<Int> {
        var clause = 0
        return chunks.mapIndexed { index, chunk ->
            if (index > 0 && chunk.slot == "CONJ") clause++
            clause
        }
    }
}
