package io.github.danzuep.bibleaudioproducer.ui

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import android.os.ParcelFileDescriptor
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.content.ContextCompat
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.RecyclerView
import android.widget.Spinner
import android.widget.NumberPicker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertArrayEquals
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import io.github.danzuep.bibleaudioproducer.R
import io.github.danzuep.bibleaudioproducer.audio.VerseAudio
import io.github.danzuep.bibleaudioproducer.audio.AndroidAudioDecoder
import io.github.danzuep.bibleaudioproducer.data.RecordingPlanJson
import io.github.danzuep.bibleaudioproducer.data.AudioProjectRepository
import io.github.danzuep.bibleaudioproducer.workbench.MaculaXml
import io.github.danzuep.bibleaudioproducer.workbench.AudioVerse
import io.github.danzuep.bibleaudioproducer.workbench.VerseWorkflow
import io.github.danzuep.bibleaudioproducer.workbench.WordOrderMode
import java.io.File
import android.widget.Button
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.`is`

@RunWith(AndroidJUnit4::class)
class WorkbenchLaunchTest {
    private lateinit var directory: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        File(context.filesDir, "projects").deleteRecursively()
        directory = File(context.filesDir, "projects/project-001")
        check(context.getSharedPreferences("audio-project", Context.MODE_PRIVATE).edit().clear().commit())
    }

    @After
    fun tearDown() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        File(context.filesDir, "projects").deleteRecursively()
        check(context.getSharedPreferences("audio-project", Context.MODE_PRIVATE).edit().clear().commit())
    }

    @Test
    fun john316PresetsKeepArticlesWithTheirWordsAndGermanClausesInOrder() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val verse = context.assets.open("books/JHN.xml").bufferedReader().use {
            MaculaXml.parse(it.readText()).first { item -> item.reference == "JHN 3:16" }
        }
        fun numbers(mode: WordOrderMode) = VerseWorkflow.modeOrder(verse, mode).map { it + 1 }
        val english = numbers(WordOrderMode.ENGLISH_SVO)
        assertEquals(listOf(13, 11, 12, 9, 10), english.filter { it in 9..13 })
        val german = numbers(WordOrderMode.GERMAN_V2)
        // Freie Lutherbibel, John 3:16: fronted adverb, verb, subject and object;
        // the embedded object precedes its verb, followed by the purpose clause.
        // https://github.com/bibliadelpueblo/BibliaLibre/blob/bed6a5c56de7db4895304553c6d7c3e763480f10/Bibles/German.FreieLuther/43-Johannes.md
        assertEquals(listOf(2, 1, 3, 4, 5, 6, 7), german.filter { it in 1..7 })
        assertEquals(listOf(11, 12, 9, 10, 13, 14), german.filter { it in 9..14 })
        assertEquals(listOf(25, 24, 23), german.filter { it in 23..25 })
    }

    @Test
    fun john316OtherPresetsKeepPurposeAndNegationInTheirClauses() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val verse = context.assets.open("books/JHN.xml").bufferedReader().use {
            MaculaXml.parse(it.readText()).first { item -> item.reference == "JHN 3:16" }
        }
        fun numbers(mode: WordOrderMode) = VerseWorkflow.modeOrder(verse, mode).map { it + 1 }
        val mandarin = numbers(WordOrderMode.MANDARIN_SADVVO)
        assertEquals(listOf(13, 11, 12, 9, 10, 14), mandarin.filter { it in 9..14 })
        val japanese = numbers(WordOrderMode.JAPANESE_SOV)
        assertEquals(listOf(11, 12, 9, 10, 13, 14, 3), japanese.filter { it in 9..14 || it == 3 })
        val arabic = numbers(WordOrderMode.ARABIC_VSO)
        assertEquals(listOf(2, 1, 3, 4, 5, 6, 7), arabic.filter { it in 1..7 })
        assertEquals(listOf(20, 21), arabic.filter { it in 20..21 })
        for (order in listOf(mandarin, japanese, arabic)) {
            assertEquals((1..verse.words.size).toList(), order.sorted())
        }
    }

    @Test
    fun johnPresetSelectionPersistsWithoutRecordingAndRespectsSavedOrders() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val verses = context.assets.open("books/JHN.xml").bufferedReader().use {
                MaculaXml.parse(it.readText())
            }
            val first = verses.first { it.reference == "JHN 1:1" }
            val repository = AudioProjectRepository(directory)
            fun select(label: Int) {
                onView(withId(R.id.audio_verse_word_order)).perform(click())
                onData(`is`(context.getString(label))).perform(click())
            }
            fun waitForDefault(expected: String) {
                val deadline = System.currentTimeMillis() + 15_000
                while (System.currentTimeMillis() < deadline) {
                    if (repository.loadState().defaultWordOrder == expected) return
                    Thread.sleep(50)
                }
                throw AssertionError("Default was not saved: $expected")
            }
            fun waitForSave(scenario: ActivityScenario<WorkbenchActivity>) {
                val busy = WorkbenchActivity::class.java.getDeclaredField("busy").apply { isAccessible = true }
                val deadline = System.currentTimeMillis() + 15_000
                while (System.currentTimeMillis() < deadline) {
                    var saving = true
                    scenario.onActivity { saving = busy.getBoolean(it) }
                    if (!saving) return
                    Thread.sleep(50)
                }
                throw AssertionError("Verse save did not finish")
            }
            ActivityScenario.launch(WorkbenchActivity::class.java).use { scenario ->
                awaitBooks(scenario, 27)
                onView(withText("JHN 3:16")).check(matches(isDisplayed()))
                assertEquals(null, repository.loadState().defaultWordOrder)
                onView(withId(R.id.audio_verse_record)).perform(click())
                select(R.string.audio_word_order_english)
                waitForDefault("ENGLISH_SVO")
                scenario.recreate()
                awaitBooks(scenario, 27)
                onView(withText("JHN 3:16")).check(matches(isDisplayed()))
                onView(withId(R.id.audio_verse_record)).perform(click())
                select(R.string.audio_word_order_greek)
                select(R.string.audio_word_order_custom)
                pressBack()
                onView(withId(R.id.audio_verse_close)).perform(click())
                waitForSave(scenario)
                waitForDefault("ENGLISH_SVO")
                onView(withId(R.id.audio_choose_verse)).perform(click())
                onView(withContentDescription(first.words.joinToString(" ") { it.surface })).perform(click())
                onView(withId(R.id.audio_verse_record)).perform(click())
                onView(withId(R.id.audio_verse_word_order)).check { view, _ ->
                    assertEquals(context.getString(R.string.audio_word_order_english), (view as Spinner).selectedItem)
                }
                select(R.string.audio_word_order_japanese)
                pressBack()
                onView(withId(R.id.audio_verse_close)).perform(click())
                waitForSave(scenario)
                onView(withContentDescription(first.words.joinToString(" ") { it.surface })).perform(click())
                onView(withId(R.id.audio_verse_record)).perform(click())
                onView(withId(R.id.audio_verse_word_order)).check { view, _ ->
                    assertEquals(context.getString(R.string.audio_word_order_japanese), (view as Spinner).selectedItem)
                }
                pressBack()
                onView(withId(R.id.audio_verse_close)).perform(click())
                waitForSave(scenario)
                scenario.recreate()
                awaitBooks(scenario, 27)
                assertEquals("ENGLISH_SVO", repository.loadState().defaultWordOrder)
                assertEquals(VerseWorkflow.modeOrder(first, WordOrderMode.JAPANESE_SOV),
                    repository.loadState().wordOrders[first.reference])
                val other = File(context.filesDir, "projects/project-002").apply { mkdirs() }
                File(other, "project-name.txt").writeText("Project002")
                File(other, "verse-onboarding").writeText("JHN 3:16")
                onView(withId(R.id.audio_verse_close)).perform(click())
                pressBack()
                onView(withText("Project002")).perform(click())
                assertTrue(awaitBookCount(scenario, 27, 15_000))
                assertEquals(null, AudioProjectRepository(other).loadState().defaultWordOrder)
                onView(withId(R.id.audio_verse_record)).perform(click())
                select(R.string.audio_word_order_japanese)
                assertEquals("JAPANESE_SOV", AudioProjectRepository(other).loadState().defaultWordOrder)
                assertEquals("ENGLISH_SVO", repository.loadState().defaultWordOrder)
            }
    }

    @Test
    fun newInstallLoadsTheFullGreekPlan() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cards = context.assets.open("recording-plan.json").bufferedReader().use {
            RecordingPlanJson.parse(it.readText())
        }
        assertEquals(4917, cards.size)
        assertEquals(27, cards.map { it.book }.distinct().size)
        assertEquals("JHN", cards.first().book)
        val chapterReferences = context.assets.open("books/JHN.xml").bufferedReader().use {
            MaculaXml.parse(it.readText()).filter { it.chapter == 1 }
        }
        val forms = chapterReferences.flatMap { it.words }.filterNot { it.proper }
            .flatMap { listOf(it.lemma, it.surface).distinct() }.groupingBy { it }.eachCount()
        val chapterCards = cards.filter { it.lemma in forms }
        ActivityScenario.launch(WorkbenchActivity::class.java).use { scenario ->
            awaitBooks(scenario, 27)
            onView(withText("JHN 3:16")).check(matches(isDisplayed()))
            onView(withId(R.id.audio_verse_close)).check(matches(isDisplayed()))
            onView(withId(R.id.audio_verse_record))
                .check(matches(isDisplayed()))
                .check(matches(withContentDescription(R.string.audio_record_verse_words)))
                .check { view, _ ->
                    val header = view.parent as ViewGroup
                    val play = header.getChildAt(header.indexOfChild(view) - 1)
                    assertEquals(context.getString(R.string.audio_play_verse), play.contentDescription)
                    assertEquals(context.getString(R.string.audio_record_symbol), (view as TextView).text.toString())
                }
            onView(withId(R.id.audio_verse_close)).perform(click())
            onView(withId(R.id.audio_workbench_chapter)).check(matches(withText("1")))
            onView(withId(R.id.audio_workbench_chapter)).perform(click())
            onView(isAssignableFrom(NumberPicker::class.java)).check(matches(isDisplayed()))
            pressBack()
            onView(withId(R.id.audio_workbench_coverage))
                .check(matches(withText("0 of ${chapterCards.size} recorded")))
            onView(withId(R.id.audio_workbench_list)).check { view, _ ->
                val rows = ((view as RecyclerView).adapter as RecordingCardAdapter).currentList
                assertEquals(chapterCards.maxBy { forms.getValue(it.lemma) }.lemma, rows.first().card.lemma)
            }
            openSettings(scenario)
            onView(withId(R.id.audio_workbench_export)).check(matches(isEnabled()))
        }
    }

    @Test
    fun savedPlanOpensAndSearchShowsEmptyResults() {
        check(directory.mkdirs())
        File(directory, "recording-plan.json").writeText(
            """{"cards":[{"book":"JHN","lemma":"λόγος","gloss":"word","occurrences":1}]}"""
        )
        ActivityScenario.launch(WorkbenchActivity::class.java).use { scenario ->
            awaitBooks(scenario, 1)
            onView(withId(R.id.audio_verse_close)).perform(click())
            onView(withId(R.id.audio_workbench_coverage)).check(matches(withText("0 of 1 recorded")))
            openSettings(scenario)
            onView(withId(R.id.audio_workbench_export)).check(matches(isEnabled()))
            openSettings(scenario)
            onView(withId(R.id.audio_workbench_search)).perform(replaceText("no match"))
            closeSoftKeyboard()
            onView(withId(R.id.audio_workbench_empty))
                .check(matches(withText(R.string.audio_workbench_no_results)))
        }
    }

    @Test
    fun chapterPickerPreservesSelectionOnRefreshAndResetsForAnotherBook() {
        ActivityScenario.launch(WorkbenchActivity::class.java).use { scenario ->
            awaitBooks(scenario, 27)
            onView(withId(R.id.audio_verse_close)).perform(click())
            onView(withId(R.id.audio_workbench_chapter)).perform(click())
            onView(isAssignableFrom(NumberPicker::class.java)).perform(object : ViewAction {
                override fun getDescription() = "Select chapter 3"
                override fun getConstraints() = isAssignableFrom(NumberPicker::class.java)
                override fun perform(uiController: UiController, view: View) {
                    (view as NumberPicker).value = 2
                }
            })
            onView(withText(android.R.string.ok)).perform(click())
            onView(withId(R.id.audio_workbench_chapter))
                .check(matches(allOf(withText("3"), withContentDescription("Chapter 3"))))
            scenario.onActivity { activity ->
                val refresh = WorkbenchActivity::class.java.getDeclaredMethod("refreshBooks", Boolean::class.javaPrimitiveType)
                refresh.isAccessible = true
                refresh.invoke(activity, false)
            }
            assertTrue(awaitBookCount(scenario, 27, 15_000))
            onView(withId(R.id.audio_workbench_chapter)).check(matches(withText("3")))
            pressBack()
            onView(allOf(withText("Project001"), isDisplayed())).perform(click())
            assertTrue(awaitBookCount(scenario, 27, 15_000))
            onView(withId(R.id.audio_verse_close)).perform(click())
            onView(withId(R.id.audio_workbench_chapter)).check(matches(withText("3")))
            scenario.onActivity { activity ->
                val spinner = activity.findViewById<Spinner>(R.id.audio_workbench_books)
                spinner.setSelection((spinner.selectedItemPosition + 1) % spinner.count)
            }
            assertTrue(awaitBookCount(scenario, 27, 15_000))
            onView(withId(R.id.audio_workbench_chapter)).check(matches(withText("1")))
            onView(withId(R.id.audio_choose_verse)).perform(click())
            onView(withId(R.id.audio_workbench_toolbar)).check(matches(org.hamcrest.Matchers.not(isDisplayed())))
            pressBack()
            onView(withId(R.id.audio_workbench_toolbar)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun savesAnOrderForABundledVerse() {
        createLegacyProject()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val verse = context.assets.open("books/JHN.xml").bufferedReader().use {
            MaculaXml.parse(it.readText()).first { item -> item.reference == "JHN 1:1" }
        }
        val skipped = verse.words.filterNot { it.proper }.first { candidate ->
            verse.words.count { it.surface == candidate.surface } == 1
        }
        var expectedSurfaceOrder: List<String>? = null
        ActivityScenario.launch(WorkbenchActivity::class.java).use { scenario ->
            awaitBooks(scenario, 27)
            onView(withText(skipped.surface)).check(matches(isDisplayed()))
            onView(withText(skipped.surface)).perform(object : ViewAction {
                override fun getDescription() = "Drag the word onto its adjacent card"

                override fun getConstraints() = isAssignableFrom(TextView::class.java)

                override fun perform(uiController: UiController, view: View) {
                    var token = view
                    while (token !is FrameLayout || token.parent !is ViewGroup) {
                        token = token.parent as? View
                            ?: throw AssertionError("Greek word token was not found")
                    }
                    val flow = token.parent as ViewGroup
                    val sourceIndex = flow.indexOfChild(token)
                    val targetIndex = if (sourceIndex == 0) 1 else sourceIndex - 1
                    check(targetIndex != sourceIndex)
                    val expected = (0 until flow.childCount).map { index ->
                        val stack = (flow.getChildAt(index) as FrameLayout).getChildAt(0) as ViewGroup
                        (stack.getChildAt(1) as TextView).text.toString()
                    }.toMutableList()
                    val moved = expected.removeAt(sourceIndex)
                    expected.add(targetIndex, moved)
                    expectedSurfaceOrder = expected
                    val start = IntArray(2)
                    val end = IntArray(2)
                    token.getLocationOnScreen(start)
                    flow.getChildAt(targetIndex).getLocationOnScreen(end)
                    val downTime = SystemClock.uptimeMillis()
                    fun inject(action: Int, x: Int, y: Int) {
                        val event = MotionEvent.obtain(
                            downTime, SystemClock.uptimeMillis(), action, x.toFloat(), y.toFloat(), 0
                        )
                        try {
                            uiController.injectMotionEvent(event)
                        } finally {
                            event.recycle()
                        }
                    }
                    inject(
                        MotionEvent.ACTION_DOWN,
                        start[0] + token.width / 2,
                        start[1] + token.height / 2
                    )
                    uiController.loopMainThreadForAtLeast(
                        (ViewConfiguration.getLongPressTimeout() + ViewConfiguration.getTapTimeout() + 100).toLong()
                    )
                    inject(
                        MotionEvent.ACTION_MOVE,
                        end[0] + flow.getChildAt(targetIndex).width / 2,
                        end[1] + flow.getChildAt(targetIndex).height / 2
                    )
                    assertTrue("The held card did not follow the finger", token.translationX != 0f || token.translationY != 0f)
                    inject(
                        MotionEvent.ACTION_UP,
                        end[0] + flow.getChildAt(targetIndex).width / 2,
                        end[1] + flow.getChildAt(targetIndex).height / 2
                    )
                }
            })
            onView(withContentDescription(context.getString(R.string.audio_word_unspoken, skipped.surface)))
                .perform(click())
            onView(withText(R.string.audio_word_disable)).perform(click())
            onView(withId(R.id.audio_verse_close)).perform(click())
            val stateFile = File(directory, "audio-state.json")
            val saveDeadline = System.currentTimeMillis() + 15_000
            while (!stateFile.isFile && System.currentTimeMillis() < saveDeadline) Thread.sleep(50)
            assertTrue("Verse order was not saved", stateFile.isFile)
            val saved = AudioProjectRepository(directory).loadState()
            val savedOrder = saved.wordOrders["JHN 1:1"]
                ?: throw AssertionError("Reordered verse indices were not saved")
            assertEquals(expectedSurfaceOrder, savedOrder.map { verse.words[it].surface })
            assertTrue(skipped.unspokenKey in saved.unspoken)
            onView(withId(R.id.audio_choose_verse)).perform(click())
            onView(withContentDescription(verse.words.joinToString(" ") { it.surface })).perform(click())
            onView(withId(R.id.audio_verse_more)).perform(click())
            onView(withText(R.string.audio_format_wav)).check(matches(isDisplayed()))
            onView(withText(android.R.string.cancel)).perform(click())
            onView(withId(R.id.audio_verse_close)).perform(click())
        }
    }

    @Test
    fun interruptedTakeCanBeReviewedAfterRelaunch() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = AudioProjectRepository(directory)
        val current = repository.clipFile("λόγος")
        check(current.parentFile!!.mkdirs())
        current.writeBytes(byteArrayOf(1))

        fun prepareTake() {
            repository.recordingCandidateFile("λόγος").writeBytes(byteArrayOf(2))
            check(context.getSharedPreferences("audio-project", Context.MODE_PRIVATE).edit()
                .putString("pending_recording_lemma", "λόγος")
                .putString("pending_recording_project", "project-001").commit())
        }

        prepareTake()
        ActivityScenario.launch(WorkbenchActivity::class.java).use { scenario ->
            onView(withText(R.string.audio_review_title)).check(matches(isDisplayed()))
            onView(withContentDescription(R.string.audio_review_keep)).perform(click())
            awaitBooks(scenario, 27)
            assertEquals(listOf(1), current.readBytes().map { it.toInt() })
        }

        prepareTake()
        ActivityScenario.launch(WorkbenchActivity::class.java).use { scenario ->
            onView(withContentDescription(R.string.audio_review_use)).perform(click())
            awaitBooks(scenario, 27)
            val deadline = System.currentTimeMillis() + 15_000
            val preferences = context.getSharedPreferences("audio-project", Context.MODE_PRIVATE)
            while (preferences.contains("pending_recording_lemma") && System.currentTimeMillis() < deadline) {
                Thread.sleep(50)
            }
            assertTrue("Take review was not completed", !preferences.contains("pending_recording_lemma"))
            assertEquals(listOf(2), current.readBytes().map { it.toInt() })
            assertTrue(!repository.recordingCandidateFile("λόγος").exists())
        }
    }

    @Test
    fun recordingPlaybackCanStopAndReturnsToPlayWhenComplete() {
        check(directory.mkdirs())
        File(directory, "project-name.txt").writeText("Project001")
        File(directory, "recording-plan.json").writeText(
            """{"cards":[{"book":"JHN","lemma":"λόγος","gloss":"word","occurrences":1}]}"""
        )
        val clip = AudioProjectRepository(directory).clipFile("λόγος")
        check(clip.parentFile!!.mkdirs())
        clip.writeBytes(VerseAudio.encodeWav(FloatArray(16000) { 0.1f }))
        ActivityScenario.launch(WorkbenchActivity::class.java).use { scenario ->
            awaitBooks(scenario, 1)
            onView(withId(R.id.audio_workbench_play)).perform(click())
            awaitPlayDescription(scenario, "Stop λόγος")
            onView(withId(R.id.audio_workbench_play)).perform(click())
            awaitPlayDescription(scenario, "Play λόγος")
            onView(withId(R.id.audio_workbench_play)).perform(click())
            awaitPlayDescription(scenario, "Stop λόγος")
            awaitPlayDescription(scenario, "Play λόγος")
        }
    }

    @Test
    fun downloadWarnsAboutMissingSpeechAndCanStripIt() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val verse = context.assets.open("books/JHN.xml").bufferedReader().use {
            MaculaXml.parse(it.readText()).first()
        }
        val words = verse.words.filter { !it.disabled(emptySet()) }.distinctBy { it.surface }.take(2)
        assertEquals(2, words.size)
        val shortVerse = AudioVerse(verse.reference, words)
        ActivityScenario.launch(WorkbenchActivity::class.java).use { scenario ->
            awaitBooks(scenario, 27)
            val clip = AudioProjectRepository(directory).clipFile(words.first().surface)
            check(clip.parentFile!!.mkdirs() || clip.parentFile!!.isDirectory)
            clip.writeBytes(VerseAudio.encodeWav(FloatArray(16000) { 0.1f }))
            fun openDownload() {
                scenario.onActivity { activity ->
                    val download = WorkbenchActivity::class.java.getDeclaredMethod(
                        "downloadVerse", AudioVerse::class.java
                    ).apply { isAccessible = true }
                    download.invoke(activity, shortVerse)
                }
            }
            openDownload()
            onView(withText(context.getString(R.string.audio_download_tts_warning, 1)))
                .inRoot(isDialog()).check(matches(isDisplayed()))
            onView(withText(R.string.audio_download_strip_tts)).inRoot(isDialog()).check(matches(isDisplayed()))
            onView(withText(android.R.string.cancel)).inRoot(isDialog()).perform(click())
            check(clip.delete())
            openDownload()
            onView(withText(context.getString(R.string.audio_download_tts_warning, 2)))
                .inRoot(isDialog()).check(matches(isDisplayed()))
            onView(withText(R.string.audio_download_strip_tts)).inRoot(isDialog()).perform(click())
            onView(withText(R.string.audio_download_strip_tts)).inRoot(isDialog()).check(matches(isChecked()))
            onView(withText(R.string.audio_download_verse)).inRoot(isDialog()).perform(click())
            onView(withText(R.string.audio_download_strip_tts)).inRoot(isDialog()).check(matches(isDisplayed()))
            onView(withText(android.R.string.cancel)).inRoot(isDialog()).perform(click())
        }
    }

    @Test
    fun missingVerseUsesModernGreekFallback() {
        org.junit.Assume.assumeTrue(
            InstrumentationRegistry.getArguments().getString("testGreekTts") == "true"
        )
        val context = ApplicationProvider.getApplicationContext<Context>()
        val verse = context.assets.open("books/JHN.xml").bufferedReader().use {
            MaculaXml.parse(it.readText()).first()
        }
        val shortVerse = AudioVerse(verse.reference, listOf(verse.words.first { !it.disabled(emptySet()) }))
        ActivityScenario.launch(WorkbenchActivity::class.java).use { scenario ->
            awaitBooks(scenario, 27)
            scenario.onActivity { activity ->
                val playVerse = WorkbenchActivity::class.java.getDeclaredMethod(
                    "playVerse", AudioVerse::class.java
                ).apply { isAccessible = true }
                val versePlaying = WorkbenchActivity::class.java.getDeclaredField("playingVerseReference")
                    .apply { isAccessible = true }
                playVerse.invoke(activity, shortVerse)
                assertEquals(shortVerse.reference, versePlaying.get(activity))
            }
            val deadline = System.currentTimeMillis() + 20_000
            var rendered = false
            while (!rendered && System.currentTimeMillis() < deadline) {
                scenario.onActivity { activity ->
                    val temporary = WorkbenchActivity::class.java.getDeclaredField("playbackTempFile")
                        .apply { isAccessible = true }
                    rendered = (temporary.get(activity) as? File)?.length()?.let { it > 44 } == true
                }
                if (!rendered) Thread.sleep(25)
            }
            assertTrue("Modern Greek fallback did not render", rendered)
        }
    }

    @Test
    fun downloadIncludesOrStripsGreekSpeechAsSelected() {
        org.junit.Assume.assumeTrue(
            Build.VERSION.SDK_INT >= 29 &&
                InstrumentationRegistry.getArguments().getString("testGreekTts") == "true"
        )
        val context = ApplicationProvider.getApplicationContext<Context>()
        val verse = context.assets.open("books/JHN.xml").bufferedReader().use {
            MaculaXml.parse(it.readText()).first()
        }
        val words = verse.words.filter { !it.disabled(emptySet()) }.distinctBy { it.surface }.take(2)
        assertEquals(2, words.size)
        val shortVerse = AudioVerse(verse.reference, words)
        val resolver = context.contentResolver
        val uris = mutableListOf<android.net.Uri>()
        try {
            ActivityScenario.launch(WorkbenchActivity::class.java).use { scenario ->
                awaitBooks(scenario, 27)
                val clip = AudioProjectRepository(directory).clipFile(words.first().surface)
                check(clip.parentFile!!.mkdirs() || clip.parentFile!!.isDirectory)
                clip.writeBytes(VerseAudio.encodeWav(FloatArray(16000) { 0.1f }))
                fun save(strip: Boolean): ByteArray {
                    val values = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "test-${System.nanoTime()}.wav")
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "audio/wav")
                    }
                    val uri = checkNotNull(resolver.insert(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values))
                    uris += uri
                    checkNotNull(resolver.openOutputStream(uri)).close()
                    val paths = listOf(clip.absolutePath, "")
                    val selected = selectVerseDownloadIndices(paths, strip)
                    scenario.onActivity { activity ->
                        val exportClass = WorkbenchActivity::class.java.declaredClasses
                            .first { it.simpleName == "VerseExport" }
                        val export = exportClass.declaredConstructors.single().apply { isAccessible = true }
                            .newInstance(
                                shortVerse.reference, selected.map { words[it].surface },
                                selected.map { paths[it] }, if (strip) 1 else 0,
                                io.github.danzuep.bibleaudioproducer.audio.VerseExportFormat.WAV
                            )
                        WorkbenchActivity::class.java.getDeclaredMethod("exportVerse", android.net.Uri::class.java, exportClass)
                            .apply { isAccessible = true }.invoke(activity, uri, export)
                    }
                    val deadline = System.currentTimeMillis() + 30_000
                    var bytes = byteArrayOf()
                    var busy = true
                    while ((bytes.size <= 44 || busy) && System.currentTimeMillis() < deadline) {
                        bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: byteArrayOf()
                        scenario.onActivity { activity ->
                            busy = WorkbenchActivity::class.java.getDeclaredField("busy")
                                .apply { isAccessible = true }.getBoolean(activity)
                        }
                        if (bytes.size <= 44 || busy) Thread.sleep(100)
                    }
                    assertTrue("Verse download was not written", bytes.size > 44 && !busy)
                    return bytes
                }
                val stripped = save(true)
                val withTts = save(false)
                assertEquals(44 + 16000 * 2, stripped.size)
                assertTrue("Generated Greek audio was not included", withTts.size > stripped.size)
            }
        } finally {
            uris.forEach { resolver.delete(it, null, null) }
        }
    }

    @Test
    fun chapterPlaybackStopsDuringPreparationAndResetsAfterCompletion() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val verse = context.assets.open("books/JHN.xml").bufferedReader().use {
            MaculaXml.parse(it.readText()).first()
        }
        val word = verse.words.first { !it.disabled(emptySet()) }
        val shortVerse = AudioVerse(verse.reference, listOf(word))
        ActivityScenario.launch(WorkbenchActivity::class.java).use { scenario ->
            awaitBooks(scenario, 27)
            onView(withId(R.id.audio_verse_close)).perform(click())
            val clip = AudioProjectRepository(directory).clipFile(word.surface)
            check(clip.parentFile!!.mkdirs() || clip.parentFile!!.isDirectory)
            clip.writeBytes(VerseAudio.encodeWav(FloatArray(VerseAudio.SAMPLE_RATE) { 0.1f }))
            onView(withId(R.id.audio_choose_verse)).perform(click())
            fun startShortChapter() {
                scenario.onActivity { activity ->
                    val play = WorkbenchActivity::class.java.getDeclaredMethod(
                        "playChapter", Int::class.javaPrimitiveType, List::class.java
                    )
                    play.isAccessible = true
                    play.invoke(activity, 1, listOf(shortVerse))
                }
            }
            startShortChapter()
            onView(withContentDescription(R.string.audio_stop_chapter)).perform(click())
            onView(withContentDescription(R.string.audio_play_chapter)).check(matches(isDisplayed()))
            startShortChapter()
            onView(withContentDescription(R.string.audio_stop_chapter)).check(matches(isDisplayed()))
            val deadline = System.currentTimeMillis() + 15_000
            var completed = false
            while (!completed && System.currentTimeMillis() < deadline) {
                scenario.onActivity { activity ->
                    val views = ArrayList<View>()
                    activity.window.decorView.findViewsWithText(
                        views, context.getString(R.string.audio_play_chapter), View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION
                    )
                    completed = views.any { it.isShown }
                }
                if (!completed) Thread.sleep(50)
            }
            assertTrue("Chapter playback did not complete", completed)
        }
    }

    @Test
    fun closingChapterStopsPlayback() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val verse = context.assets.open("books/JHN.xml").bufferedReader().use {
            MaculaXml.parse(it.readText()).first()
        }
        val word = verse.words.first { !it.disabled(emptySet()) }
        val shortVerse = AudioVerse(verse.reference, listOf(word))
        ActivityScenario.launch(WorkbenchActivity::class.java).use { scenario ->
            awaitBooks(scenario, 27)
            onView(withId(R.id.audio_verse_close)).perform(click())
            val clip = AudioProjectRepository(directory).clipFile(word.surface)
            check(clip.parentFile!!.mkdirs() || clip.parentFile!!.isDirectory)
            clip.writeBytes(VerseAudio.encodeWav(FloatArray(10 * VerseAudio.SAMPLE_RATE) { 0.1f }))
            fun openChapter() {
                onView(withId(R.id.audio_choose_verse)).perform(click())
            }
            fun playShortChapter() {
                scenario.onActivity { activity ->
                    WorkbenchActivity::class.java.getDeclaredMethod(
                        "playChapter", Int::class.javaPrimitiveType, List::class.java
                    ).apply { isAccessible = true }.invoke(activity, 1, listOf(shortVerse))
                }
            }
            openChapter()
            playShortChapter()
            onView(withContentDescription(R.string.audio_stop_chapter)).check(matches(isDisplayed()))
            onView(withContentDescription(R.string.audio_stop_chapter)).perform(click())
            onView(withContentDescription(R.string.audio_play_chapter)).check(matches(isDisplayed()))
            playShortChapter()
            onView(withContentDescription(R.string.audio_stop_chapter)).check(matches(isDisplayed()))
            pressBack()
            openChapter()
            onView(withContentDescription(R.string.audio_play_chapter)).check(matches(isEnabled()))
        }
    }

    @Test
    fun verseRecordingShowsReviewBeforeStoppingAndCanKeepTheOldTake() {
        createLegacyProject()
        val context = grantRecordingPermission()
        val repository = AudioProjectRepository(directory)
        val surface = context.assets.open("books/JHN.xml").bufferedReader().use {
            val words = MaculaXml.parse(it.readText()).first { verse -> verse.reference == "JHN 1:1" }.words
            words.first { word -> words.count { it.surface == word.surface } == 1 }.surface
        }
        ActivityScenario.launch(WorkbenchActivity::class.java).use { scenario ->
            awaitBooks(scenario, 27)
            onView(withContentDescription(context.getString(R.string.audio_word_unspoken, surface))).perform(click())
            val current = repository.clipFile(surface)
            check(current.parentFile!!.mkdirs() || current.parentFile!!.isDirectory)
            val original = VerseAudio.encodeWav(FloatArray(16000) { 0.1f })
            current.writeBytes(original)
            onView(withContentDescription(context.getString(R.string.audio_word_record, surface))).perform(click())
            onView(withText(R.string.audio_review_new_waveform)).check(matches(isDisplayed()))
            onView(withText(R.string.audio_review_old_waveform)).check(matches(isDisplayed()))
            Thread.sleep(800)
            onView(withContentDescription(R.string.audio_review_stop_recording)).perform(click())
            onView(withContentDescription(R.string.audio_review_play_new)).check(matches(isEnabled()))
            onView(withContentDescription(R.string.audio_review_keep)).perform(click())
            assertArrayEquals(original, current.readBytes())
            assertTrue(!repository.recordingCandidateFile(surface).exists())
        }
    }

    @Test
    fun verseTakeRequiresConfirmationBeforeReplacingExistingClip() {
        val context = grantRecordingPermission()
        val verse = context.assets.open("books/JHN.xml").bufferedReader().use {
            MaculaXml.parse(it.readText()).first { it.reference == "JHN 3:16" }
        }
        val words = verse.words.filter { !it.disabled(emptySet()) }
        val first = words.first()
        ActivityScenario.launch(WorkbenchActivity::class.java).use { scenario ->
            awaitBooks(scenario, 27)
            val clip = AudioProjectRepository(directory).clipFile(first.surface)
            check(clip.parentFile!!.mkdirs() || clip.parentFile!!.isDirectory)
            val original = VerseAudio.encodeWav(FloatArray(16000) { 0.1f })
            clip.writeBytes(original)
            onView(withId(R.id.audio_verse_record)).perform(click())
            onView(withId(R.id.audio_verse_word_order)).perform(click())
            onData(`is`(context.getString(R.string.audio_word_order_greek))).perform(click())
            onView(withId(R.id.clip_toolbar)).check(matches(isDisplayed()))
            onView(withId(R.id.audio_workbench_toolbar)).check(matches(org.hamcrest.Matchers.not(isDisplayed())))
            onView(withContentDescription("Record verse")).perform(click())
            Thread.sleep(1000)
            onView(withContentDescription("Stop recording")).perform(click())
            val deadline = System.currentTimeMillis() + 15_000
            var ready = false
            while (!ready && System.currentTimeMillis() < deadline) {
                scenario.onActivity { activity ->
                    val views = ArrayList<View>()
                    activity.window.decorView.findViewsWithText(views, "Next", View.FIND_VIEWS_WITH_TEXT)
                    ready = views.any { it is Button && it.isEnabled }
                }
                if (!ready) Thread.sleep(50)
            }
            assertTrue("Verse take was not decoded", ready)
            onView(withId(R.id.clip_next)).perform(click())
            onView(withText(android.R.string.cancel)).perform(click())
            assertArrayEquals(original, clip.readBytes())
            onView(withText("Keep & next")).perform(click())
            onView(withText("1 / ${words.size} reviewed"))
                .check(matches(isDisplayed()))
            val label = words[1].gloss.ifBlank { words[1].mandarin.ifBlank { "[translation needed]" } }
            scenario.onActivity { activity ->
                val scroll = activity.findViewById<androidx.core.widget.NestedScrollView>(R.id.clip_scroll)
                val cards = activity.findViewById<View>(R.id.clip_cards)
                scroll.scrollTo(0, cards.top)
            }
            onView(withContentDescription("Play $label slice")).perform(click())
            onView(withId(R.id.clip_next)).perform(click())
            val saved = AudioProjectRepository(directory).clipFile(words[1].surface)
            val saveDeadline = System.currentTimeMillis() + 15_000
            while (!saved.isFile && System.currentTimeMillis() < saveDeadline) Thread.sleep(50)
            assertTrue("The reviewed word clip was not saved", saved.isFile && saved.length() > 0)
            var advanced = false
            while (!advanced && System.currentTimeMillis() < saveDeadline) {
                scenario.onActivity { activity ->
                    val views = ArrayList<View>()
                    activity.window.decorView.findViewsWithText(
                        views, "2 / ${words.size} reviewed", View.FIND_VIEWS_WITH_TEXT
                    )
                    advanced = views.isNotEmpty()
                }
                if (!advanced) Thread.sleep(50)
            }
            assertTrue("The review did not advance", advanced)
            assertEquals("RIFF", saved.inputStream().use { input ->
                ByteArray(4).also { assertEquals(4, input.read(it)) }.toString(Charsets.US_ASCII)
            })
            assertTrue("Saved word cannot be decoded", AndroidAudioDecoder().decode(saved).isNotEmpty())
            pressBack()
            onView(withText("Discard")).perform(click())
            assertArrayEquals(original, clip.readBytes())
            onView(withId(R.id.audio_verse_close)).check(matches(isDisplayed()))
            onView(withId(R.id.audio_verse_record)).check(matches(isDisplayed())).check(matches(isEnabled()))
        }
    }

    private fun grantRecordingPermission(): Context {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val result = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "pm grant ${context.packageName} ${Manifest.permission.RECORD_AUDIO}"
        )
        ParcelFileDescriptor.AutoCloseInputStream(result).bufferedReader().use { it.readText() }
        assertEquals(
            PackageManager.PERMISSION_GRANTED,
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
        )
        return context
    }

    private fun createLegacyProject() {
        check(directory.mkdirs())
        File(directory, "project-name.txt").writeText("Project001")
    }

    @Test
    fun holdShowsWaveformSavesOnReleaseAndPreservesTakeOnCancel() {
        grantRecordingPermission()
        check(directory.mkdirs())
        File(directory, "recording-plan.json").writeText(
            """{"cards":[{"book":"JHN","lemma":"λόγος","gloss":"word","occurrences":1}]}"""
        )
        val repository = AudioProjectRepository(directory)
        ActivityScenario.launch(WorkbenchActivity::class.java).use { scenario ->
            awaitBooks(scenario, 1)
            onView(withId(R.id.audio_verse_close)).perform(click())
            onView(withId(R.id.audio_workbench_record)).check(matches(isDisplayed()))
            fun touch(action: Int) = scenario.onActivity { activity ->
                val button = activity.findViewById<Button>(R.id.audio_workbench_record)
                val now = SystemClock.uptimeMillis()
                val event = MotionEvent.obtain(now, now, action, button.width / 2f, button.height / 2f, 0)
                try {
                    button.dispatchTouchEvent(event)
                } finally {
                    event.recycle()
                }
            }
            touch(MotionEvent.ACTION_DOWN)
            val deadline = System.currentTimeMillis() + 10_000
            var showing = false
            while (!showing && System.currentTimeMillis() < deadline) {
                scenario.onActivity {
                    showing = it.findViewById<View>(R.id.audio_workbench_waveform)?.visibility == View.VISIBLE
                }
                if (!showing) Thread.sleep(25)
            }
            assertTrue("Recording waveform did not appear", showing)
            Thread.sleep(800)
            touch(MotionEvent.ACTION_UP)
            val clip = repository.clipFile("λόγος")
            assertTrue("Take was not saved on release", clip.isFile && clip.length() > 0)
            val original = clip.readBytes()
            touch(MotionEvent.ACTION_DOWN)
            Thread.sleep(800)
            touch(MotionEvent.ACTION_CANCEL)
            assertArrayEquals(original, clip.readBytes())
            assertTrue(!repository.recordingCandidateFile("λόγος").exists())
        }
    }

    private fun awaitPlayDescription(scenario: ActivityScenario<WorkbenchActivity>, description: String) {
        val deadline = System.currentTimeMillis() + 10_000
        var matches = false
        while (!matches && System.currentTimeMillis() < deadline) {
            scenario.onActivity { activity ->
                matches = activity.findViewById<Button>(R.id.audio_workbench_play)
                    ?.contentDescription?.toString() == description
            }
            if (!matches) Thread.sleep(25)
        }
        assertTrue("Playback control did not become $description", matches)
    }

    private fun openSettings(scenario: ActivityScenario<WorkbenchActivity>) {
        var opened = false
        scenario.onActivity { activity ->
            val back = activity.findViewById<Button>(R.id.audio_settings_back)
            opened = if (back.isShown) back.performClick() else
                activity.findViewById<Toolbar>(R.id.audio_workbench_toolbar).menu
                    .performIdentifierAction(R.id.audio_menu_settings, 0)
        }
        assertTrue("Settings menu action was not available", opened)
    }

    private fun awaitBooks(scenario: ActivityScenario<WorkbenchActivity>, count: Int) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        if (!awaitBookCount(scenario, count, 500)) {
            var verseOpen = false
            scenario.onActivity { activity ->
                verseOpen = activity.findViewById<View>(R.id.audio_verse_close)?.isShown == true
            }
            if (verseOpen) onView(withId(R.id.audio_verse_close)).perform(click())
            if (!awaitBookCount(scenario, count, 500)) {
                onView(allOf(withText("Project001"), isDisplayed())).perform(click())
            }
        }
        assertTrue("Recording plan did not load", awaitBookCount(scenario, count, 15_000))
    }

    private fun awaitBookCount(
        scenario: ActivityScenario<WorkbenchActivity>,
        count: Int,
        timeoutMillis: Long
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        var loaded = false
        while (!loaded && System.currentTimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                loaded = activity.findViewById<Spinner>(R.id.audio_workbench_books).adapter?.count == count &&
                    activity.findViewById<Button>(R.id.audio_workbench_chapter).isEnabled
            }
            if (!loaded) Thread.sleep(50)
        }
        return loaded
    }
}
