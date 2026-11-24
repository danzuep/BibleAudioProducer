package io.github.danzuep.bibleaudioproducer.audio

/** Shared boundaries in chronological spoken order. */
class VerseBoundaries(initial: List<Int>) {
    private val points = initial.toMutableList()

    init {
        require(points.size >= 2 && points.first() >= 0 &&
            points.zipWithNext().all { (a, b) -> a < b }) { "Invalid verse boundaries." }
    }

    val count: Int get() = points.size - 1
    fun start(index: Int): Int = points[index]
    fun end(index: Int): Int = points[index + 1]
    fun snapshot(): List<Int> = points.toList()

    fun setRange(index: Int, start: Int, end: Int): Set<Int> {
        require(index in 0 until count && start < end &&
            (if (index == 0) start >= 0 else start > points[index - 1]) &&
            (if (index == count - 1) end == points.last() else end < points[index + 2])) {
            "The selection must remain between its neighboring boundaries."
        }
        val changed = mutableSetOf<Int>()
        if (points[index] != start) {
            points[index] = start
            if (index > 0) changed.add(index - 1)
            changed.add(index)
        }
        if (points[index + 1] != end) {
            points[index + 1] = end
            changed.add(index)
            changed.add(index + 1)
        }
        return changed
    }

    fun move(boundary: Int, sample: Int): Set<Int> {
        require(boundary in 0 until points.lastIndex) { "The recording end cannot be moved." }
        require((if (boundary == 0) sample >= 0 else sample > points[boundary - 1]) &&
            sample < points[boundary + 1]) {
            "A boundary must stay between its neighboring boundaries."
        }
        if (points[boundary] == sample) return emptySet()
        points[boundary] = sample
        return if (boundary == 0) setOf(0) else setOf(boundary - 1, boundary)
    }

    /** Split a segment in two at [sample]; later segment indexes shift up by one. */
    fun split(index: Int, sample: Int) {
        require(index in 0 until count && sample > points[index] && sample < points[index + 1]) {
            "A split must fall inside its segment."
        }
        points.add(index + 1, sample)
    }

    /** Remove the boundary between segments [boundary] - 1 and [boundary]; later indexes shift down by one. */
    fun merge(boundary: Int) {
        require(boundary in 1 until points.lastIndex) { "Only an inner boundary can be merged." }
        points.removeAt(boundary)
    }

    /** Replace consecutive inner boundaries starting at [boundary], keeping their number. */
    fun replaceFrom(boundary: Int, replacement: List<Int>): Set<Int> {
        val after = boundary + replacement.size
        require(boundary in 1 until points.lastIndex && replacement.isNotEmpty() && after < points.size &&
            replacement.first() > points[boundary - 1] && replacement.last() < points[after] &&
            replacement.zipWithNext().all { (a, b) -> a < b }) {
            "The replacement boundaries must stay in order between their neighbors."
        }
        val changed = mutableSetOf<Int>()
        replacement.forEachIndexed { offset, sample ->
            val point = boundary + offset
            if (points[point] != sample) {
                points[point] = sample
                changed.add(point - 1)
                changed.add(point)
            }
        }
        return changed
    }
}
