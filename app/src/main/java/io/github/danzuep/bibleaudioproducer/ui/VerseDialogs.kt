package io.github.danzuep.bibleaudioproducer.ui

import android.content.res.ColorStateList
import android.view.Gravity
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
        fun bookCodes(): List<String>
        fun versesOf(book: String): List<AudioVerse>
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
        val initial = VerseWorkflow.recordingWords(verse.words, order, unspoken)
        if (initial.isEmpty()) {
            android.widget.Toast.makeText(activity, R.string.audio_verse_no_words, android.widget.Toast.LENGTH_LONG).show()
            return
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
                    }
                    changed = true
                    if (verse.reference == "JHN 3:16") {
                        actions.saveVerseState(verse.reference, order.toList(), unspoken.toSet(), mode)
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
        fun markCustomOrder() {
            selecting = true
            orderMode.setSelection(WordOrderMode.CUSTOM.ordinal)
            selecting = false
            selectedMode = WordOrderMode.CUSTOM
            changed = true
        }
        actions.stopPlayback()
        val editor = VerseTakeEditor(activity, repository, initial,
            actions::hasRecordingPermission, { originalOrder ->
                val reordered = originalOrder.iterator()
                order = order.map {
                    if (verse.words[it].disabled(unspoken)) it else reordered.next()
                }.toMutableList()
                markCustomOrder()
            }, { back() }, {
                changed = false
                back()
                actions.completeVerse(verse, order.toList(), unspoken.toSet())
            }, verse.reference, orderMode,
            { order.map { VerseRecordingWord(verse.words[it], it) } },
            { word -> word.disabled(unspoken) },
            { scriptOrder ->
                order = scriptOrder.toMutableList()
                markCustomOrder()
                takeEditor?.syncWords(VerseWorkflow.recordingWords(verse.words, order, unspoken))
            },
            { entry ->
                showWordDetails(entry.word, entry.word.disabled(unspoken)) { disabled ->
                    if (takeEditor?.canChangeSpoken() != true) {
                        android.widget.Toast.makeText(activity, "Discard the take before changing spoken words.", android.widget.Toast.LENGTH_LONG).show()
                    } else {
                        val updated = unspoken.toMutableSet()
                        entry.word.withDisabled(updated, disabled)
                        val cards = VerseWorkflow.recordingWords(verse.words, order, updated)
                        if (cards.isEmpty()) {
                            android.widget.Toast.makeText(activity, "Keep at least one word enabled.", android.widget.Toast.LENGTH_LONG).show()
                        } else {
                            unspoken.clear()
                            unspoken.addAll(updated)
                            changed = true
                            takeEditor?.syncWords(cards)
                        }
                    }
                }
            }, { showVerseActions(verse) }, {
                VerseNavigator(activity, actions.bookCodes(), actions::versesOf) { target ->
                    val depth = pages.size
                    back()
                    if (pages.size < depth) openVerse(target)
                }.show(verse.reference)
            })
        takeEditor = editor
        push(editor.view(), { editor.refreshExternal() }) {
            if (!editor.close()) return@push false
            takeEditor = null
            if (changed) actions.saveVerseState(verse.reference, order.toList(), unspoken)
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
                setTextIsSelectable(true)
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

    override fun generateDefaultLayoutParams(): LayoutParams =
        MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)

    override fun generateLayoutParams(attrs: android.util.AttributeSet): LayoutParams =
        MarginLayoutParams(context, attrs)

    override fun generateLayoutParams(params: LayoutParams): LayoutParams =
        MarginLayoutParams(params)

    override fun checkLayoutParams(params: LayoutParams): Boolean = params is MarginLayoutParams
}
