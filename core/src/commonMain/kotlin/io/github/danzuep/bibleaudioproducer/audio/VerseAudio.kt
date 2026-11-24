package io.github.danzuep.bibleaudioproducer.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// Audio assembly follows MaculaScripta's Apache-2.0 verse-audio.js.
object VerseAudio {
    const val SAMPLE_RATE = 16000

    private const val FRAME = SAMPLE_RATE / 100
    private const val ONSET_FRAMES = 4
    private const val LEAD_IN = SAMPLE_RATE / 25
    private const val TAIL_OUT = SAMPLE_RATE / 12

    private fun frameLevels(samples: FloatArray): DoubleArray = DoubleArray((samples.size + FRAME - 1) / FRAME) { frame ->
        val start = frame * FRAME
        val end = minOf(start + FRAME, samples.size)
        var sum = 0.0
        for (i in start until end) sum += samples[i].toDouble() * samples[i]
        sqrt(sum / (end - start))
    }

    /**
     * Find where speech begins and ends. Short clicks (such as a button tap) and steady background noise
     * are ignored: speech must stay above the noise floor for several consecutive frames, and the edges
     * are widened to include soft consonants. Returns null when the recording is silent.
     */
    fun speechRegion(samples: FloatArray): Pair<Int, Int>? {
        val levels = frameLevels(samples)
        val peak = levels.maxOrNull() ?: 0.0
        if (peak == 0.0) return null
        val floor = levels.sorted()[levels.size / 5]
        val noise = if (floor < peak * 0.25) floor * 3 else 0.0
        val threshold = maxOf(peak * 0.05, noise)
        val soft = threshold * 0.4
        val needed = minOf(ONSET_FRAMES, levels.size)
        fun loud(frame: Int) = (0 until needed).all { levels[frame + it] >= threshold }
        val first = (0..levels.size - needed).firstOrNull(::loud) ?: return null
        val last = (levels.size - needed downTo first).first(::loud) + needed - 1
        var onset = first
        while (onset > 0 && first - onset < 10 && levels[onset - 1] >= soft) onset--
        var offset = last
        while (offset < levels.lastIndex && offset - last < 15 && levels[offset + 1] >= soft) offset++
        return maxOf(0, onset * FRAME - LEAD_IN) to minOf(samples.size, (offset + 1) * FRAME + TAIL_OUT)
    }

    /**
     * Suggest contiguous boundaries for [weights].size cards, in chronological order. The result starts
     * with 0 and ends with the sample count; the first and last segments hold the silence around the speech
     * and the segments between them follow the weights, which only express how long each card is likely to be.
     * The suggestions always require review.
     */
    fun suggestBoundaries(samples: FloatArray, weights: List<Double>): List<Int> {
        val count = weights.size
        require(count > 0 && samples.size >= count + 2) { "Recording is too short to split." }
        require(samples.all { it.isFinite() }) { "The recording contains invalid audio samples." }
        val (found, foundEnd) = speechRegion(samples) ?: (0 to samples.size)
        val start = found.coerceIn(1, samples.size - 1 - count)
        val end = foundEnd.coerceIn(start + count, samples.size - 1)
        return listOf(0) + splitRegion(samples, start, end, weights) + samples.size
    }

    fun suggestBoundaries(samples: FloatArray, wordCount: Int): List<Int> =
        suggestBoundaries(samples, List(wordCount) { 1.0 })

    /** Split [start, end) into one segment per weight, preferring quiet points; returns weights.size + 1 points. */
    fun splitRegion(samples: FloatArray, start: Int, end: Int, weights: List<Double>): List<Int> {
        val count = weights.size
        require(count > 0 && start >= 0 && end <= samples.size && end - start >= count) { "Region is too short to split." }
        require(weights.all { it > 0.0 }) { "Weights must be positive." }
        val levels = frameLevels(samples)
        val total = weights.sum()
        val points = mutableListOf(start)
        var cumulative = 0.0
        for (i in 1 until count) {
            cumulative += weights[i - 1]
            val target = start + ((end - start) * cumulative / total).toLong()
            val radius = ((end - start) * minOf(weights[i - 1], weights[i]) / total / 3).toInt()
            val minimum = points.last() + 1
            val maximum = end - (count - i)
            val first = maxOf(minimum, (target - radius).toInt())
            val last = minOf(maximum, (target + radius).toInt())
            var best = target.toInt().coerceIn(minimum, maximum)
            var bestScore = Double.POSITIVE_INFINITY
            for (frame in (first + FRAME - 1) / FRAME..last / FRAME) {
                val point = frame * FRAME
                if (frame !in levels.indices || point !in minimum..maximum) continue
                val distance = abs(point - target).toDouble() / radius.coerceAtLeast(1)
                val score = levels[frame] * levels[frame] + distance * 1e-8
                if (score < bestScore) {
                    bestScore = score
                    best = point
                }
            }
            points.add(best)
        }
        points.add(end)
        return points
    }

    /** Copy a non-destructive selection with short fades to reduce clicks. */
    fun slice(samples: FloatArray, start: Int, end: Int): FloatArray {
        require(start >= 0 && end <= samples.size && start < end) { "Invalid audio selection." }
        val result = samples.copyOfRange(start, end)
        val fade = minOf(SAMPLE_RATE / 200, result.size / 2)
        for (i in 0 until fade) {
            val gain = i.toFloat() / fade
            result[i] *= gain
            result[result.lastIndex - i] *= gain
        }
        return result
    }

    fun assemble(clips: List<FloatArray>): FloatArray {
        require(clips.isNotEmpty()) { "No recordings are available for this verse." }
        val parts = clips.map { trimSilence(normalize(it)) }
        require(parts.all { it.isNotEmpty() }) { "A recording contains no audible audio." }
        val fades = parts.zipWithNext { a, b -> minOf(480, a.size / 2, b.size / 2) }
        val size = parts.sumOf { it.size.toLong() } - fades.sumOf { it.toLong() }
        require(size <= Int.MAX_VALUE) { "The verse is too long to render." }
        val result = FloatArray(size.toInt())
        var offset = 0
        parts.forEachIndexed { index, samples ->
            val into = fades.getOrElse(index - 1) { 0 }
            val out = fades.getOrElse(index) { 0 }
            for (i in samples.indices) {
                val gain = when {
                    i < into -> sin(PI / 2 * i / into)
                    i >= samples.size - out -> cos(PI / 2 * (i - (samples.size - out)) / out)
                    else -> 1.0
                }
                result[offset + i] += (samples[i] * gain).toFloat()
            }
            offset += samples.size - out
        }
        return result
    }

    fun encodeWav(samples: FloatArray): ByteArray {
        require(samples.size <= (Int.MAX_VALUE - 44) / 2) { "The verse is too long to export." }
        val bytes = ByteArray(44 + samples.size * 2)
        fun text(offset: Int, value: String) {
            value.forEachIndexed { index, char -> bytes[offset + index] = char.code.toByte() }
        }
        fun integer(offset: Int, value: Int, count: Int) {
            repeat(count) { bytes[offset + it] = (value ushr (8 * it)).toByte() }
        }
        text(0, "RIFF")
        integer(4, bytes.size - 8, 4)
        text(8, "WAVEfmt ")
        integer(16, 16, 4)
        integer(20, 1, 2)
        integer(22, 1, 2)
        integer(24, SAMPLE_RATE, 4)
        integer(28, SAMPLE_RATE * 2, 4)
        integer(32, 2, 2)
        integer(34, 16, 2)
        text(36, "data")
        integer(40, samples.size * 2, 4)
        samples.forEachIndexed { index, value ->
            require(value.isFinite()) { "The verse contains invalid audio samples." }
            integer(44 + index * 2, (value.coerceIn(-1f, 1f) * 32767).roundToInt(), 2)
        }
        return bytes
    }

    private fun normalize(samples: FloatArray): FloatArray {
        var sum = 0.0
        for (sample in samples) {
            require(sample.isFinite()) { "A recording contains invalid audio samples." }
            sum += sample.toDouble() * sample
        }
        val rms = sqrt(sum / samples.size.coerceAtLeast(1))
        if (rms == 0.0) return samples
        val gain = minOf(0.12 / rms, 8.0)
        return FloatArray(samples.size) { (samples[it] * gain).toFloat().coerceIn(-1f, 1f) }
    }

    private fun trimSilence(samples: FloatArray): FloatArray {
        val limit = (samples.maxOfOrNull { abs(it) } ?: 0f) * 0.02f
        var start = 0
        var end = samples.size
        while (start < end && abs(samples[start]) <= limit) start++
        while (end > start && abs(samples[end - 1]) <= limit) end--
        if (start == end) return FloatArray(0)
        return samples.copyOfRange((start - 640).coerceAtLeast(0), (end + 640).coerceAtMost(samples.size))
    }
}
