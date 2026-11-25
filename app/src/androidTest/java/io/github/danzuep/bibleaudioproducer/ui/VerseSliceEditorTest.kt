package io.github.danzuep.bibleaudioproducer.ui

import android.content.Context
import android.Manifest
import android.graphics.Rect
import android.view.MotionEvent
import android.view.InputDevice
import android.view.View
import android.os.SystemClock
import android.os.ParcelFileDescriptor
import android.widget.EditText
import android.widget.Button
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.ChipGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.matcher.RootMatchers.isDialog
import io.github.danzuep.bibleaudioproducer.audio.VerseAudio
import io.github.danzuep.bibleaudioproducer.R
import io.github.danzuep.bibleaudioproducer.audio.VerseBoundaries
import io.github.danzuep.bibleaudioproducer.data.AudioProjectRepository
import io.github.danzuep.bibleaudioproducer.workbench.MaculaXml
import io.github.danzuep.bibleaudioproducer.workbench.VerseRecordingWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class VerseSliceEditorTest {
    @Test
    fun draggingScriptChipsReordersRecordingCards() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val words = context.assets.open("books/JHN.xml").bufferedReader().use {
            MaculaXml.parse(it.readText()).first().words.filter { !it.disabled(emptySet()) }.take(3)
        }.mapIndexed { index, word -> VerseRecordingWord(word.copy(gloss = "word$index"), index) }
        var reordered = emptyList<Int>()
        ActivityScenario.launch(WorkbenchActivity::class.java).use { scenario ->
            lateinit var editor: VerseTakeEditor
            lateinit var root: View
            scenario.onActivity { activity ->
                editor = VerseTakeEditor(activity, AudioProjectRepository(File(context.cacheDir, "script-drag-test")),
                    words, { true }, { reordered = it }, {}, {})
                root = editor.view()
                activity.setContentView(root)
            }
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.waitForIdleSync()
            val from = Rect()
            val to = Rect()
            scenario.onActivity {
                val chips = root.findViewById<ChipGroup>(R.id.clip_tokens)
                assertTrue(chips.getChildAt(0).getGlobalVisibleRect(from))
                assertTrue(chips.getChildAt(1).getGlobalVisibleRect(to))
            }
            val downTime = SystemClock.uptimeMillis()
            fun touch(action: Int, x: Float, y: Float) {
                val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true))
                event.recycle()
            }
            touch(MotionEvent.ACTION_DOWN, from.exactCenterX(), from.exactCenterY())
            SystemClock.sleep(900)
            for (step in 1..10) {
                val fraction = step / 10f
                touch(MotionEvent.ACTION_MOVE,
                    from.exactCenterX() + (to.exactCenterX() - from.exactCenterX()) * fraction,
                    from.exactCenterY() + (to.exactCenterY() - from.exactCenterY()) * fraction)
                SystemClock.sleep(30)
            }
            touch(MotionEvent.ACTION_UP, to.exactCenterX(), to.exactCenterY())
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                assertEquals(listOf(1, 0, 2), reordered)
                editor.dispose()
            }
        }
    }

    @Test
    fun waveformStartsAtOneSecondAndOnlySelectedBoundariesDrag() {
        ActivityScenario.launch(WorkbenchActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val waveform = VerseSliceWaveform(activity)
                val parent = android.widget.FrameLayout(activity)
                parent.addView(waveform)
                waveform.layout(0, 0, 400, 120)
                val samples = FloatArray(80000) { 0.1f }
                val boundaries = listOf(0, 8000, 16000, 32000, 80000)
                waveform.show(samples, boundaries, 0)
                assertEquals(1, waveform.segmentAt(360f))
                waveform.zoomOut()
                assertEquals(2, waveform.segmentAt(360f))
                var selected = -1
                var moved = -1
                waveform.onSelect = { selected = it }
                waveform.onBoundary = { boundary, _ -> moved = boundary }
                fun touch(action: Int, x: Float) {
                    val event = MotionEvent.obtain(0, 0, action, x, 60f, 0)
                    waveform.dispatchTouchEvent(event)
                    event.recycle()
                }
                touch(MotionEvent.ACTION_DOWN, 200f)
                touch(MotionEvent.ACTION_MOVE, 220f)
                touch(MotionEvent.ACTION_UP, 220f)
                assertEquals(2, selected)
                assertEquals(-1, moved)
                touch(MotionEvent.ACTION_DOWN, 100f)
                touch(MotionEvent.ACTION_MOVE, 110f)
                touch(MotionEvent.ACTION_UP, 110f)
                assertEquals(1, moved)
                waveform.show(samples, boundaries, 3)
                assertEquals(3, waveform.segmentAt(200f))
            }
        }
    }

    @Test
    fun recordingShowsLiveWaveformAndStopsSamplingInBackground() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val grant = instrumentation.uiAutomation.executeShellCommand(
            "pm grant ${context.packageName} ${Manifest.permission.RECORD_AUDIO}"
        )
        ParcelFileDescriptor.AutoCloseInputStream(grant).bufferedReader().use { it.readText() }
        val words = context.assets.open("books/JHN.xml").bufferedReader().use {
            MaculaXml.parse(it.readText()).first().words.filter { !it.disabled(emptySet()) }.take(3)
        }.mapIndexed { index, word -> VerseRecordingWord(word, index) }
        ActivityScenario.launch(WorkbenchActivity::class.java).use { scenario ->
            lateinit var editor: VerseTakeEditor
            lateinit var root: View
            scenario.onActivity { activity ->
                editor = VerseTakeEditor(
                    activity, AudioProjectRepository(File(context.cacheDir, "slice-live-test")),
                    words, { true }, {}, {}, {}
                )
                root = editor.view()
                activity.setContentView(root)
                root.findViewById<Button>(R.id.clip_record).performClick()
                assertEquals(View.VISIBLE, root.findViewById<View>(R.id.clip_live_waveform).visibility)
            }
            SystemClock.sleep(500)
            scenario.onActivity {
                val field = VerseTakeEditor::class.java.getDeclaredField("levels").apply { isAccessible = true }
                val levels = field.get(editor) as List<*>
                assertTrue("Microphone levels were not sampled", levels.size >= 2)
                editor.stopForBackground()
                assertEquals(View.GONE, root.findViewById<View>(R.id.clip_live_waveform).visibility)
                editor.dispose()
            }
        }
    }

    @Test
    fun droppingWordMovesCardsAndInvalidatesAffectedReviews() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val words = context.assets.open("books/JHN.xml").bufferedReader().use {
            MaculaXml.parse(it.readText()).first().words.filter { !it.disabled(emptySet()) }.take(3)
        }.mapIndexed { index, word -> VerseRecordingWord(word, index) }
        ActivityScenario.launch(WorkbenchActivity::class.java).use { scenario ->
            lateinit var editor: VerseTakeEditor
            lateinit var points: VerseBoundaries
            lateinit var waveform: VerseSliceWaveform
            lateinit var root: View
            scenario.onActivity { activity ->
                editor = VerseTakeEditor(
                    activity, AudioProjectRepository(File(context.cacheDir, "slice-swap-test")),
                    words, { true }, {}, {}, {}
                )
                root = editor.view()
                activity.setContentView(root)
                fun field(name: String) = VerseTakeEditor::class.java.getDeclaredField(name).apply { isAccessible = true }
                fun invoke(name: String) = VerseTakeEditor::class.java.getDeclaredMethod(name)
                    .apply { isAccessible = true }.invoke(editor)
                val samples = FloatArray(2400) { 0.1f }
                points = VerseBoundaries(listOf(0, 300, 800, 1600, 2100, 2400))
                field("samples").set(editor, samples)
                field("boundaries").set(editor, points)
                invoke("showSelection")
                invoke("refresh")
                @Suppress("UNCHECKED_CAST")
                val reviewed = field("reviewed").get(editor) as MutableSet<Int>
                reviewed.addAll(listOf(1, 2, 3))
                waveform = root.findViewById(R.id.clip_waveform)
            }
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.waitForIdleSync()
            var startX = 0f
            var startY = 0f
            var endX = 0f
            var endY = 0f
            scenario.onActivity {
                val recycler = root.findViewById<RecyclerView>(R.id.clip_cards)
                val scroll = root.findViewById<androidx.core.widget.NestedScrollView>(R.id.clip_scroll)
                scroll.scrollTo(0, (recycler.top - scroll.height + 80).coerceAtLeast(0))
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                val recycler = root.findViewById<RecyclerView>(R.id.clip_cards)
                val card = checkNotNull(recycler.findViewHolderForAdapterPosition(1)).itemView
                val waveLocation = IntArray(2)
                waveform.getLocationOnScreen(waveLocation)
                val cardVisible = Rect()
                val waveVisible = Rect()
                assertTrue(card.getGlobalVisibleRect(cardVisible))
                assertTrue(waveform.getGlobalVisibleRect(waveVisible))
                startX = cardVisible.left + cardVisible.width() / 4f
                startY = cardVisible.top + minOf(cardVisible.height() / 2f, 20f)
                endX = waveLocation[0] + waveform.width * 5f / 6f
                endY = waveVisible.centerY().toFloat()
            }
            val downTime = SystemClock.uptimeMillis()
            fun touch(action: Int, x: Float, y: Float) {
                val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true))
                event.recycle()
            }
            touch(MotionEvent.ACTION_DOWN, startX, startY)
            SystemClock.sleep(900)
            for (step in 1..10) {
                val fraction = step / 10f
                touch(MotionEvent.ACTION_MOVE, startX + (endX - startX) * fraction, startY + (endY - startY) * fraction)
                SystemClock.sleep(30)
            }
            touch(MotionEvent.ACTION_UP, endX, endY)
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                fun field(name: String) = VerseTakeEditor::class.java.getDeclaredField(name).apply { isAccessible = true }
                @Suppress("UNCHECKED_CAST")
                val reviewed = field("reviewed").get(editor) as MutableSet<Int>
                @Suppress("UNCHECKED_CAST")
                val order = field("words").get(editor) as List<VerseRecordingWord>
                assertEquals(listOf(1, 2, 0), order.map { it.originalIndex })
                assertEquals(listOf(0, 300, 800, 1600, 2100, 2400), points.snapshot())
                assertTrue(reviewed.isEmpty())
                assertEquals(1600, points.start(3))
                root.findViewById<RecyclerView>(R.id.clip_cards).scrollToPosition(2)
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                fun field(name: String) = VerseTakeEditor::class.java.getDeclaredField(name).apply { isAccessible = true }
                val recycler = root.findViewById<RecyclerView>(R.id.clip_cards)
                val card = checkNotNull(recycler.findViewHolderForAdapterPosition(2)).itemView
                val assign = card.findViewById<Button>(R.id.word_assign)
                assertTrue(assign.isEnabled)
                val move = VerseTakeEditor::class.java.getDeclaredMethod(
                    "assignWord", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
                ).apply { isAccessible = true }
                assertEquals(true, move.invoke(editor, 0, 2))
                @Suppress("UNCHECKED_CAST")
                val order = field("words").get(editor) as List<VerseRecordingWord>
                assertEquals(listOf(1, 0, 2), order.map { it.originalIndex })
                @Suppress("UNCHECKED_CAST")
                val reviewed = field("reviewed").get(editor) as MutableSet<Int>
                assertTrue(reviewed.isEmpty())
                editor.dispose()
            }
        }
    }

    @Test
    fun draggingABoundaryRightPushesAndRecomputesLaterBoundaries() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val words = context.assets.open("books/JHN.xml").bufferedReader().use {
            MaculaXml.parse(it.readText()).first().words.filter { !it.disabled(emptySet()) }.take(3)
        }.mapIndexed { index, word -> VerseRecordingWord(word, index) }
        assertEquals(3, words.size)
        var reordered = emptyList<Int>()
        ActivityScenario.launch(WorkbenchActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val editor = VerseTakeEditor(
                    activity, AudioProjectRepository(File(context.cacheDir, "slice-editor-test")),
                    words, { true }, { reordered = it }, {}, {}
                )
                editor.view()
                fun field(name: String) = VerseTakeEditor::class.java.getDeclaredField(name).apply { isAccessible = true }
                fun invoke(name: String) = VerseTakeEditor::class.java.getDeclaredMethod(name)
                    .apply { isAccessible = true }.invoke(editor)
                val samples = FloatArray(1600) { if (it % 2 == 0) 0.1f else -0.1f }
                val boundaries = VerseBoundaries(listOf(0, 100, 400, 900, 1300, 1600))
                field("samples").set(editor, samples)
                field("boundaries").set(editor, boundaries)
                invoke("showSelection")
                invoke("refresh")
                val waveform = field("waveform").get(editor) as VerseSliceWaveform
                waveform.layout(0, 0, 400, 120)
                fun touch(action: Int, x: Float) {
                    val event = MotionEvent.obtain(0, 0, action, x, 60f, 0)
                    waveform.dispatchTouchEvent(event)
                    event.recycle()
                }
                touch(MotionEvent.ACTION_DOWN, 100f)
                touch(MotionEvent.ACTION_MOVE, 120f)
                touch(MotionEvent.ACTION_UP, 120f)
                assertEquals(boundaries.end(1), boundaries.start(2))
                assertTrue(boundaries.end(1) in 470..490)
                assertEquals(1300, boundaries.start(4))
                touch(MotionEvent.ACTION_DOWN, 120f)
                touch(MotionEvent.ACTION_MOVE, 200f)
                touch(MotionEvent.ACTION_UP, 200f)
                val pushed = boundaries.snapshot()
                assertTrue(pushed[2] in 790..810)
                assertTrue(pushed[3] > pushed[2] && pushed[3] < 1300)
                assertEquals(listOf(0, 100), pushed.take(2))
                assertEquals(listOf(1300, 1600), pushed.takeLast(2))
                touch(MotionEvent.ACTION_DOWN, pushed[2] / 4f)
                touch(MotionEvent.ACTION_MOVE, 5f)
                touch(MotionEvent.ACTION_UP, 5f)
                val clamped = boundaries.snapshot()
                assertEquals(101, clamped[2])
                VerseTakeEditor::class.java.getDeclaredMethod("moveCard", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                    .apply { isAccessible = true }.invoke(editor, 1, 2)
                assertEquals(listOf(1, 0, 2), reordered)
                assertEquals(clamped, boundaries.snapshot())
                editor.dispose()
            }
        }
    }

    @Test
    fun cardOrderMatchesAudioAndGuideAfterReordering() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val verseWords = context.assets.open("books/JHN.xml").bufferedReader().use {
            MaculaXml.parse(it.readText()).first().words.filter { !it.disabled(emptySet()) }.take(3)
        }
        val words = verseWords.mapIndexed { index, word ->
            VerseRecordingWord(word.copy(gloss = "english$index", mandarin = if (index == 1) "" else "chinese$index"), index * 2)
        }
        var reordered = emptyList<Int>()
        ActivityScenario.launch(WorkbenchActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val editor = VerseTakeEditor(
                    activity, AudioProjectRepository(File(context.cacheDir, "slice-order-test")),
                    words, { true }, { reordered = it }, {}, {}
                )
                editor.view()
                fun field(name: String) = VerseTakeEditor::class.java.getDeclaredField(name).apply { isAccessible = true }
                fun invoke(name: String) = VerseTakeEditor::class.java.getDeclaredMethod(name)
                    .apply { isAccessible = true }.invoke(editor)
                fun move(from: Int, to: Int) =
                    VerseTakeEditor::class.java.getDeclaredMethod("moveCard", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                        .apply { isAccessible = true }.invoke(editor, from, to)
                val guide = field("tokens").get(editor) as ChipGroup
                fun guideText() = (0 until guide.childCount).joinToString(" · ") {
                    (guide.getChildAt(it) as TextView).text.toString()
                }
                move(1, 3)
                assertEquals(listOf(2, 4, 0), reordered)
                assertEquals("english1 · english2 · english0", guideText())
                VerseTakeEditor::class.java.getDeclaredMethod("selectCard", Int::class.javaPrimitiveType)
                    .apply { isAccessible = true }.invoke(editor, 1)
                val boundaries = VerseBoundaries(listOf(0, 200, 600, 1100, 1400, 1600))
                field("samples").set(editor, FloatArray(1600))
                field("boundaries").set(editor, boundaries)
                invoke("showSelection")
                invoke("refresh")
                move(1, 3)
                assertEquals(listOf(4, 0, 2), reordered)
                assertEquals(listOf(0, 200, 600, 1100, 1400, 1600), boundaries.snapshot())
                assertEquals("english2 · english0 · english1", guideText())
                assertEquals(1100, boundaries.start(3))
                assertEquals(1400, boundaries.end(3))
                field("guideMode").setInt(editor, 1)
                field("lastGlossMode").setInt(editor, 1)
                invoke("updateGuide")
                assertEquals("chinese2 · chinese0 · ·", guideText())
                val custom = field("customGuide").get(editor) as EditText
                field("guideMode").setInt(editor, 4)
                invoke("beginCustomGuide")
                invoke("updateGuide")
                assertEquals("chinese2 chinese0 ·", custom.text.toString())
                assertEquals(View.VISIBLE, (field("customRow").get(editor) as View).visibility)
                assertEquals(View.GONE, guide.visibility)
                custom.setText("chinese2 new chinese0 ·")
                invoke("commitCustomGuide")
                assertEquals(View.VISIBLE, guide.visibility)
                assertEquals("chinese2 · new · chinese0 · · · ✎", guideText())
                assertTrue(guide.getChildAt(0).isClickable)
                assertTrue(!guide.getChildAt(1).isClickable)
                assertTrue(guide.getChildAt(2).isClickable)
                field("guideMode").setInt(editor, 0)
                invoke("updateGuide")
                @Suppress("UNCHECKED_CAST")
                val reviewed = field("reviewed").get(editor) as MutableSet<Int>
                reviewed.addAll(listOf(1, 2))
                guide.getChildAt(1).performClick()
                assertEquals(2, field("index").getInt(editor))
                assertEquals(setOf(1, 2), reviewed)
                assertEquals(1100, boundaries.end(2))
                invoke("advance")
                assertEquals(3, field("index").getInt(editor))
                assertEquals(setOf(1, 2), reviewed)
                editor.dispose()
            }
        }
    }

    @Test
    fun duplicateSurfacesRequireReplacementAndKeepSeparateTakeSlices() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val word = context.assets.open("books/JHN.xml").bufferedReader().use {
            MaculaXml.parse(it.readText()).first().words.first { !it.disabled(emptySet()) }
        }
        val directory = File(context.cacheDir, "slice-duplicate-test")
        directory.deleteRecursively()
        val repository = AudioProjectRepository(directory)
        val clip = repository.clipFile(word.surface)
        check(clip.parentFile!!.mkdirs() || clip.parentFile!!.isDirectory)
        val original = VerseAudio.encodeWav(FloatArray(800))
        val samples = FloatArray(1600) { if (it < 800) 0.1f else 0.5f }
        clip.writeBytes(original)
        lateinit var editor: VerseTakeEditor
        fun field(name: String) = VerseTakeEditor::class.java.getDeclaredField(name).apply { isAccessible = true }
        fun invoke(name: String) = VerseTakeEditor::class.java.getDeclaredMethod(name)
            .apply { isAccessible = true }.invoke(editor)
        fun reviewedCount(): Int {
            @Suppress("UNCHECKED_CAST")
            return (field("reviewed").get(editor) as Set<Int>).size
        }
        try {
            ActivityScenario.launch(WorkbenchActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    editor = VerseTakeEditor(
                        activity, repository, listOf(VerseRecordingWord(word, 0), VerseRecordingWord(word, 2)),
                        { true }, {}, {}, {}
                    )
                    editor.view()
                    field("samples").set(editor, samples)
                    field("boundaries").set(editor, VerseBoundaries(listOf(0, 100, 800, 1500, 1600)))
                    invoke("showSelection")
                    invoke("refresh")
                    VerseTakeEditor::class.java.getDeclaredMethod("selectCard", Int::class.javaPrimitiveType)
                        .apply { isAccessible = true }.invoke(editor, 2)
                    assertEquals(0, reviewedCount())
                    (field("next").get(editor) as Button).performClick()
                }
                fun waitForActivityFocus() {
                    val deadline = System.currentTimeMillis() + 10_000
                    var focused = false
                    while (!focused && System.currentTimeMillis() < deadline) {
                        scenario.onActivity { focused = it.window.decorView.hasWindowFocus() }
                        if (!focused) Thread.sleep(50)
                    }
                    assertTrue("Activity did not regain focus after replacement dialog", focused)
                }
                onView(withText(android.R.string.cancel)).inRoot(isDialog()).perform(click())
                waitForActivityFocus()
                scenario.onActivity {
                    assertEquals(0, reviewedCount())
                    assertArrayEquals(original, clip.readBytes())
                    (field("next").get(editor) as Button).performClick()
                }
                onView(withText("Replace")).inRoot(isDialog()).perform(click())
                fun waitForReviewed(count: Int) {
                    val deadline = System.currentTimeMillis() + 15_000
                    while (System.currentTimeMillis() < deadline) {
                        var actual = 0
                        scenario.onActivity { actual = reviewedCount() }
                        if (actual == count) return
                        Thread.sleep(50)
                    }
                    throw AssertionError("Expected $count reviewed clips")
                }
                waitForReviewed(1)
                assertArrayEquals(VerseAudio.encodeWav(VerseAudio.slice(samples, 800, 1500)), clip.readBytes())
                waitForActivityFocus()
                scenario.onActivity {
                    VerseTakeEditor::class.java.getDeclaredMethod("moveCard", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                        .apply { isAccessible = true }.invoke(editor, 2, 1)
                    assertEquals(100, (field("boundaries").get(editor) as VerseBoundaries).start(1))
                    VerseTakeEditor::class.java.getDeclaredMethod("selectCard", Int::class.javaPrimitiveType)
                        .apply { isAccessible = true }.invoke(editor, 2)
                    assertEquals(0, reviewedCount())
                    (field("next").get(editor) as Button).performClick()
                }
                onView(withText("Replace")).inRoot(isDialog()).perform(click())
                waitForReviewed(1)
                assertArrayEquals(VerseAudio.encodeWav(VerseAudio.slice(samples, 800, 1500)), clip.readBytes())
                scenario.onActivity { editor.dispose() }
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun chipsHelpAndNudgesKeepSharedBoundariesAndStickyControls() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val words = context.assets.open("books/JHN.xml").bufferedReader().use {
            MaculaXml.parse(it.readText()).first().words.filter { !it.disabled(emptySet()) }.take(3)
        }.mapIndexed { index, word -> VerseRecordingWord(word, index) }
        ActivityScenario.launch(WorkbenchActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val editor = VerseTakeEditor(
                    activity, AudioProjectRepository(File(context.cacheDir, "slice-nudge-test")),
                    words, { true }, {}, {}, {}
                )
                val root = editor.view()
                fun field(name: String) = VerseTakeEditor::class.java.getDeclaredField(name).apply { isAccessible = true }
                fun invoke(name: String) = VerseTakeEditor::class.java.getDeclaredMethod(name)
                    .apply { isAccessible = true }.invoke(editor)
                val boundaries = VerseBoundaries(listOf(0, 200, 800, 1600, 2200, 2400))
                field("samples").set(editor, FloatArray(2400))
                field("boundaries").set(editor, boundaries)
                invoke("showSelection")
                invoke("refresh")
                @Suppress("UNCHECKED_CAST")
                val reviewed = field("reviewed").get(editor) as MutableSet<Int>
                reviewed.addAll(listOf(1, 2, 3))
                root.findViewById<View>(R.id.clip_nudge_forward).performClick()
                assertEquals(960, boundaries.end(1))
                assertEquals(960, boundaries.start(2))
                assertEquals(setOf(3), reviewed)
                val chips = root.findViewById<ChipGroup>(R.id.clip_tokens)
                chips.getChildAt(2).performClick()
                assertEquals(3, field("index").getInt(editor))
                root.findViewById<View>(R.id.clip_nudge_back).performClick()
                assertEquals(2040, boundaries.start(4))
                assertEquals(2040, boundaries.end(3))
                assertTrue(reviewed.isEmpty())
                val help = root.findViewById<View>(R.id.clip_help)
                assertEquals(View.GONE, help.visibility)
                val toolbar = root.findViewById<MaterialToolbar>(R.id.clip_toolbar)
                toolbar.menu.performIdentifierAction(R.id.clip_help_action, 0)
                assertEquals(View.VISIBLE, help.visibility)
                val bottom = root.findViewById<View>(R.id.clip_bottom)
                assertTrue(bottom.parent is ConstraintLayout)
                assertEquals(root, bottom.parent)
                editor.dispose()
            }
        }
    }

    @Test
    fun separatorsCutOutMistakesAndEdgeCardsStay() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val words = context.assets.open("books/JHN.xml").bufferedReader().use {
            MaculaXml.parse(it.readText()).first().words.filter { !it.disabled(emptySet()) }.take(2)
        }.mapIndexed { index, word -> VerseRecordingWord(word, index) }
        ActivityScenario.launch(WorkbenchActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val editor = VerseTakeEditor(
                    activity, AudioProjectRepository(File(context.cacheDir, "slice-separator-test")),
                    words, { true }, {}, {}, {}
                )
                val root = editor.view()
                fun field(name: String) = VerseTakeEditor::class.java.getDeclaredField(name).apply { isAccessible = true }
                fun invoke(name: String) = VerseTakeEditor::class.java.getDeclaredMethod(name)
                    .apply { isAccessible = true }.invoke(editor)
                fun select(position: Int) = VerseTakeEditor::class.java.getDeclaredMethod("selectCard", Int::class.javaPrimitiveType)
                    .apply { isAccessible = true }.invoke(editor, position)
                val boundaries = VerseBoundaries(listOf(0, 400, 1600, 2800, 3200))
                field("samples").set(editor, FloatArray(3200))
                field("boundaries").set(editor, boundaries)
                invoke("showSelection")
                invoke("refresh")
                @Suppress("UNCHECKED_CAST")
                val reviewed = field("reviewed").get(editor) as MutableSet<Int>
                @Suppress("UNCHECKED_CAST")
                val slots = field("slots").get(editor) as List<VerseRecordingWord?>
                assertEquals(listOf(null, words[0], words[1], null), slots)
                select(0)
                invoke("insertSeparator")
                assertEquals(4, slots.size)
                select(2)
                reviewed.addAll(listOf(1, 2))
                invoke("insertSeparator")
                assertEquals(listOf(0, 400, 1600, 2200, 2800, 3200), boundaries.snapshot())
                assertEquals(listOf(null, words[0], null, words[1], null), slots)
                assertEquals(3, field("index").getInt(editor))
                assertEquals(setOf(1), reviewed)
                val remove = VerseTakeEditor::class.java.getDeclaredMethod("removeSeparator", Int::class.javaPrimitiveType)
                    .apply { isAccessible = true }
                remove.invoke(editor, 1)
                assertEquals(5, slots.size)
                remove.invoke(editor, 2)
                assertEquals(listOf(0, 400, 1600, 2800, 3200), boundaries.snapshot())
                assertEquals(listOf(null, words[0], words[1], null), slots)
                assertEquals(2, field("index").getInt(editor))
                editor.dispose()
            }
        }
    }

    @Test
    fun checkMarkSavesItsCardAndAdvances() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val words = context.assets.open("books/JHN.xml").bufferedReader().use {
            MaculaXml.parse(it.readText()).first().words.filter { !it.disabled(emptySet()) }
                .distinctBy { it.surface }.take(3)
        }.mapIndexed { index, word -> VerseRecordingWord(word, index) }
        val directory = File(context.cacheDir, "slice-check-test")
        directory.deleteRecursively()
        val repository = AudioProjectRepository(directory)
        try {
            ActivityScenario.launch(WorkbenchActivity::class.java).use { scenario ->
                lateinit var editor: VerseTakeEditor
                lateinit var root: View
                fun field(name: String) = VerseTakeEditor::class.java.getDeclaredField(name).apply { isAccessible = true }
                scenario.onActivity { activity ->
                    editor = VerseTakeEditor(activity, repository, words, { true }, {}, {}, {})
                    root = editor.view()
                    activity.setContentView(root)
                    field("samples").set(editor, FloatArray(2400) { 0.1f })
                    field("boundaries").set(editor, VerseBoundaries(listOf(0, 300, 800, 1600, 2100, 2400)))
                    for (method in listOf("showSelection", "refresh")) {
                        VerseTakeEditor::class.java.getDeclaredMethod(method).apply { isAccessible = true }.invoke(editor)
                    }
                    root.findViewById<androidx.core.widget.NestedScrollView>(R.id.clip_scroll).scrollTo(0, 1000)
                    root.findViewById<RecyclerView>(R.id.clip_cards).scrollToPosition(2)
                }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity {
                    val cards = root.findViewById<RecyclerView>(R.id.clip_cards)
                    val holder = checkNotNull(cards.findViewHolderForAdapterPosition(2))
                    assertTrue(holder.itemView.findViewById<Button>(R.id.word_assign).performClick())
                }
                val deadline = System.currentTimeMillis() + 15_000
                var advanced = false
                while (!advanced && System.currentTimeMillis() < deadline) {
                    scenario.onActivity { advanced = field("index").getInt(editor) == 3 }
                    if (!advanced) Thread.sleep(50)
                }
                assertTrue("Saving the selected card did not advance", advanced)
                assertArrayEquals(VerseAudio.encodeWav(VerseAudio.slice(FloatArray(2400) { 0.1f }, 800, 1600)),
                    repository.clipFile(words[1].word.surface).readBytes())
                scenario.onActivity {
                    val cards = root.findViewById<RecyclerView>(R.id.clip_cards)
                    val button = checkNotNull(cards.findViewHolderForAdapterPosition(2))
                        .itemView.findViewById<Button>(R.id.word_assign)
                    assertEquals(androidx.core.content.ContextCompat.getColor(context, R.color.workbench_recorded),
                        button.currentTextColor)
                    editor.dispose()
                }
            }
        } finally {
            directory.deleteRecursively()
        }
    }
}
