package io.github.danzuep.bibleaudioproducer.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VerseSplitTest {
    @Test
    fun movingSharedBoundaryUpdatesBothNeighborsAndRejectsCrossings() {
        val boundaries = VerseBoundaries(listOf(0, 100, 200, 300))
        assertEquals(setOf(0, 1), boundaries.move(1, 120))
        assertEquals(120, boundaries.end(0))
        assertEquals(120, boundaries.start(1))
        assertEquals(emptySet(), boundaries.move(1, 120))
        assertFailsWith<IllegalArgumentException> { boundaries.move(1, 200) }
        assertFailsWith<IllegalArgumentException> { boundaries.move(0, -1) }
        assertEquals(listOf(0, 120, 200, 300), boundaries.snapshot())
        assertEquals(setOf(0, 1, 2), boundaries.setRange(1, 130, 220))
        assertEquals(listOf(0, 130, 220, 300), boundaries.snapshot())
        assertFailsWith<IllegalArgumentException> { boundaries.setRange(1, 220, 220) }
        assertFailsWith<IllegalArgumentException> { boundaries.setRange(0, -1, 100) }
        assertEquals(listOf(0, 130, 220, 300), boundaries.snapshot())
    }

    @Test
    fun leadingAndTrailingSilenceBecomeTheirOwnSegments() {
        val samples = FloatArray(32000) { if (it in 16000 until 24000) 0.4f else 0f }
        val suggested = VerseAudio.suggestBoundaries(samples, 2)
        assertEquals(5, suggested.size)
        assertEquals(0, suggested.first())
        assertEquals(15360, suggested[1])
        assertTrue(suggested[3] in 24000..26000)
        assertEquals(32000, suggested.last())
        assertTrue(suggested.zipWithNext().all { (a, b) -> a < b })
        assertEquals(0f, samples[0])
        assertEquals(0.4f, samples[16000])
        val silent = VerseAudio.suggestBoundaries(FloatArray(1000), 2)
        assertEquals(listOf(0, 1), silent.take(2))
        assertEquals(listOf(999, 1000), silent.takeLast(2))
    }

    @Test
    fun startDetectionIgnoresClicksAndBackgroundNoise() {
        val samples = FloatArray(48000) { if (it % 2 == 0) 0.01f else -0.01f }
        for (i in 4000 until 4100) samples[i] = if (i % 2 == 0) 0.9f else -0.9f
        for (i in 20000 until 40000) samples[i] = if (i % 40 < 20) 0.3f else -0.3f
        val (start, end) = checkNotNull(VerseAudio.speechRegion(samples))
        assertTrue(start in 18000..19400, "start $start")
        assertTrue(end in 40000..42000, "end $end")
        assertEquals(null, VerseAudio.speechRegion(FloatArray(500)))
    }

    @Test
    fun regionSplitFollowsWeights() {
        val samples = FloatArray(10000) { 0.5f }
        val points = VerseAudio.splitRegion(samples, 1000, 9000, listOf(1.0, 3.0))
        assertEquals(listOf(1000, 9000), listOf(points.first(), points.last()))
        assertTrue(points[1] in 2900..3100, "split ${points[1]}")
    }

    @Test
    fun boundariesCoverAudioInOrderAndPreferQuietGaps() {
        val samples = FloatArray(48000) { 0.5f }
        for (i in 15840 until 16000) samples[i] = 0f
        for (i in 31840 until 32000) samples[i] = 0f
        val boundaries = VerseAudio.suggestBoundaries(samples, 3)
        assertEquals(listOf(0, 1, 15840, 31840, 47999, 48000), boundaries)
    }

    @Test
    fun splitMergeAndReplaceKeepBoundariesOrdered() {
        val boundaries = VerseBoundaries(listOf(0, 100, 200, 300, 400))
        boundaries.split(1, 150)
        assertEquals(listOf(0, 100, 150, 200, 300, 400), boundaries.snapshot())
        assertFailsWith<IllegalArgumentException> { boundaries.split(1, 150) }
        assertEquals(setOf(2, 3, 4), boundaries.replaceFrom(3, listOf(250, 350)))
        assertEquals(listOf(0, 100, 150, 250, 350, 400), boundaries.snapshot())
        assertFailsWith<IllegalArgumentException> { boundaries.replaceFrom(3, listOf(250, 400)) }
        assertFailsWith<IllegalArgumentException> { boundaries.replaceFrom(2, listOf(90)) }
        boundaries.merge(2)
        assertEquals(listOf(0, 100, 250, 350, 400), boundaries.snapshot())
        assertFailsWith<IllegalArgumentException> { boundaries.merge(0) }
    }

    @Test
    fun rejectsTooShortOrInvalidAudio() {
        assertFailsWith<IllegalArgumentException> { VerseAudio.suggestBoundaries(FloatArray(2), 3) }
        assertFailsWith<IllegalArgumentException> { VerseAudio.suggestBoundaries(floatArrayOf(Float.NaN), 1) }
    }

    @Test
    fun sliceFadesBothEndsWithoutChangingSource() {
        val source = FloatArray(200) { 1f }
        val slice = VerseAudio.slice(source, 10, 190)
        assertEquals(180, slice.size)
        assertEquals(0f, slice.first())
        assertEquals(0f, slice.last())
        assertEquals(1f, slice[80])
        assertTrue(source.all { it == 1f })
        assertFailsWith<IllegalArgumentException> { VerseAudio.slice(source, 10, 10) }
    }
}
