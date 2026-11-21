package io.github.danzuep.bibleaudioproducer.workbench

internal object MaculaWordOrder {
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
        fun visit(group: SyntaxGroup, incomplete: Boolean, subordinate: Boolean) {
            val partial = incomplete || group.partial
            val sub = subordinate || group.rule.equals("sub-CL", ignoreCase = true)
            group.children.filterIsInstance<SyntaxGroup>().forEach { visit(it, partial, sub) }
            val all = members(group)
            if (partial || !contiguous(all)) return
            if (group.children.size == 2) {
                val particle = group.children.filterIsInstance<SyntaxWord>()
                    .singleOrNull { words[it.index].lemma in setOf("γάρ", "δέ") }
                val clause = group.children.filterIsInstance<SyntaxGroup>().singleOrNull { it.category == "cl" }
                if (particle != null && clause != null) {
                    val remaining = current(clause)
                    replace(all, listOf(particle.index) + remaining)
                    visit(clause, partial, sub)
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
            if ((mode.headFinal || mode == WordOrderMode.ENGLISH_SVO) && group.category == "np" && !coordinated) {
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
                }
            }
            if (group.category != "cl" || (mode.verbSecond && sub)) return
            val orderedChildren = group.children.sortedBy { positions(members(it)).firstOrNull() ?: Int.MAX_VALUE }
            fun slot(node: SyntaxNode): String = if (
                node is SyntaxWord && words[node.index].lemma in setOf("γάρ", "δέ")
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
                val indices = segment.flatMap(::members)
                data class Part(val nodes: List<SyntaxNode>, val slot: String)
                val parts = mutableListOf<Part>()
                for (node in segment) {
                    if (mode == WordOrderMode.ENGLISH_SVO && slot(node) == "V" &&
                        parts.lastOrNull()?.slot == "ADV"
                    ) {
                        val adverb = parts.removeAt(parts.lastIndex)
                        parts.add(Part(adverb.nodes + node, "V"))
                    } else parts.add(Part(listOf(node), slot(node)))
                }
                val leading = parts.firstOrNull()?.takeIf {
                    mode == WordOrderMode.ENGLISH_SVO && it.nodes.size == 1 &&
                        category(it.nodes.single()) == "pp" && it.slot in setOf("O", "ADV")
                }
                val sorted = parts.filter { it !== leading }.sortedBy {
                    if (it.slot == "CONJ") -1
                    else slots.indexOf(it.slot).let { rank -> if (rank < 0) slots.size else rank }
                }
                val reordered = if (mode.verbSecond && sorted.any { it.slot == "V" }) {
                    val lead = sorted.firstOrNull { it.slot != "V" }
                    if (lead == null) sorted else listOf(lead) + sorted.filter { it.slot == "V" } +
                        sorted.filter { it !== lead && it.slot != "V" }
                } else sorted
                replace(indices, (listOfNotNull(leading) + reordered).flatMap { it.nodes.flatMap(::current) })
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
        visit(root, false, false)
        return result
    }
}
