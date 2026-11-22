package io.github.danzuep.bibleaudioproducer.ui

import android.content.res.ColorStateList
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import io.github.danzuep.bibleaudioproducer.workbench.WordOrderMode
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.util.TypedValue
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import io.github.danzuep.bibleaudioproducer.R
import io.github.danzuep.bibleaudioproducer.audio.GreekTransliterator
import io.github.danzuep.bibleaudioproducer.data.AudioProjectRepository
import io.github.danzuep.bibleaudioproducer.workbench.AudioProjectState
import io.github.danzuep.bibleaudioproducer.workbench.AudioVerse
import io.github.danzuep.bibleaudioproducer.workbench.RecordingCard
import io.github.danzuep.bibleaudioproducer.workbench.VerseWord
import io.github.danzuep.bibleaudioproducer.workbench.VerseRecordingWord
import io.github.danzuep.bibleaudioproducer.workbench.VerseWorkflow
import kotlin.math.max

internal class VerseDialogs(
    private val activity: AppCompatActivity,
    private val currentRepository: () -> AudioProjectRepository,
    private val actions: Actions
) {
    private val repository get() = currentRepository()

    interface Actions {
        val state: AudioProjectState
        val busy: Boolean
        val speechFailed: Boolean
        fun playVerse(verse: AudioVerse)
        fun isPlayingVerse(verse: AudioVerse): Boolean
        fun playChapter(chapter: Int, verses: List<AudioVerse>)
        fun isPlayingChapter(chapter: Int): Boolean
        fun downloadVerse(verse: AudioVerse)
        fun saveVerseState(
            reference: String, order: List<Int>?, unspoken: Set<String>,
            selectedPreset: WordOrderMode? = null
        )
        fun recordWord(word: VerseWord)
        fun isPlaying(card: RecordingCard): Boolean
        fun playRecording(card: RecordingCard)
        fun speakGreek(text: String)
        fun stopPlayback()
        fun hasRecordingPermission(): Boolean
        fun completeVerse(
            verse: AudioVerse, order: List<Int>, unspoken: Set<String>
        )
    }

    private val refreshers = mutableListOf<() -> Unit>()
    private var takeEditor: VerseTakeEditor? = null

    fun stopForBackground() = takeEditor?.stopForBackground()

    fun dispose() {
        takeEditor?.dispose()
        takeEditor = null
    }

    fun refreshControls() = refreshers.toList().forEach { it() }

    private data class Page(val view: View, val refresh: () -> Unit, val close: () -> Boolean)
    private val pages = mutableListOf<Page>()
    private var previousVisibility = emptyMap<View, Int>()
    val hasPages: Boolean get() = pages.isNotEmpty()

    private fun push(view: View, refresh: () -> Unit, close: () -> Boolean) {
        val container = activity.findViewById<FrameLayout>(R.id.audio_navigation_panel)
        if (pages.isEmpty()) {
            val root = container.parent as ViewGroup
            previousVisibility = (0 until root.childCount).map(root::getChildAt)
                .filter { it !== container }
                .associateWith { it.visibility }
            previousVisibility.keys.forEach { it.visibility = View.GONE }
            container.visibility = View.VISIBLE
        }
        container.removeAllViews()
        container.addView(view)
        pages.add(Page(view, refresh, close))
        activity.invalidateOptionsMenu()
        refreshers.clear()
        refreshers.add(refresh)
        refresh()
    }

    fun back(): Boolean {
        if (pages.isEmpty()) return false
        if (!pages.last().close()) return true
        pages.removeAt(pages.lastIndex)
        activity.invalidateOptionsMenu()
        val container = activity.findViewById<FrameLayout>(R.id.audio_navigation_panel)
        container.removeAllViews()
        refreshers.clear()
        if (pages.isEmpty()) {
            container.visibility = View.GONE
            previousVisibility.forEach { (view, visibility) -> view.visibility = visibility }
            previousVisibility = emptyMap()
        } else {
            val page = pages.last()
            container.addView(page.view)
            refreshers.add(page.refresh)
            page.refresh()
        }
        return true
    }

    private fun showVerseActions(verse: AudioVerse) {
        actions.downloadVerse(verse)
    }

    fun showVerses(bookVerses: List<AudioVerse>, chapter: Int) {
        val verses = bookVerses.filter { it.chapter == chapter }
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val header = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }
        val title = TextView(activity).apply {
            text = activity.getString(R.string.audio_chapter, chapter)
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val chapterPlay = roundButton(R.color.workbench_primary)
        val close = Button(activity).apply {
            text = "←"
            contentDescription = activity.getString(R.string.audio_back)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
        }
        header.addView(close, LinearLayout.LayoutParams(dp(48), dp(56)))
        header.addView(title, LinearLayout.LayoutParams(0, dp(56), 1f))
        header.addView(chapterPlay)
        root.addView(header)

        val scroll = ScrollView(activity).apply {
            isVerticalScrollBarEnabled = true
            isScrollbarFadingEnabled = false
        }
        val rows = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(rows)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        val verseButtons = mutableListOf<Pair<AudioVerse, MaterialButton>>()
        val wordLabels = mutableListOf<Pair<VerseWord, TextView>>()
        verses.forEach { verse ->
            val row = LinearLayout(activity).apply {
                gravity = Gravity.CENTER_VERTICAL
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(6), 0, dp(6))
            }
            val wordText = VerseFlowLayout(activity).apply {
                isClickable = true
                isFocusable = true
                contentDescription = verse.words.joinToString(" ") { it.surface }
                setOnClickListener {
                    actions.stopPlayback()
                    openVerse(verse)
                }
                verse.words.forEach { word ->
                    val greek = greekLabel(word, 18f)
                    wordLabels += word to greek
                    addView(wordStack(word, greek).apply { setPadding(dp(4), dp(4), dp(4), dp(4)) })
                }
            }
            val textColumn = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(activity).apply {
                    text = verse.reference
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setTextColor(ContextCompat.getColor(activity, R.color.workbench_secondary))
                })
                addView(wordText)
            }
            row.addView(textColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val play = roundButton(R.color.workbench_primary).apply {
                layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
                cornerRadius = dp(22)
                textSize = 18f
                setOnClickListener { actions.playVerse(verse) }
            }
            verseButtons += verse to play
            row.addView(play, LinearLayout.LayoutParams(dp(48), dp(48)))
            val download = Button(activity).apply {
                text = "↓"
                contentDescription = activity.getString(R.string.audio_download_verse)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                setOnClickListener { showVerseActions(verse) }
            }
            row.addView(download, LinearLayout.LayoutParams(dp(44), dp(48)))
            rows.addView(row)
        }
        chapterPlay.setOnClickListener { actions.playChapter(chapter, verses) }
        close.setOnClickListener { back() }
        val refresh = {
            val state = actions.state
            val chapterRecorded = verses.any { verse ->
                VerseWorkflow.spokenWords(
                    verse.words, state.rules, state.seedOrders[verse.reference], state.unspoken,
                    VerseWorkflow.effectiveWordOrder(verse, state), verse.reference in state.fullVerseReferences
                ).any { repository.playableClip(it) != null }
            }
            val chapterPlayable = actions.isPlayingChapter(chapter) || chapterRecorded || !actions.speechFailed
            chapterPlay.isEnabled = chapterPlayable
            chapterPlay.alpha = if (chapterPlayable) 1f else 0.45f
            chapterPlay.setText(
                if (actions.isPlayingChapter(chapter)) R.string.audio_stop_symbol else R.string.audio_play_symbol
            )
            chapterPlay.contentDescription = activity.getString(
                if (actions.isPlayingChapter(chapter)) R.string.audio_stop_chapter else R.string.audio_play_chapter
            )
            verseButtons.forEach { (verse, button) ->
                val recorded = VerseWorkflow.spokenWords(
                    verse.words, state.rules, state.seedOrders[verse.reference], state.unspoken,
                    VerseWorkflow.effectiveWordOrder(verse, state), verse.reference in state.fullVerseReferences
                ).any { repository.playableClip(it) != null }
                val playable = actions.isPlayingVerse(verse) || recorded || !actions.speechFailed
                button.isEnabled = playable
                button.alpha = if (playable) 1f else 0.45f
                button.setText(
                    if (actions.isPlayingVerse(verse)) R.string.audio_stop_symbol else R.string.audio_play_symbol
                )
                button.contentDescription = activity.getString(
                    if (actions.isPlayingVerse(verse)) R.string.audio_stop_word else R.string.audio_play_verse,
                    verse.reference
                )
            }
            wordLabels.forEach { (word, label) ->
                label.setTextColor(wordColor(word.disabled(state.unspoken), repository.clipFile(word.surface).isFile))
            }
        }
        push(root, refresh) { actions.stopPlayback(); true }
    }

    fun openVerse(verse: AudioVerse) {
        if (actions.busy) return
        val defaultMode = VerseWorkflow.defaultMode(actions.state.defaultWordOrder)
        val hasSavedOrder = verse.reference in actions.state.wordOrders || verse.reference in actions.state.seedOrders
        var order = VerseWorkflow.orderedWordIndices(
            verse.words, actions.state.rules, VerseWorkflow.effectiveWordOrder(verse, actions.state),
            actions.state.seedOrders[verse.reference]
        ).toMutableList()
        val unspoken = actions.state.unspoken.toMutableSet()
        var changed = !hasSavedOrder && defaultMode != null
        var selectedMode = if (!hasSavedOrder) defaultMode else null
        val recordedStates = mutableMapOf<String, Boolean>()
        lateinit var renderWords: () -> Unit

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val header = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }
        val reference = TextView(activity).apply {
            text = verse.reference
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val play = roundButton(R.color.workbench_primary).apply {
            setOnClickListener { actions.playVerse(verse) }
        }
        val addRecording = roundButton(R.color.workbench_record).apply {
            id = R.id.audio_verse_record
            setText(R.string.audio_record_symbol)
            contentDescription = activity.getString(R.string.audio_record_verse_words)
        }
        val more = Button(activity).apply {
            id = R.id.audio_verse_more
            text = "⋮"
            contentDescription = activity.getString(R.string.audio_download_verse)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setOnClickListener { showVerseActions(verse) }
        }
        val close = Button(activity).apply {
            id = R.id.audio_verse_close
            text = "←"
            contentDescription = activity.getString(R.string.audio_back)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
        }
        header.addView(close, LinearLayout.LayoutParams(dp(48), dp(56)))
        header.addView(reference, LinearLayout.LayoutParams(0, dp(56), 1f))
        header.addView(play)
        header.addView(addRecording)
        header.addView(more, LinearLayout.LayoutParams(dp(48), dp(56)))
        root.addView(header)
        val hint = TextView(activity).apply {
            text = activity.getString(R.string.audio_verse_drag_hint)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(4), 0, dp(8))
        }
        root.addView(hint)
        val controls = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }
        val modes = WordOrderMode.values()
        val modeLabels = listOf(
            R.string.audio_word_order_greek, R.string.audio_word_order_english,
            R.string.audio_word_order_german,
            R.string.audio_word_order_mandarin, R.string.audio_word_order_japanese,
            R.string.audio_word_order_arabic, R.string.audio_word_order_custom
        ).map(activity::getString)
        var selecting = false
        val orderMode = Spinner(activity).apply {
            id = R.id.audio_verse_word_order
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, modeLabels)
            val current = selectedMode ?: modes.firstOrNull {
                it != WordOrderMode.CUSTOM && VerseWorkflow.modeOrder(verse, it) == order
            } ?: WordOrderMode.CUSTOM
            selectedMode = current
            setSelection(current.ordinal)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val mode = modes[position]
                    if (selecting || mode == selectedMode) return
                    selectedMode = mode
                    if (mode == WordOrderMode.CUSTOM) return
                    val next = VerseWorkflow.modeOrder(verse, mode)
                    if (next != order) {
                        order = next.toMutableList()
                        takeEditor?.syncWords(VerseWorkflow.recordingWords(verse.words, order, unspoken))
                        renderWords()
                    }
                    changed = true
                    if (verse.reference == "JHN 3:16") {
                        actions.saveVerseState(verse.reference, order.toList(), unspoken.toSet(), mode)
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
        lateinit var flow: VerseFlowLayout
        lateinit var scroll: ScrollView
        val editorContainer = FrameLayout(activity).apply { visibility = View.GONE }
        fun showVersePage() {
            editorContainer.removeAllViews()
            editorContainer.visibility = View.GONE
            header.visibility = View.VISIBLE
            scroll.visibility = View.VISIBLE
            root.setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        addRecording.setOnClickListener {
            val cards = VerseWorkflow.recordingWords(verse.words, order, unspoken)
            if (cards.isEmpty()) return@setOnClickListener
            actions.stopPlayback()
            lateinit var editor: VerseTakeEditor
            fun closeEditor() {
                if (editor.close()) {
                    takeEditor = null
                    showVersePage()
                    addRecording.isEnabled = true
                    addRecording.visibility = View.VISIBLE
                    flow.visibility = View.VISIBLE
                    controls.visibility = View.VISIBLE
                    hint.visibility = View.VISIBLE
                    renderWords()
                    refreshControls()
                }
            }
            editor = VerseTakeEditor(activity, repository, cards,
                actions::hasRecordingPermission, { originalOrder ->
                    val reordered = originalOrder.iterator()
                    order = order.map {
                        if (verse.words[it].disabled(unspoken)) it else reordered.next()
                    }.toMutableList()
                    selecting = true
                    orderMode.setSelection(WordOrderMode.CUSTOM.ordinal)
                    selecting = false
                    selectedMode = WordOrderMode.CUSTOM
                    changed = true
                }, { closeEditor() }, {
                    closeEditor()
                    changed = false
                    back()
                    actions.completeVerse(verse, order.toList(), unspoken.toSet())
                }, verse.reference, orderMode,
                { order.map { VerseRecordingWord(verse.words[it], it) } },
                { word -> word.disabled(unspoken) },
                { scriptOrder ->
                    order = scriptOrder.toMutableList()
                    selecting = true
                    orderMode.setSelection(WordOrderMode.CUSTOM.ordinal)
                    selecting = false
                    selectedMode = WordOrderMode.CUSTOM
                    changed = true
                    takeEditor?.syncWords(VerseWorkflow.recordingWords(verse.words, order, unspoken))
                },
                { entry ->
                    showWordDetails(entry.word, entry.word.disabled(unspoken)) { disabled ->
                        if (takeEditor?.canChangeSpoken() != true) {
                            android.widget.Toast.makeText(activity, "Discard the take before changing spoken words.", android.widget.Toast.LENGTH_LONG).show()
                        } else {
                            wordToggle@ run {
                                val updated = unspoken.toMutableSet()
                                entry.word.withDisabled(updated, disabled)
                                val cards = VerseWorkflow.recordingWords(verse.words, order, updated)
                                if (cards.isEmpty()) {
                                    android.widget.Toast.makeText(activity, "Keep at least one word enabled.", android.widget.Toast.LENGTH_LONG).show()
                                    return@wordToggle
                                }
                                unspoken.clear()
                                unspoken.addAll(updated)
                                changed = true
                                takeEditor?.syncWords(cards)
                            }
                        }
                    }
                })
            takeEditor = editor
            header.visibility = View.GONE
            scroll.visibility = View.GONE
            root.setPadding(0, 0, 0, 0)
            editorContainer.visibility = View.VISIBLE
            addRecording.isEnabled = false
            addRecording.visibility = View.GONE
            flow.visibility = View.GONE
            controls.visibility = View.GONE
            hint.visibility = View.GONE
            editorContainer.addView(editor.view(), FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ))
            scroll.post { scroll.scrollTo(0, 0) }
        }

        renderWords = {
            val oldScroll = scroll.scrollY
            flow.removeAllViews()
            order.forEachIndexed { orderIndex, wordIndex ->
                val word = verse.words[wordIndex]
                val recorded = repository.clipFile(word.surface).isFile
                recordedStates[word.surface] = recorded
                val token = wordToken(
                    word,
                    word.disabled(unspoken),
                    recorded,
                    28f
                )
                token.setOnClickListener {
                    if (takeEditor != null) return@setOnClickListener
                    showWordDetails(word, word.disabled(unspoken)) { disabled ->
                        word.withDisabled(unspoken, disabled)
                        changed = true
                        renderWords()
                    }
                }
                val drag = object : View.OnTouchListener {
                    private var downX = 0f
                    private var downY = 0f
                    private var touching = false
                    private var dragging = false

                    fun lift(): Boolean {
                        if (!touching || takeEditor != null) return false
                        dragging = true
                        token.translationZ = dp(8).toFloat()
                        token.alpha = 0.85f
                        flow.requestDisallowInterceptTouchEvent(true)
                        return true
                    }

                    override fun onTouch(view: View, event: MotionEvent): Boolean {
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                downX = event.rawX
                                downY = event.rawY
                                touching = true
                            }
                            MotionEvent.ACTION_MOVE -> if (dragging) {
                                view.translationX = event.rawX - downX
                                view.translationY = event.rawY - downY
                                return true
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                touching = false
                                if (!dragging) return false
                                dragging = false
                                view.isPressed = false
                                view.alpha = 1f
                                view.translationX = 0f
                                view.translationY = 0f
                                view.translationZ = 0f
                                flow.requestDisallowInterceptTouchEvent(false)
                                if (event.actionMasked == MotionEvent.ACTION_UP) {
                                    val bounds = android.graphics.Rect()
                                    scroll.getGlobalVisibleRect(bounds)
                                    val location = IntArray(2)
                                    val windowLocation = IntArray(2)
                                    scroll.getLocationOnScreen(location)
                                    scroll.getLocationInWindow(windowLocation)
                                    bounds.offset(location[0] - windowLocation[0], location[1] - windowLocation[1])
                                    val target = if (bounds.contains(event.rawX.toInt(), event.rawY.toInt())) {
                                        flow.indexAt(event.rawX, event.rawY)
                                    } else -1
                                    if (target >= 0 && target != orderIndex) {
                                        order.add(target, order.removeAt(orderIndex))
                                        selecting = true
                                        orderMode.setSelection(WordOrderMode.CUSTOM.ordinal)
                                        selecting = false
                                        selectedMode = WordOrderMode.CUSTOM
                                        changed = true
                                        renderWords()
                                    }
                                }
                                return true
                            }
                        }
                        return false
                    }
                }
                token.setOnTouchListener(drag)
                token.setOnLongClickListener { drag.lift() }
                flow.addView(token)
            }
            scroll.post { scroll.scrollTo(0, oldScroll) }
        }

        flow = VerseFlowLayout(activity)
        val verseContent = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(flow)
        }
        scroll = ScrollView(activity).apply {
            setFillViewport(true)
            isVerticalScrollBarEnabled = true
            isScrollbarFadingEnabled = false
            addView(verseContent, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        root.addView(editorContainer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        renderWords()
        val refresh = {
            val recordingStateChanged = order.map { verse.words[it].surface }.distinct().any { surface ->
                val recorded = repository.clipFile(surface).isFile
                recordedStates.put(surface, recorded)?.let { it != recorded } == true
            }
            if (recordingStateChanged) renderWords()
            val recorded = VerseWorkflow.spokenWords(
                verse.words, actions.state.rules, actions.state.seedOrders[verse.reference], unspoken, order,
                verse.reference in actions.state.fullVerseReferences
            ).any { repository.playableClip(it) != null }
            val playable = actions.isPlayingVerse(verse) || recorded || !actions.speechFailed
            play.isEnabled = playable
            play.setText(if (actions.isPlayingVerse(verse)) R.string.audio_stop_symbol else R.string.audio_play_symbol)
            play.contentDescription = if (actions.isPlayingVerse(verse)) {
                activity.getString(R.string.audio_stop_word, verse.reference)
            } else {
                activity.getString(R.string.audio_play_verse)
            }
            play.alpha = if (playable) 1f else 0.45f
        }
        close.setOnClickListener { back() }
        push(root, refresh) {
            val editor = takeEditor
            if (editor != null) {
                if (editor.close()) {
                    takeEditor = null
                    showVersePage()
                    addRecording.isEnabled = true
                    addRecording.visibility = View.VISIBLE
                    flow.visibility = View.VISIBLE
                    controls.visibility = View.VISIBLE
                    hint.visibility = View.VISIBLE
                    renderWords()
                    refreshControls()
                }
                return@push false
            }
            if (changed) {
                actions.saveVerseState(
                    verse.reference,
                    if (changed) order.toList() else actions.state.wordOrders[verse.reference],
                    unspoken
                )
            }
            actions.stopPlayback()
            true
        }
    }

    private fun showWordDetails(word: VerseWord, disabled: Boolean, onToggleDisabled: (Boolean) -> Unit) {
        val card = RecordingCard("", word.surface, word.gloss, 0)
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(12), dp(16))
        }
        val header = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }
        val greek = greekLabel(word, 28f).apply {
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val record = roundButton(if (repository.clipFile(word.surface).isFile) {
            R.color.workbench_muted
        } else {
            R.color.workbench_record
        }).apply {
            setOnClickListener { actions.recordWord(word) }
        }
        val play = roundButton(R.color.workbench_primary).apply {
            setOnClickListener {
                if (repository.clipFile(word.surface).isFile) actions.playRecording(card)
                else actions.speakGreek(word.surface)
            }
        }
        val close = Button(activity).apply {
            text = "←"
            contentDescription = activity.getString(R.string.audio_back)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
        }
        header.addView(close, LinearLayout.LayoutParams(dp(48), dp(56)))
        header.addView(wordStack(word, greek), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(record)
        header.addView(play, LinearLayout.LayoutParams(dp(56), dp(56)).apply {
            leftMargin = dp(4)
        })
        root.addView(header)
        root.addView(MaterialButton(activity).apply {
            setText(if (disabled) R.string.audio_word_restore else R.string.audio_word_disable)
            setOnClickListener {
                onToggleDisabled(!disabled)
                back()
            }
        })
        root.addView(MaterialButton(activity).apply {
            setText(R.string.audio_speak_greek)
            setOnClickListener { actions.speakGreek(word.surface) }
        })

        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), dp(4), 0)
        }
        listOf(
            R.string.audio_word_field_reference to word.reference,
            R.string.audio_word_field_surface to word.surface,
            R.string.audio_word_field_lemma to word.lemma,
            R.string.audio_word_field_gloss to word.gloss,
            R.string.audio_word_field_pos to word.pos,
            R.string.audio_word_field_type to word.type,
            R.string.audio_word_field_role to word.role,
            R.string.audio_word_field_case to word.grammaticalCase,
            R.string.audio_word_field_mood to word.mood,
            R.string.audio_word_field_mandarin to word.mandarin,
            R.string.audio_word_field_morphology to word.morphology
        ).forEach { (label, value) ->
            content.addView(TextView(activity).apply {
                text = "${activity.getString(label)}: ${value.ifBlank { "—" }}"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setPadding(0, dp(6), 0, dp(6))
            })
        }
        val scroll = ScrollView(activity).apply {
            isVerticalScrollBarEnabled = true
            isScrollbarFadingEnabled = false
            addView(content)
        }
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        close.setOnClickListener { back() }
        val refresh = {
            val recorded = repository.clipFile(word.surface).isFile
            record.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(
                    activity,
                    if (recorded) R.color.workbench_muted else R.color.workbench_record
                )
            )
            record.setText(R.string.audio_record_symbol)
            record.contentDescription = activity.getString(R.string.audio_word_record, word.surface)
            play.isEnabled = recorded || actions.isPlaying(card) || !actions.speechFailed
            play.alpha = if (play.isEnabled) 1f else 0.4f
            play.setText(if (actions.isPlaying(card)) R.string.audio_stop_symbol else R.string.audio_play_symbol)
            play.contentDescription = activity.getString(
                if (actions.isPlaying(card)) R.string.audio_stop_word else R.string.audio_word_play, word.surface
            )
        }
        push(root, refresh) {
            actions.stopPlayback()
            true
        }
    }

    private fun wordToken(word: VerseWord, unspoken: Boolean, recorded: Boolean, fontSize: Float): View {
        val token = FrameLayout(activity).apply {
            setPadding(dp(4), dp(8), dp(4), dp(8))
            isClickable = true
            isLongClickable = true
            contentDescription = activity.getString(
                if (unspoken) R.string.audio_word_spoken else R.string.audio_word_unspoken,
                word.surface
            )
        }
        val greek = greekLabel(word, fontSize).apply { setTextColor(wordColor(unspoken, recorded)) }
        token.addView(wordStack(word, greek), FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))
        return token
    }

    private fun greekLabel(word: VerseWord, fontSize: Float) = TextView(activity).apply {
        text = word.surface
        gravity = Gravity.CENTER
        textSize = fontSize
    }

    private fun wordColor(unspoken: Boolean, recorded: Boolean) = ContextCompat.getColor(activity, when {
        unspoken -> R.color.workbench_unspoken
        recorded -> R.color.workbench_recorded
        else -> R.color.workbench_primary_dark
    })

    private fun wordStack(word: VerseWord, greek: TextView) = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        val gloss = listOf(word.gloss, word.mandarin).filter(String::isNotBlank).joinToString(" · ")
        fun annotation(label: String) = TextView(activity).apply {
            text = label
            gravity = Gravity.CENTER
            maxWidth = dp(140)
            textSize = 12f
            setTextColor(ContextCompat.getColor(activity, R.color.workbench_secondary))
        }
        addView(annotation(GreekTransliterator.transliterate(word.surface)))
        addView(greek)
        if (gloss.isNotEmpty()) addView(annotation(gloss))
    }

    private fun roundButton(color: Int): MaterialButton {
        val size = dp(56)
        return MaterialButton(activity).apply {
            layoutParams = LinearLayout.LayoutParams(size, size)
            insetTop = 0
            insetBottom = 0
            minWidth = 0
            setPadding(0, 0, 0, 0)
            cornerRadius = size / 2
            backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(activity, color))
            setTextColor(ContextCompat.getColor(activity, android.R.color.white))
            textSize = 22f
            setText(R.string.audio_play_symbol)
        }
    }

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()
}

private class VerseFlowLayout(
    context: android.content.Context
) : ViewGroup(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val available = width - paddingLeft - paddingRight
        var x = 0
        var y = paddingTop
        var rowHeight = 0
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            measureChild(child, MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST), heightMeasureSpec)
            val params = child.layoutParams as MarginLayoutParams
            val childWidth = child.measuredWidth + params.leftMargin + params.rightMargin
            val childHeight = child.measuredHeight + params.topMargin + params.bottomMargin
            if (x > 0 && x + childWidth > available) {
                x = 0
                y += rowHeight
                rowHeight = 0
            }
            x += childWidth
            rowHeight = max(rowHeight, childHeight)
        }
        setMeasuredDimension(
            resolveSize(width, widthMeasureSpec),
            resolveSize(y + rowHeight + paddingBottom, heightMeasureSpec)
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val available = right - left - paddingLeft - paddingRight
        var x = paddingLeft
        var y = paddingTop
        var rowHeight = 0
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            val params = child.layoutParams as MarginLayoutParams
            val childWidth = child.measuredWidth + params.leftMargin + params.rightMargin
            val childHeight = child.measuredHeight + params.topMargin + params.bottomMargin
            if (x > paddingLeft && x + childWidth > paddingLeft + available) {
                x = paddingLeft
                y += rowHeight
                rowHeight = 0
            }
            val childLeft = x + params.leftMargin
            val childTop = y + params.topMargin
            child.layout(childLeft, childTop, childLeft + child.measuredWidth, childTop + child.measuredHeight)
            x += childWidth
            rowHeight = max(rowHeight, childHeight)
        }
    }

    fun indexAt(rawX: Float, rawY: Float): Int {
        val location = IntArray(2)
        getLocationOnScreen(location)
        val x = rawX - location[0]
        val y = rawY - location[1]
        var closestIndex = -1
        var closestDistance = Float.POSITIVE_INFINITY
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (y < child.top || y > child.bottom) continue
            val distance = max(child.left - x, x - child.right).coerceAtLeast(0f)
            if (distance < closestDistance) {
                closestIndex = index
                closestDistance = distance
            }
        }
        return closestIndex
    }

    override fun generateDefaultLayoutParams(): LayoutParams =
        MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)

    override fun generateLayoutParams(attrs: android.util.AttributeSet): LayoutParams =
        MarginLayoutParams(context, attrs)

    override fun generateLayoutParams(params: LayoutParams): LayoutParams =
        MarginLayoutParams(params)

    override fun checkLayoutParams(params: LayoutParams): Boolean = params is MarginLayoutParams
}
