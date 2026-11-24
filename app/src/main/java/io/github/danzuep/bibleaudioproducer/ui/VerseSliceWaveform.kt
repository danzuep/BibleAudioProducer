package io.github.danzuep.bibleaudioproducer.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import io.github.danzuep.bibleaudioproducer.audio.VerseAudio
import kotlin.math.abs

class VerseSliceWaveform @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var samples = FloatArray(0)
    private var peaks = FloatArray(0)
    private var boundaries = emptyList<Int>()
    private var selected = 0
    private var dragging = -1
    private var visibleSamples = 1
    private var windowStart = 0
    private var dropTarget = -1
    /** Segments that hold no word, such as the audio before and after the speech. */
    var muted: Set<Int> = emptySet()
    var onSelect: (Int) -> Unit = {}
    var onBoundary: (Int, Int) -> Unit = { _, _ -> }

    fun segmentAt(x: Float): Int? {
        if (samples.isEmpty() || boundaries.size < 2 || width <= 0 || x < 0 || x > width) return null
        val sample = windowStart + (x / width * windowSize()).toInt().coerceAtMost(windowSize() - 1)
        return (0 until boundaries.lastIndex).firstOrNull { sample >= boundaries[it] && sample < boundaries[it + 1] }
    }

    fun showDropTarget(segment: Int?) {
        dropTarget = segment ?: -1
        invalidate()
    }

    fun show(samples: FloatArray, boundaries: List<Int>, selected: Int) {
        val newSamples = this.samples !== samples
        val newSelection = this.selected != selected
        if (newSamples) {
            peaks = if (samples.isEmpty()) FloatArray(0) else FloatArray(minOf(1024, samples.size)) { bucket ->
                val first = (bucket.toLong() * samples.size / minOf(1024, samples.size)).toInt()
                val last = ((bucket + 1L) * samples.size / minOf(1024, samples.size)).toInt()
                var peak = 0f
                for (i in first until last) peak = maxOf(peak, abs(samples[i]))
                peak
            }
        }
        this.samples = samples
        this.boundaries = boundaries
        this.selected = selected
        if (newSamples) visibleSamples = minOf(samples.size, VerseAudio.SAMPLE_RATE).coerceAtLeast(1)
        if (newSamples || newSelection) centerSelection()
        invalidate()
    }

    fun zoomBy(factor: Int) {
        visibleSamples = (windowSize() / factor).coerceAtLeast(1)
        centerSelection()
        invalidate()
    }

    fun zoomOut() {
        visibleSamples = (windowSize().toLong() * 2).coerceAtMost(samples.size.toLong()).toInt().coerceAtLeast(1)
        centerSelection()
        invalidate()
    }

    private fun windowSize() = visibleSamples.coerceAtLeast(1)

    private fun centerSelection() {
        if (samples.isEmpty() || selected !in 0 until boundaries.lastIndex) {
            windowStart = 0
            return
        }
        val center = (boundaries[selected].toLong() + boundaries[selected + 1]) / 2
        windowStart = (center - windowSize() / 2).toInt().coerceIn(0, samples.size - windowSize())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(23, 30, 41))
        if (samples.isEmpty() || boundaries.size < 2) return
        val visible = windowSize()
        val scale = width.toFloat() / visible
        fun position(sample: Int) = (sample - windowStart) * scale
        for (i in 0 until boundaries.lastIndex) {
            paint.color = color(i, if (i == selected) 170 else 75, i in muted)
            canvas.drawRect(position(boundaries[i]), 0f, position(boundaries[i + 1]), height.toFloat(), paint)
        }
        if (dropTarget in 0 until boundaries.lastIndex) {
            paint.color = Color.argb(110, 255, 255, 255)
            canvas.drawRect(position(boundaries[dropTarget]), 0f, position(boundaries[dropTarget + 1]), height.toFloat(), paint)
        }
        paint.color = Color.rgb(215, 230, 244)
        paint.strokeWidth = 1f
        for (x in 0 until width) {
            val first = windowStart + x.toLong() * visible / width
            val last = windowStart + (x + 1L) * visible / width
            val start = (first * peaks.size / samples.size).toInt().coerceAtMost(peaks.lastIndex)
            val end = (last * peaks.size / samples.size).toInt().coerceAtLeast(start + 1).coerceAtMost(peaks.size)
            var peak = 0f
            for (i in start until end) peak = maxOf(peak, peaks[i])
            val half = (peak.coerceAtMost(1f) * height * 0.43f).coerceAtLeast(1f)
            canvas.drawLine(x.toFloat(), height / 2f - half, x.toFloat(), height / 2f + half, paint)
        }
        for (i in 0 until boundaries.lastIndex) {
            if (i == 0) continue
            val x = position(boundaries[i])
            if (x < 0 || x > width) continue
            val active = i == selected || i == selected + 1
            paint.color = if (active) Color.WHITE else Color.rgb(150, 164, 180)
            paint.strokeWidth = if (active) 2f * resources.displayMetrics.density else 1f
            canvas.drawLine(x, 0f, x, height.toFloat(), paint)
            if (active) {
                val d = resources.displayMetrics.density
                canvas.drawRoundRect(x - 9 * d, height / 2f - 18 * d, x + 9 * d, height / 2f + 18 * d, 8 * d, 8 * d, paint)
                paint.color = Color.rgb(35, 44, 58)
                paint.strokeWidth = 2 * d
                canvas.drawLine(x - 2 * d, height / 2f - 7 * d, x - 2 * d, height / 2f + 7 * d, paint)
                canvas.drawLine(x + 2 * d, height / 2f - 7 * d, x + 2 * d, height / 2f + 7 * d, paint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || samples.isEmpty() || boundaries.size < 2 || width == 0) return false
        val sample = windowStart + (event.x.coerceIn(0f, width.toFloat()) / width * windowSize()).toInt()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val radius = 24 * resources.displayMetrics.density
                val closest = (1 until boundaries.lastIndex).minByOrNull {
                    abs((boundaries[it] - windowStart).toFloat() / windowSize() * width - event.x)
                }
                dragging = closest?.takeIf {
                    it in selected..selected + 1 &&
                        abs((boundaries[it] - windowStart).toFloat() / windowSize() * width - event.x) <= radius
                } ?: -1
                parent.requestDisallowInterceptTouchEvent(true)
                if (dragging < 0) {
                    segmentAt(event.x)?.let {
                        onSelect(it)
                        performClick()
                    }
                }
                return true
            }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                if (dragging >= 0) {
                    val low = boundaries[dragging - 1] + 1
                    // Later boundaries are pushed along, so only leave room for the segments after this one.
                    val high = samples.size - (boundaries.lastIndex - dragging)
                    val target = sample.coerceIn(low, high)
                    val radius = 80
                    val crossing = (maxOf(low, target - radius)..minOf(high, target + radius))
                        .filter { it > 0 && it < samples.size &&
                            (samples[it - 1] <= 0f && samples[it] >= 0f ||
                                samples[it - 1] >= 0f && samples[it] <= 0f) }
                        .minByOrNull { abs(it - target) } ?: target
                    onBoundary(dragging, crossing)
                }
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    dragging = -1
                    parent.requestDisallowInterceptTouchEvent(false)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                dragging = -1
                parent.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return false
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    companion object {
        fun color(index: Int, alpha: Int = 255, muted: Boolean = false): Int {
            if (muted) return (alpha shl 24) or 0x7A8594
            val colors = intArrayOf(0x3569B1, 0x008776, 0xA65728, 0x8754A4, 0xA83859, 0x5D7924)
            return (alpha shl 24) or colors[index % colors.size]
        }
    }
}
