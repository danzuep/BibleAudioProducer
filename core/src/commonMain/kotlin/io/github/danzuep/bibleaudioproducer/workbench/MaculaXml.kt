package io.github.danzuep.bibleaudioproducer.workbench

import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.xmlStreaming

sealed interface SyntaxNode
data class SyntaxWord(val index: Int) : SyntaxNode
data class SyntaxGroup(
    val category: String,
    val role: String,
    val rule: String,
    val head: Boolean,
    val children: List<SyntaxNode>,
    val partial: Boolean = false
) : SyntaxNode

/** Reads MACULA lowfat trees and projects groups onto their individual verses. */
object MaculaXml {
    private sealed interface RawNode
    private class RawGroup(
        val category: String = "", val role: String = "", val rule: String = "",
        val head: Boolean = false, val children: MutableList<RawNode> = mutableListOf()
    ) : RawNode
    private data class RawWord(val word: VerseWord) : RawNode

    fun parse(xml: String): List<AudioVerse> {
        val root = RawGroup()
        val stack = mutableListOf(root)
        val allWords = mutableMapOf<String, MutableList<VerseWord>>()
        val references = mutableSetOf<String>()
        val reader = xmlStreaming.newReader(xml)
        var attributes: Map<String, String>? = null
        var text = StringBuilder()
        try {
            while (reader.hasNext()) {
                when (reader.next()) {
                    EventType.START_ELEMENT -> when (reader.localName) {
                        "wg" -> {
                            val attrs = (0 until reader.attributeCount).associate {
                                reader.getAttributeLocalName(it) to reader.getAttributeValue(it)
                            }
                            val group = RawGroup(
                                attrs["class"].orEmpty(), attrs["role"].orEmpty(),
                                attrs["rule"] ?: attrs["Rule"].orEmpty(), attrs["head"] == "true"
                            )
                            stack.last().children.add(group)
                            stack.add(group)
                        }
                        "w" -> {
                            check(attributes == null) { "Nested MACULA words are invalid." }
                            attributes = (0 until reader.attributeCount).associate {
                                reader.getAttributeLocalName(it) to reader.getAttributeValue(it)
                            }
                            text = StringBuilder()
                        }
                    }
                    EventType.TEXT, EventType.CDSECT -> if (attributes != null) text.append(reader.text)
                    EventType.END_ELEMENT -> when (reader.localName) {
                        "w" -> {
                            val attrs = checkNotNull(attributes)
                            val ref = attrs["ref"].orEmpty()
                            require(ref.contains('!') && ref.substringAfter('!').toIntOrNull() != null) {
                                "Invalid MACULA word reference: $ref"
                            }
                            require(references.add(ref)) { "Duplicate MACULA word reference: $ref" }
                            val role = attrs["role"].orEmpty().ifEmpty {
                                stack.asReversed().firstOrNull { it.role.isNotEmpty() }?.role.orEmpty()
                            }
                            val word = VerseWord(
                                surface = attrs["text"] ?: text.toString().trim(),
                                lemma = attrs["lemma"].orEmpty(),
                                pos = attrs["class"].orEmpty(), type = attrs["type"].orEmpty(),
                                grammaticalCase = attrs["case"].orEmpty(), mood = attrs["mood"].orEmpty(),
                                gloss = attrs["english"] ?: attrs["gloss"].orEmpty(), role = role,
                                mandarin = attrs["chinese"] ?: attrs["mandarin"].orEmpty(),
                                morphology = attrs["morph"].orEmpty(), reference = ref,
                                id = attrs["id"].orEmpty(), head = attrs["head"] == "true",
                                sourceGloss = attrs["gloss"].orEmpty()
                            )
                            allWords.getOrPut(ref.substringBefore('!')) { mutableListOf() }.add(word)
                            stack.last().children.add(RawWord(word))
                            attributes = null
                        }
                        "wg" -> {
                            check(stack.size > 1) { "Unexpected MACULA group end." }
                            stack.removeAt(stack.lastIndex)
                        }
                    }
                    else -> Unit
                }
            }
        } finally {
            reader.close()
        }
        require(allWords.isNotEmpty()) { "No MACULA words found." }
        val sorted = allWords.mapValues { (_, words) ->
            words.sortedBy { it.reference.substringAfter('!').toInt() }
        }
        val indices = sorted.mapValues { (_, words) -> words.mapIndexed { i, word -> word.reference to i }.toMap() }

        fun project(node: RawNode): Map<String, SyntaxNode> = when (node) {
            is RawWord -> {
                val ref = node.word.reference.substringBefore('!')
                mapOf(ref to SyntaxWord(indices.getValue(ref).getValue(node.word.reference)))
            }
            is RawGroup -> {
                val grouped = linkedMapOf<String, MutableList<SyntaxNode>>()
                for (child in node.children) for ((ref, part) in project(child)) {
                    grouped.getOrPut(ref) { mutableListOf() }.add(part)
                }
                grouped.mapValues { (_, children) ->
                    SyntaxGroup(node.category, node.role, node.rule, node.head, children, node !== root && grouped.size > 1)
                }
            }
        }
        val groups = project(root)
        return sorted.entries.sortedWith(compareBy({ it.key.substringAfter(' ').substringBefore(':').toInt() },
            { it.key.substringAfter(':').toInt() })).map { (ref, words) ->
            AudioVerse(ref, words, groups.getValue(ref) as SyntaxGroup)
        }
    }
}
