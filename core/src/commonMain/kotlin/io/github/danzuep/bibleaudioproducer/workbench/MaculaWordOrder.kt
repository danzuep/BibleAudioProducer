package io.github.danzuep.bibleaudioproducer.workbench

internal object MaculaWordOrder {
    private val NEGATIONS = setOf("οὐ", "οὐκ", "οὐχ", "μή")
    private val QUESTION_WORDS = setOf("πῶς", "ποῦ", "πόθεν", "πότε")

    fun order(words: List<VerseWord>, root: SyntaxGroup, mode: WordOrderMode): List<Int> {
        val result = words.indices.toMutableList()
        val slots = mode.slots ?: return result

        fun members(node: SyntaxNode): List<Int> = when (node) {
            is SyntaxWord -> listOf(node.index)
            is SyntaxGroup -> node.children.flatMap(::members)
        }
        fun positions(indices: List<Int>) = result.indices.filter { result[it] in indices }
        fun contiguous(indices: List<Int>): Boolean {
            val positions = positions(indices)
            return positions.isNotEmpty() && positions.size == indices.size &&
                positions.last() - positions.first() + 1 == positions.size
        }
        fun replace(indices: List<Int>, ordered: List<Int>) {
            positions(indices).zip(ordered).forEach { (position, index) -> result[position] = index }
        }
        fun current(node: SyntaxNode): List<Int> {
            val indices = members(node)
            return result.filter { it in indices }
        }
        fun role(node: SyntaxNode): String = when (node) {
            is SyntaxWord -> words[node.index].role
            is SyntaxGroup -> node.role
        }.lowercase()
        fun category(node: SyntaxNode): String = when (node) {
            is SyntaxWord -> words[node.index].pos
            is SyntaxGroup -> node.category
        }
        fun head(node: SyntaxNode): Boolean = when (node) {
            is SyntaxWord -> words[node.index].head
            is SyntaxGroup -> node.head || node.children.any(::head)
        }
        fun isWord(node: SyntaxNode, test: (VerseWord) -> Boolean) =
            node is SyntaxWord && test(words[node.index])
        fun visit(group: SyntaxGroup, incomplete: Boolean, subordinate: Boolean, inNominal: Boolean) {
            val partial = incomplete || group.partial
            val sub = subordinate || group.rule.equals("sub-CL", ignoreCase = true)
            val nominal = inNominal || group.category == "np"
            group.children.filterIsInstance<SyntaxGroup>().forEach { visit(it, partial, sub, nominal) }
            val all = members(group)
            if (partial || !contiguous(all)) return
            if (group.children.size == 2) {
                val particle = group.children.filterIsInstance<SyntaxWord>()
                    .singleOrNull { words[it.index].lemma in setOf("γάρ", "δέ", "οὖν") }
                val clause = group.children.filterIsInstance<SyntaxGroup>().singleOrNull { it.category == "cl" }
                if (particle != null && clause != null) {
                    val remaining = current(clause)
                    replace(all, listOf(particle.index) + remaining)
                    visit(clause, partial, sub, nominal)
                }
            }
            val children = group.children.sortedBy { positions(members(it)).firstOrNull() ?: Int.MAX_VALUE }
            if (children.any { !contiguous(members(it)) }) return
            val coordinated = children.any { child ->
                category(child) == "conj" ||
                    (child is SyntaxGroup && child.children.firstOrNull()?.let { category(it) == "conj" } == true)
            }

            if (mode.postpositions && group.category == "pp" && !coordinated) {
                val prep = children.firstOrNull()
                if (prep != null && category(prep) == "prep" && children.size > 1) {
                    replace(all, children.drop(1).flatMap(::current) + current(prep))
                }
            }
            if ((mode.headFinal || mode == WordOrderMode.ENGLISH_SVO || mode.verbSecond) &&
                group.category == "np" && !coordinated
            ) {
                val headIndex = children.indexOfFirst(::head)
                if (headIndex >= 0 && children.count(::head) == 1) {
                    val before = children.take(headIndex)
                    val after = children.drop(headIndex + 1)
                    replace(all, (before + after + children[headIndex]).flatMap(::current))
                } else if (group.rule.equals("NpAdjp", ignoreCase = true) && children.size == 2 &&
                    category(children[1]) in setOf("adj", "adjp")
                ) {
                    val noun = current(children[0])
                    val determiners = noun.takeWhile { words[it].pos == "det" }
                    replace(all, determiners + current(children[1]) + noun.drop(determiners.size))
                } else if (group.rule.equals("NPDetAdj", ignoreCase = true) && children.size == 2 &&
                    category(children[0]) == "noun" && children[1] is SyntaxGroup &&
                    (children[1] as SyntaxGroup).rule.equals("DetAdj", ignoreCase = true)
                ) {
                    replace(all, current(children[1]) + current(children[0]))
                } else if (group.rule.equals("DetNP", ignoreCase = true) && children.size == 2 &&
                    category(children[0]) == "det" && children[1] is SyntaxGroup
                ) {
                    val nounPhrase = children[1] as SyntaxGroup
                    val noun = nounPhrase.children.firstOrNull()
                    val modifier = nounPhrase.children.getOrNull(1) as? SyntaxGroup
                    if (nounPhrase.rule.equals("NPDetAdj", ignoreCase = true) &&
                        nounPhrase.children.size == 2 && noun is SyntaxWord &&
                        category(noun) == "noun" && modifier != null &&
                        modifier.rule.equals("DetAdj", ignoreCase = true)
                    ) {
                        replace(all, current(modifier) + current(children[0]) + current(noun))
                    }
                }
            }
            if (group.category != "cl" || (mode.verbSecond && nominal)) return
            val orderedChildren = group.children.sortedBy { positions(members(it)).firstOrNull() ?: Int.MAX_VALUE }
            fun slot(node: SyntaxNode): String = if (
                node is SyntaxGroup && node.category == "cl" &&
                (mode.verbSecond || (mode != WordOrderMode.ENGLISH_SVO && role(node) == "adv"))
            ) {
                ""
            } else if (
                node is SyntaxWord && words[node.index].lemma in setOf("γάρ", "δέ", "οὖν")
            ) "CONJ" else when (role(node)) {
                "s" -> "S"
                "v", "vc" -> "V"
                "o", "p" -> "O"
                "io" -> "IO"
                "adv" -> "ADV"
                else -> ""
            }
            fun sort(segment: List<SyntaxNode>) {
                if (segment.size < 2) return
                if (mode.verbSecond && segment.none { slot(it) == "V" }) return
                val indices = segment.flatMap(::members)
                data class Part(val nodes: List<SyntaxNode>, val slot: String)
                val parts = mutableListOf<Part>()
                for (node in segment) {
                    if (mode in setOf(WordOrderMode.ENGLISH_SVO, WordOrderMode.ARABIC_VSO) && slot(node) == "V" &&
                        parts.lastOrNull()?.slot == "ADV"
                    ) {
                        val adverb = parts.removeAt(parts.lastIndex)
                        // "is not" rather than "not is".
                        val negatedBe = mode == WordOrderMode.ENGLISH_SVO &&
                            adverb.nodes.singleOrNull()?.let { isWord(it) { w -> w.lemma in NEGATIONS } } == true &&
                            isWord(node) { w -> w.lemma == "εἰμί" }
                        parts.add(Part(if (negatedBe) listOf(node) + adverb.nodes else adverb.nodes + node, "V"))
                    } else parts.add(Part(listOf(node), slot(node)))
                }
                val leading = parts.firstOrNull()?.takeIf {
                    mode == WordOrderMode.ENGLISH_SVO && it.nodes.size == 1 &&
                        category(it.nodes.single()) == "pp" && it.slot in setOf("O", "ADV")
                }
                val remaining = parts.filter { it !== leading }
                // "In the beginning was the Word": a fronted phrase before "to be" and its subject stays inverted.
                if (leading != null && remaining.map { it.slot } == listOf("V", "S") &&
                    remaining[0].nodes.singleOrNull()?.let { it is SyntaxWord && words[it.index].lemma == "εἰμί" } == true
                ) return
                val sorted = remaining.sortedBy {
                    if (it.slot == "CONJ") -1
                    else if (mode.verbSecond && sub && it.slot == "V") slots.size
                    else slots.indexOf(it.slot).let { rank -> if (rank < 0) slots.size else rank }
                }
                val reordered = if (mode.verbSecond && !sub && sorted.any { it.slot == "V" }) {
                    val conjunctions = sorted.filter { it.slot == "CONJ" }
                    val lead = parts.firstOrNull { it.slot != "V" && it.slot != "CONJ" }
                    conjunctions + listOfNotNull(lead) + sorted.filter { it.slot == "V" } +
                        sorted.filter { it !== lead && it.slot != "V" && it.slot != "CONJ" }
                } else sorted
                // "Who are you": a question word leads its clause.
                val question = if (mode == WordOrderMode.ENGLISH_SVO) {
                    reordered.firstOrNull { part ->
                        part.slot != "CONJ" && part.nodes.size == 1 && part.nodes.single().let { node ->
                            (node is SyntaxWord || (node as SyntaxGroup).category != "cl") &&
                                members(node).any { words[it].let { w -> w.type == "interrogative" || w.lemma in QUESTION_WORDS } }
                        }
                    }
                } else null
                val ordered = if (question == null) listOfNotNull(leading) + reordered else
                    listOf(question) + listOfNotNull(leading) + reordered.filter { it !== question }
                replace(indices, ordered.flatMap { it.nodes.flatMap(::current) })
            }
            val segment = mutableListOf<SyntaxNode>()
            for (child in orderedChildren) {
                if (slot(child).isEmpty()) {
                    sort(segment)
                    segment.clear()
                } else segment.add(child)
            }
            sort(segment)
        }
        visit(root, false, false, false)
        return result
    }
}
