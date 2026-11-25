package io.github.danzuep.bibleaudioproducer.ui

import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.DragEvent
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.FrameLayout
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.MaterialColors
import com.google.android.material.card.MaterialCardView
import io.github.danzuep.bibleaudioproducer.R
import io.github.danzuep.bibleaudioproducer.audio.AndroidAudioDecoder
import io.github.danzuep.bibleaudioproducer.audio.AndroidAudioPlayer
import io.github.danzuep.bibleaudioproducer.audio.AndroidAudioRecorder
import io.github.danzuep.bibleaudioproducer.audio.GreekTransliterator
import io.github.danzuep.bibleaudioproducer.audio.VerseAudio
import io.github.danzuep.bibleaudioproducer.audio.VerseBoundaries
import io.github.danzuep.bibleaudioproducer.data.AudioProjectRepository
import io.github.danzuep.bibleaudioproducer.workbench.VerseRecordingWord
import io.github.danzuep.bibleaudioproducer.workbench.VerseWord
import java.io.File
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import java.util.concurrent.Executors

/** A take is private to this editor until the user explicitly saves each mapping. */
internal class VerseTakeEditor(
    private val activity: AppCompatActivity,
    private val repository: AudioProjectRepository,
    words: List<VerseRecordingWord>,
    private val hasPermission: () -> Boolean,
    private val onReorder: (List<Int>) -> Unit,
    private val onBack: () -> Unit,
    private val onComplete: () -> Unit,
    private val reference: String = "Verse recording",
    private val orderControl: Spinner? = null,
    private val scriptWords: (() -> List<VerseRecordingWord>)? = null,
    private val isUnspoken: (VerseWord) -> Boolean = { false },
    private val onScriptReorder: ((List<Int>) -> Unit)? = null,
    private val onWordDetails: ((VerseRecordingWord) -> Unit)? = null,
    private val onDownload: (() -> Unit)? = null,
    private val onReferenceClick: (() -> Unit)? = null
) {
    private val io = Executors.newSingleThreadExecutor()
    private val words = words.toMutableList()
    // One entry per audio segment: the words in card order, null for a separator (the first and last always are).
    private val slots = mutableListOf<VerseRecordingWord?>()
    private data class WordDrag(val originalIndex: Int)
    private val recorder = AndroidAudioRecorder(activity)
    private val player = AndroidAudioPlayer {
        playingTake = false
        pausedTake = false
        toast("Unable to play this audio.")
        if (!closed && ::playTake.isInitialized) refresh()
    }
    private var draft: File? = null
    private var preview: File? = null
    private var samples = FloatArray(0)
    private var boundaries: VerseBoundaries? = null
    private val reviewed = mutableSetOf<Int>()
    private var recording = false
    private var working = false
    private var closed = false
    private var discardConfirmed = false
    private var index = 0
    private lateinit var record: Button
    private lateinit var deck: RecyclerView
    private lateinit var cards: WordAdapter
    private lateinit var dragHelper: ItemTouchHelper
    private lateinit var next: Button
    private lateinit var tokens: ChipGroup
    private lateinit var customGuide: EditText
    private lateinit var customRow: View
    private var guideMode = 0
    private var lastGlossMode = 0
    private var customEditing = false
    private var customTokens = emptyList<Pair<String, VerseRecordingWord?>>()
    private var customOriginal = emptyList<Pair<String, VerseRecordingWord?>>()
    private lateinit var status: TextView
    private lateinit var timing: TextView
    private lateinit var help: View
    private lateinit var playTake: MaterialButton
    private lateinit var nudgeBack: Button
    private lateinit var nudgeForward: Button
    private var playingTake = false
    private var pausedTake = false
    private var reordering = false
    private lateinit var waveform: VerseSliceWaveform
    private lateinit var liveWaveform: RecordingWaveform
    private val levels = mutableListOf<Float>()
    private val waveformHandler = Handler(Looper.getMainLooper())
    private val waveformSampler = object : Runnable {
        override fun run() {
            if (!recording || closed) return
            try {
                levels.add(recorder.amplitude())
                if (levels.size > RecordingWaveform.MAX_LEVELS) levels.removeAt(0)
                liveWaveform.showLevels(levels)
                waveformHandler.postDelayed(this, 100)
            } catch (exception: RuntimeException) {
                cancelRecording("Unable to display the recording waveform. Recording cancelled.")
            }
        }
    }
    private lateinit var keep: Button

    init {
        resetSlots()
    }

    fun view(): View {
        val context = ContextThemeWrapper(activity, R.style.Theme_ClipEditor)
        val content = LayoutInflater.from(context).inflate(R.layout.activity_clip_editor, null, false)
        val toolbar = content.findViewById<MaterialToolbar>(R.id.clip_toolbar)
        toolbar.title = reference
        toolbar.post {
            (0 until toolbar.childCount).map(toolbar::getChildAt).filterIsInstance<TextView>()
                .firstOrNull { it.text.toString() == reference }?.setOnClickListener { onReferenceClick?.invoke() }
        }
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        toolbar.setNavigationContentDescription(R.string.audio_back)
        toolbar.setNavigationOnClickListener { onBack() }
        help = content.findViewById(R.id.clip_help)
        playTake = MaterialButton(context).apply {
            layoutParams = ViewGroup.LayoutParams(dp(48), dp(48))
            cornerRadius = dp(24)
            insetTop = 0
            insetBottom = 0
            setPadding(0, 0, 0, 0)
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            setIconResource(android.R.drawable.ic_media_play)
            contentDescription = "Play verse take"
            setOnClickListener { toggleTakePlayback() }
        }
        toolbar.menu.add("Play take").apply {
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            actionView = playTake
        }
        record = MaterialButton(context).apply {
            id = R.id.clip_record
            layoutParams = ViewGroup.LayoutParams(dp(48), dp(48))
            cornerRadius = dp(24)
            insetTop = 0
            insetBottom = 0
            setPadding(0, 0, 0, 0)
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            setIconResource(android.R.drawable.ic_btn_speak_now)
            setOnClickListener { toggleRecord() }
        }
        toolbar.menu.add("Record verse").apply {
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            actionView = record
        }
        toolbar.menu.add(0, R.id.clip_help_action, 0, "Info").apply {
            setIcon(android.R.drawable.ic_menu_info_details)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            setOnMenuItemClickListener {
                help.visibility = if (help.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                true
            }
        }
        toolbar.menu.add("Finish verse").setOnMenuItemClickListener {
            finishVerse()
            true
        }
        onDownload?.let { download ->
            toolbar.menu.add(0, R.id.clip_download_action, 100, R.string.audio_download_verse).apply {
                setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
                setOnMenuItemClickListener {
                    if (recording || working) toast("Finish the recording before saving the verse audio.")
                    else {
                        stopPlayback()
                        refresh()
                        download()
                    }
                    true
                }
            }
        }
        tokens = content.findViewById(R.id.clip_tokens)
        customGuide = content.findViewById(R.id.clip_custom_guide)
        customRow = content.findViewById(R.id.clip_custom_row)
        content.findViewById<View>(R.id.clip_custom_done).setOnClickListener { commitCustomGuide() }
        orderControl?.let { control ->
            (control.parent as? ViewGroup)?.removeView(control)
            content.findViewById<FrameLayout>(R.id.clip_order_container).addView(control,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        content.findViewById<Spinner>(R.id.clip_guide_mode).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item,
                listOf("Gloss: English", "Gloss: Chinese", "Greek", "Transliteration", "Custom script"))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (position == guideMode) return
                    if (position == CUSTOM_GUIDE) beginCustomGuide() else {
                        lastGlossMode = position
                        customEditing = false
                    }
                    guideMode = position
                    updateGuide()
                    cards.notifyDataSetChanged()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
        liveWaveform = content.findViewById(R.id.clip_live_waveform)
        waveform = content.findViewById<VerseSliceWaveform>(R.id.clip_waveform).apply {
            onSelect = { spoken ->
                selectCard(spoken)
            }
            onBoundary = { boundary, sample ->
                if (boundary in index..index + 1 && rememberSelection()) {
                    moveBoundary(boundary, sample)
                    showSelection()
                    refresh()
                }
            }
            setOnDragListener { _, event ->
                when (event.action) {
                    DragEvent.ACTION_DRAG_STARTED -> event.localState is WordDrag && !working && !recording && boundaries != null
                    DragEvent.ACTION_DRAG_LOCATION -> {
                        showDropTarget(segmentAt(event.x))
                        true
                    }
                    DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> {
                        showDropTarget(null)
                        true
                    }
                    DragEvent.ACTION_DROP -> {
                        val target = segmentAt(event.x)
                        showDropTarget(null)
                        val drag = event.localState as? WordDrag
                        if (target != null && drag != null) assignWord(drag.originalIndex, target) else false
                    }
                    else -> true
                }
            }
        }
        content.findViewById<View>(R.id.clip_zoom_in).setOnClickListener { waveform.zoomBy(2) }
        content.findViewById<View>(R.id.clip_zoom_out).setOnClickListener { waveform.zoomOut() }
        status = content.findViewById(R.id.clip_progress)
        timing = content.findViewById(R.id.clip_timing)
        deck = content.findViewById(R.id.clip_cards)
        cards = WordAdapter()
        deck.layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
        deck.adapter = cards
        dragHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0
        ) {
            override fun isLongPressDragEnabled() = false
            override fun onMove(recyclerView: RecyclerView, holder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val from = holder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION || working || recording) return false
                return moveCard(from, to)
            }
            override fun onSwiped(holder: RecyclerView.ViewHolder, direction: Int) = Unit
            override fun clearView(recyclerView: RecyclerView, holder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, holder)
                cards.notifyDataSetChanged()
            }
        })
        dragHelper.attachToRecyclerView(deck)
        next = content.findViewById<Button>(R.id.clip_next).apply { setOnClickListener { saveSelection() } }
        nudgeBack = content.findViewById<Button>(R.id.clip_nudge_back).apply { setOnClickListener { nudge(-1) } }
        nudgeForward = content.findViewById<Button>(R.id.clip_nudge_forward).apply { setOnClickListener { nudge(1) } }
        keep = content.findViewById<Button>(R.id.clip_keep).apply {
            setOnClickListener {
                val word = slots[index]?.word
                if (word != null && repository.clipFile(word.surface).isFile && rememberSelection()) advance()
            }
        }
        refresh()
        return content
    }

    private fun finishVerse() {
        if (samples.isNotEmpty() && boundaries != null && allReviewed()) {
            discardConfirmed = true
            onComplete()
        } else {
            refresh()
            toast("Review and save the changed clip before finishing.")
        }
    }

    private fun playExisting(position: Int) {
        val word = slots.getOrNull(position) ?: return
        stopPlayback()
        player.play(listOf(repository.clipFile(word.word.surface)))
    }

    internal fun loadDemoTake(demoSamples: FloatArray) {
        samples = demoSamples
        boundaries = VerseBoundaries(VerseAudio.suggestBoundaries(samples, words.size))
        resetSlots()
        reviewed.clear()
        showSelection()
        refresh()
    }

    private fun toggleRecord() {
        if (working) return
        if (!recording) {
            if (!hasPermission()) return
            if (samples.isNotEmpty()) {
                AlertDialog.Builder(activity).setMessage("Discard this verse take and record again?")
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok) { _, _ -> beginRecording() }.show()
            } else beginRecording()
        } else {
            recording = false
            waveformHandler.removeCallbacks(waveformSampler)
            try {
                val file = recorder.stop()
                working = true
                refresh()
                io.execute {
                    try {
                        val decoded = AndroidAudioDecoder().decode(file)
                        val suggested = VerseAudio.suggestBoundaries(decoded, words.size)
                        activity.runOnUiThread {
                            if (!closed) {
                                samples = decoded
                                boundaries = VerseBoundaries(suggested)
                                resetSlots()
                                reviewed.clear()
                                working = false
                                showSelection()
                                refresh()
                            }
                        }
                    } catch (e: Exception) {
                        activity.runOnUiThread { if (!closed) { working = false; toast(e.message ?: "Unable to decode take."); refresh() } }
                    }
                }
            } catch (e: Exception) {
                toast(e.message ?: "Unable to stop recording.")
                refresh()
            }
        }
    }

    private fun beginRecording() {
        try {
            stopPlayback()
            draft?.delete()
            val file = repository.newVerseDraft()
            draft = file
            recorder.start(file)
            recording = true
            levels.clear()
            liveWaveform.showLevels(levels)
            waveformHandler.post(waveformSampler)
            samples = FloatArray(0)
            boundaries = null
            resetSlots()
            reviewed.clear()
            refresh()
        } catch (e: Exception) {
            toast(e.message ?: "Unable to start recording.")
            refresh()
        }
    }

    private fun refresh() {
        val ready = samples.isNotEmpty() && !working && !recording
        (record as MaterialButton).setIconResource(if (recording) android.R.drawable.ic_media_pause else android.R.drawable.ic_btn_speak_now)
        record.contentDescription = if (recording) "Stop recording" else if (samples.isEmpty()) "Record verse" else "Record again"
        record.isEnabled = !working
        orderControl?.isEnabled = !working && !recording
        waveform.isEnabled = ready
        waveform.visibility = if (recording) View.INVISIBLE else View.VISIBLE
        liveWaveform.visibility = if (recording) View.VISIBLE else View.GONE
        val existing = slots[index]?.let { repository.clipFile(it.word.surface).isFile } == true
        next.isEnabled = ready
        keep.isEnabled = ready && existing
        keep.visibility = if (keep.isEnabled) View.VISIBLE else View.GONE
        nudgeBack.isEnabled = ready
        nudgeForward.isEnabled = nudgeBack.isEnabled
        val edge = if (index < slots.lastIndex) "end" else "start"
        nudgeBack.contentDescription = "Move $edge boundary back 10 milliseconds"
        nudgeForward.contentDescription = "Move $edge boundary forward 10 milliseconds"
        playTake.isEnabled = ready
        playTake.setIconResource(if (playingTake && !pausedTake) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
        playTake.contentDescription = if (playingTake && !pausedTake) "Pause verse take" else "Play verse take"
        status.text = if (recording) "Recording…" else "${reviewed.size} / ${words.size} reviewed"
        val points = boundaries
        timing.text = if (points == null) "No recording" else {
            val first = points.start(index)
            val last = points.end(index)
            val secondsFormat = DecimalFormat("0.#######", DecimalFormatSymbols(Locale.US))
            val millisFormat = DecimalFormat("0.####", DecimalFormatSymbols(Locale.US))
            "${secondsFormat.format(first.toDouble() / VerseAudio.SAMPLE_RATE)}s – " +
                "${secondsFormat.format(last.toDouble() / VerseAudio.SAMPLE_RATE)}s " +
                "(${millisFormat.format((last - first) * 1000.0 / VerseAudio.SAMPLE_RATE)} ms)"
        }
        if (!reordering) cards.notifyDataSetChanged()
        updateGuide()
        showWaveform()
    }

    /** Re-reads state that changed outside the editor, such as clips recorded from the word details. */
    fun refreshExternal() {
        if (::cards.isInitialized && !closed && !recording && !working) refresh()
    }

    private fun showWaveform() {
        waveform.muted = slots.indices.filter { slots[it] == null }.toSet()
        waveform.show(samples, boundaries?.snapshot().orEmpty(), index)
    }

    private fun resetSlots() {
        slots.clear()
        slots.add(null)
        slots.addAll(words)
        slots.add(null)
        reviewed.clear()
        index = 1
    }

    private fun allReviewed() = slots.indices.all { slots[it] == null || it in reviewed }

    private fun selectCard(next: Int) {
        if (working || recording || next !in slots.indices || next == index) return
        if (boundaries != null && !rememberSelection()) return
        index = next
        showSelection()
        refresh()
        scrollToSelection()
    }

    fun canChangeSpoken(): Boolean = samples.isEmpty() && !recording && !working

    fun syncWords(updated: List<VerseRecordingWord>) {
        require(updated.isNotEmpty())
        val selected = slots[index]?.originalIndex
        val changed = words.map { it.originalIndex } != updated.map { it.originalIndex }
        if (changed) {
            require(boundaries == null || updated.size == words.size)
            words.clear()
            words.addAll(updated)
            if (boundaries == null) {
                resetSlots()
            } else {
                val replacements = updated.iterator()
                slots.indices.forEach { if (slots[it] != null) slots[it] = replacements.next() }
                reviewed.clear()
            }
            if (selected != null) index = slots.indexOfFirst { it?.originalIndex == selected }.coerceAtLeast(1)
        }
        if (::cards.isInitialized) {
            showSelection()
            refresh()
        }
    }

    private fun moveCard(from: Int, to: Int): Boolean {
        if (working || recording || from == to || slots.getOrNull(from) == null || to !in 1 until slots.lastIndex) return false
        if (boundaries != null && !rememberSelection()) return false
        slots.add(to, slots.removeAt(from))
        words.clear()
        words.addAll(slots.filterNotNull())
        if (boundaries != null) {
            invalidateSlices((minOf(from, to)..maxOf(from, to)).toSet())
        }
        index = when {
            index == from -> to
            from < to && index in from + 1..to -> index - 1
            from > to && index in to until from -> index + 1
            else -> index
        }
        onReorder(words.map { it.originalIndex })
        cards.notifyItemMoved(from, to)
        showSelection()
        reordering = true
        refresh()
        reordering = false
        return true
    }

    private fun assignWord(originalIndex: Int, target: Int): Boolean {
        if (working || recording || boundaries == null || target !in 1 until slots.lastIndex) return false
        val source = slots.indexOfFirst { it?.originalIndex == originalIndex }
        if (source < 0) return false
        if (source == target) return true
        if (!moveCard(source, target)) return false
        index = target
        showSelection()
        refresh()
        scrollToSelection()
        toast("Card moved. Review the affected clips again.")
        return true
    }

    private inner class WordAdapter : RecyclerView.Adapter<WordAdapter.Holder>() {
        inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val card = view.findViewById<MaterialCardView>(R.id.word_card)
            val insert = view.findViewById<Button>(R.id.word_insert)
            val playExisting = view.findViewById<Button>(R.id.word_play_existing)
            val label = view.findViewById<TextView>(R.id.word_label)
            val greek = view.findViewById<TextView>(R.id.word_greek)
            val position = view.findViewById<TextView>(R.id.word_position)
            val metadata = view.findViewById<Chip>(R.id.word_metadata)
            val play = view.findViewById<Button>(R.id.word_play)
            val drag = view.findViewById<View>(R.id.word_drag)
            val assign = view.findViewById<Button>(R.id.word_assign)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_word_card, parent, false))

        override fun getItemCount() = slots.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val word = slots[position]
            val color = VerseSliceWaveform.color(position, 255, word == null)
            val ready = samples.isNotEmpty() && !working && !recording
            val name = word?.let { targetLabel(it) } ?: BLANK_LABEL
            val edge = position == 0 || position == slots.lastIndex
            holder.card.strokeWidth = dp(if (position == index) 3 else 1)
            holder.card.strokeColor = if (position == index)
                MaterialColors.getColor(holder.card, com.google.android.material.R.attr.colorPrimary) else color
            val role = when {
                word != null -> word.word.surface
                position == 0 -> "start trim"
                edge -> "end trim"
                else -> "separator"
            }
            holder.card.contentDescription = "$name, $role, card ${position + 1}"
            holder.label.text = word?.let { cardLabel(it) } ?: BLANK_LABEL
            val hasExisting = word != null && repository.clipFile(word.word.surface).isFile
            holder.playExisting.visibility = if (hasExisting) View.VISIBLE else View.GONE
            holder.playExisting.isEnabled = !working && !recording
            holder.playExisting.setOnClickListener {
                val current = holder.bindingAdapterPosition
                if (current != RecyclerView.NO_POSITION) playExisting(current)
            }
            holder.insert.visibility = if (position == 0) View.GONE else View.VISIBLE
            holder.insert.isEnabled = true
            holder.insert.alpha = if (ready) 1f else 0.5f
            holder.insert.setOnClickListener {
                if (!ready) {
                    toast("Record the verse before inserting a separator.")
                    return@setOnClickListener
                }
                val current = holder.bindingAdapterPosition
                if (current != RecyclerView.NO_POSITION && (current == index || selectCardForPreview(current))) insertSeparator()
            }
            holder.greek.text = when {
                word != null -> word.word.surface
                position == 0 -> "Start"
                edge -> "End"
                else -> "Skipped"
            }
            holder.position.text = if (word != null) {
                "Card ${slots.take(position + 1).count { it != null }} • Greek ${word.originalIndex + 1}"
            } else if (edge) "Trim" else "Cut out"
            holder.metadata.chipBackgroundColor = ColorStateList.valueOf(VerseSliceWaveform.color(position, 55, word == null))
            val points = boundaries
            holder.metadata.text = if (points == null) "No take" else
                "${(points.end(position) - points.start(position)) * 1000L / VerseAudio.SAMPLE_RATE} ms"
            holder.play.isEnabled = ready
            holder.play.contentDescription = "Play $name slice"
            holder.card.setOnClickListener {
                val current = holder.bindingAdapterPosition
                if (current != RecyclerView.NO_POSITION) selectCard(current)
            }
            holder.card.setOnLongClickListener {
                if (word == null || !ready || !rememberSelection()) return@setOnLongClickListener false
                val drag = WordDrag(word.originalIndex)
                androidx.core.view.ViewCompat.startDragAndDrop(holder.card, null, View.DragShadowBuilder(holder.card), drag, 0)
            }
            holder.drag.visibility = if (word != null) View.VISIBLE else View.GONE
            holder.assign.visibility = if (edge) View.INVISIBLE else View.VISIBLE
            holder.assign.text = if (word != null) "✓" else "✕"
            holder.assign.isEnabled = ready
            holder.assign.contentDescription = if (word != null) "Save $name clip and next" else "Remove separator"
            holder.assign.setTextColor(androidx.core.content.ContextCompat.getColor(activity,
                if (word != null && repository.clipFile(word.word.surface).isFile) R.color.workbench_recorded
                else R.color.workbench_primary))
            holder.assign.setOnClickListener {
                val current = holder.bindingAdapterPosition
                if (current == RecyclerView.NO_POSITION) return@setOnClickListener
                if (slots[current] == null) removeSeparator(current)
                else if (current == index || selectCardForPreview(current)) saveSelection()
            }
            holder.play.setOnClickListener {
                val current = holder.bindingAdapterPosition
                if (current != RecyclerView.NO_POSITION && (current == index || selectCardForPreview(current))) previewSelection()
            }
            holder.drag.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN && !working && !recording) {
                    dragHelper.startDrag(holder)
                }
                false
            }
        }
    }

    private fun showSelection() {
        if (boundaries == null) return
        showWaveform()
        val existing = slots[index]?.let { repository.clipFile(it.word.surface).isFile } == true
        keep.isEnabled = existing
    }

    private fun invalidateSlices(changed: Set<Int>) {
        reviewed.removeAll(changed)
    }

    private fun selection(): Pair<Int, Int> {
        val points = checkNotNull(boundaries)
        return points.start(index) to points.end(index)
    }

    private fun previewSelection() {
        if (!rememberSelection()) return
        val (first, last) = selection()
        previewSamples(first, last)
        refresh()
    }

    private fun rememberSelection(): Boolean {
        return boundaries != null
    }

    private fun previewSamples(first: Int, last: Int) {
        try {
            stopPlayback()
            preview?.delete()
            val file = repository.newVersePreview()
            preview = file
            file.writeBytes(VerseAudio.encodeWav(VerseAudio.slice(samples, first, last)))
            player.play(listOf(file))
        } catch (e: Exception) {
            toast(e.message ?: "Unable to preview selection.")
        }
    }

    private fun toggleTakePlayback() {
        if (playingTake) {
            if (pausedTake) {
                if (player.resume()) pausedTake = false
            } else if (player.pause()) {
                pausedTake = true
            }
        } else {
            try {
                stopPlayback()
                preview?.delete()
                val file = repository.newVersePreview()
                preview = file
                val points = checkNotNull(boundaries)
                val first = points.start(1)
                val last = points.end(slots.lastIndex - 1)
                file.writeBytes(VerseAudio.encodeWav(samples.copyOfRange(first, last)))
                playingTake = true
                player.play(listOf(file)) {
                    if (!closed) {
                        playingTake = false
                        pausedTake = false
                        refresh()
                    }
                }
            } catch (e: Exception) {
                playingTake = false
                toast(e.message ?: "Unable to play the verse take.")
            }
        }
        refresh()
    }

    private fun stopPlayback() {
        player.stop()
        playingTake = false
        pausedTake = false
    }

    private fun nudge(direction: Int) {
        if (!rememberSelection()) return
        val points = boundaries ?: return
        val boundary = if (index < slots.lastIndex) index + 1 else index
        val snapshot = points.snapshot()
        val target = (snapshot[boundary] + direction * VerseAudio.SAMPLE_RATE / 100)
            .coerceIn(snapshot[boundary - 1] + 1, snapshot[boundary + 1] - 1)
        invalidateSlices(points.move(boundary, target))
        showSelection()
        refresh()
    }

    /**
     * Drag a boundary. It cannot pass the one before it, but it pushes the boundaries after it along,
     * and those are suggested again for the audio that is left.
     */
    private fun moveBoundary(boundary: Int, sample: Int) {
        val points = boundaries ?: return
        val snapshot = points.snapshot()
        val finalStart = snapshot.lastIndex - 1
        if (boundary !in 1..finalStart) return
        val low = snapshot[boundary - 1] + 1
        val tail = (boundary until finalStart).map { if (slots[it] == null) SEPARATOR_WEIGHT else 1.0 }
        if (tail.isEmpty()) {
            invalidateSlices(points.move(boundary, sample.coerceIn(low, samples.size - 1)))
            return
        }
        val minimum = ((samples.size - 1 - low) / (tail.size + 1)).coerceIn(1, MINIMUM_CLIP)
        val high = samples.size - 1 - tail.size * minimum
        if (high < low) return
        val target = sample.coerceIn(low, high)
        val regionEnd = maxOf(snapshot[finalStart], target + tail.size * minimum).coerceAtMost(samples.size - 1)
        invalidateSlices(points.replaceFrom(boundary, VerseAudio.splitRegion(samples, target, regionEnd, tail)))
    }

    private fun insertSeparator() {
        if (!rememberSelection() || working || recording) return
        val points = boundaries ?: return
        if (index == 0) {
            toast("The first · card already covers the audio before the words.")
            return
        }
        val start = points.start(index)
        val end = points.end(index)
        if (end - start < 2) {
            toast("This clip is too short to add a separator before it.")
            return
        }
        points.split(index, start + (end - start) / 2)
        slots.add(index, null)
        val shifted = reviewed.filter { it != index }.map { if (it >= index) it + 1 else it }
        reviewed.clear()
        reviewed.addAll(shifted)
        index++
        refresh()
        scrollToSelection()
    }

    private fun removeSeparator(position: Int) {
        val points = boundaries ?: return
        if (working || recording || slots.getOrNull(position) != null || position !in 1 until slots.lastIndex) return
        points.merge(position + 1)
        slots.removeAt(position)
        val shifted = reviewed.filter { it != position + 1 }.map { if (it > position + 1) it - 1 else it }
        reviewed.clear()
        reviewed.addAll(shifted)
        if (index > position) index--
        refresh()
    }

    private fun saveSelection() {
        if (!rememberSelection()) return
        refresh()
        val word = slots[index]?.word
        if (word == null) {
            advance()
            return
        }
        val (first, last) = selection()
        fun save() {
            val candidate = repository.recordingCandidateFile(word.surface)
            if (candidate.exists()) {
                toast("A pending word take must be reviewed before replacing this clip.")
                return
            }
            working = true
            stopPlayback()
            refresh()
            val part = VerseAudio.slice(samples, first, last)
            io.execute {
                var written = false
                try {
                    check(candidate.parentFile?.let { it.mkdirs() || it.isDirectory } == true)
                    candidate.writeBytes(VerseAudio.encodeWav(part))
                    written = true
                    repository.commitRecording(word.surface, "audio/wav")
                    activity.runOnUiThread {
                        if (!closed) {
                            working = false
                            advance()
                        }
                    }
                } catch (e: Exception) {
                    // Keep the candidate if committing failed so that the take can be recovered.
                    if (!written) candidate.delete()
                    activity.runOnUiThread {
                        if (!closed) { working = false; toast(e.message ?: "Unable to save clip."); refresh() }
                    }
                }
            }
        }
        if (repository.clipFile(word.surface).isFile) {
            AlertDialog.Builder(activity).setMessage(
                "Replace the existing recording for ${word.surface}? This form is shared across occurrences and verses."
            )
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Replace") { _, _ -> save() }.show()
        } else save()
    }

    private fun advance() {
        if (slots[index] != null) reviewed.add(index)
        val next = (1..slots.size).map { (index + it) % slots.size }.firstOrNull { slots[it] != null && it !in reviewed }
        if (next != null) index = next
        refresh()
        showSelection()
        scrollToSelection()
    }

    fun close(): Boolean {
        if (working) return false
        if (!discardConfirmed && (recording || samples.isNotEmpty())) {
            AlertDialog.Builder(activity)
                .setMessage("Discard the verse take? Saved clips will be kept.")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Discard") { _, _ ->
                    discardConfirmed = true
                    onBack()
                }.show()
            return false
        }
        dispose()
        return true
    }

    fun dispose() {
        if (closed) return
        closed = true
        waveformHandler.removeCallbacks(waveformSampler)
        if (recording) {
            recording = false
            runCatching { recorder.stop() }
        }
        stopPlayback()
        io.execute {
            draft?.delete()
            preview?.delete()
        }
        io.shutdown()
    }

    fun stopForBackground() {
        val wasPlayingTake = playingTake
        stopPlayback()
        if (!recording) {
            if (wasPlayingTake && !closed) refresh()
            return
        }
        cancelRecording("Recording cancelled because the app left the foreground.")
    }

    private fun cancelRecording(message: String) {
        recording = false
        waveformHandler.removeCallbacks(waveformSampler)
        runCatching { recorder.stop() }
        draft?.delete()
        draft = null
        toast(message)
        refresh()
    }

    private fun selectCardForPreview(position: Int): Boolean {
        selectCard(position)
        return index == position
    }

    private fun scrollToSelection() {
        deck.smoothScrollToPosition(index)
    }

    private fun guideLabel(word: VerseRecordingWord, mode: Int) = when (mode) {
        1 -> word.word.mandarin
        2 -> word.word.surface
        3 -> GreekTransliterator.transliterate(word.word.surface)
        else -> word.word.gloss
    }.ifBlank { BLANK_LABEL }

    // The card title follows the script selection, with the Greek selection shown as transliteration.
    private fun cardLabel(word: VerseRecordingWord): String {
        val mode = if (guideMode == CUSTOM_GUIDE) lastGlossMode else guideMode
        return guideLabel(word, if (mode == 2) 3 else mode)
    }

    private fun guideWords() = scriptWords?.invoke() ?: words

    private fun beginCustomGuide() {
        customOriginal = guideWords().flatMap { word ->
            guideLabel(word, lastGlossMode).split(WHITESPACE).filter(String::isNotEmpty).map { it to word }
        }
        customEditing = true
        customGuide.setText(customOriginal.joinToString(" ") { it.first })
    }

    private fun commitCustomGuide() {
        val edited = customGuide.text.toString().split(WHITESPACE).filter(String::isNotEmpty)
        customTokens = linkGuideTokens(customOriginal, edited)
        customEditing = false
        (activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)
            ?.hideSoftInputFromWindow(customGuide.windowToken, 0)
        updateGuide()
    }

    private fun updateGuide() {
        val custom = guideMode == CUSTOM_GUIDE
        customRow.visibility = if (custom && customEditing) View.VISIBLE else View.GONE
        tokens.visibility = if (custom && customEditing) View.GONE else View.VISIBLE
        if (custom && customEditing) return
        tokens.removeAllViews()
        val entries = if (custom) customTokens else guideWords().map { guideLabel(it, guideMode) to it }
        entries.forEach { (label, word) -> tokens.addView(guideChip(label, word)) }
        if (custom) tokens.addView(smallChip("✎").apply {
            contentDescription = "Edit custom script"
            isClickable = true
            setOnClickListener {
                customOriginal = customTokens
                customEditing = true
                customGuide.setText(customTokens.joinToString(" ") { it.first })
                updateGuide()
                customGuide.requestFocus()
            }
        })
    }

    private fun smallChip(label: String) = Chip(tokens.context).apply {
        text = label
        isCheckable = false
        setEnsureMinTouchTargetSize(false)
        chipMinHeight = dp(28).toFloat()
        textSize = 14f
        isSingleLine = true
        includeFontPadding = false
    }

    /** A word is linked to its card; the words a user types into a custom script are not. */
    private fun guideChip(label: String, word: VerseRecordingWord?): Chip = smallChip(label).apply {
        val spoken = word?.let { linked -> slots.indexOfFirst { it?.originalIndex == linked.originalIndex } } ?: -1
        val isActive = spoken >= 0 && spoken == index
        val primary = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary)
        val surface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface)
        val onPrimary = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnPrimary)
        val onSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface)
        chipBackgroundColor = ColorStateList.valueOf(
            if (isActive) primary else if (spoken in reviewed)
                MaterialColors.layer(surface, VerseSliceWaveform.color(spoken), 0.25f) else surface
        )
        chipStrokeColor = ColorStateList.valueOf(if (isActive) primary else if (spoken >= 0) VerseSliceWaveform.color(spoken) else onSurface)
        chipStrokeWidth = dp(if (isActive) 2 else 1).toFloat()
        val recorded = word != null && repository.clipFile(word.word.lemma).isFile
        setTextColor(when {
            isActive -> onPrimary
            recorded -> androidx.core.content.ContextCompat.getColor(activity, R.color.workbench_recorded)
            else -> onSurface
        })
        if (word == null) {
            isClickable = false
            contentDescription = "$label, not linked to a card"
            return@apply
        }
        isClickable = true
        alpha = if (isUnspoken(word.word)) 0.5f else 1f
        contentDescription = "$label, ${if (spoken < 0) "disabled" else if (isActive) "selected" else if (spoken in reviewed) "reviewed" else "unreviewed"}"
        setOnClickListener {
            if (working || recording) toast("Stop recording before opening word details.")
            else if (onWordDetails != null) {
                stopPlayback()
                refresh()
                onWordDetails.invoke(word)
            }
            else selectCard(spoken)
        }
        setOnLongClickListener {
            if (working || recording) return@setOnLongClickListener false
            val drag = WordDrag(word.originalIndex)
            androidx.core.view.ViewCompat.startDragAndDrop(this, null, View.DragShadowBuilder(this), drag, 0)
        }
        setOnDragListener { _, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> event.localState is WordDrag && !working && !recording
                DragEvent.ACTION_DROP -> {
                    val drag = event.localState as? WordDrag
                    val order = guideWords().map { it.originalIndex }.toMutableList()
                    val from = order.indexOf(drag?.originalIndex)
                    val to = order.indexOf(word.originalIndex)
                    if (from >= 0 && to >= 0 && from != to) {
                        order.add(to, order.removeAt(from))
                        if (onScriptReorder != null) onScriptReorder.invoke(order)
                        else moveCard(slots.indexOfFirst { it?.originalIndex == drag?.originalIndex }, spoken)
                    }
                    true
                }
                else -> true
            }
        }
    }

    private fun targetLabel(word: VerseRecordingWord) =
        word.word.gloss.ifBlank { word.word.mandarin.ifBlank { BLANK_LABEL } }
    private fun toast(message: String) = Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
}

private const val BLANK_LABEL = "\u00B7"
private const val CUSTOM_GUIDE = 4
private const val SEPARATOR_WEIGHT = 0.25
private const val MINIMUM_CLIP = VerseAudio.SAMPLE_RATE / 100
private val WHITESPACE = Regex("\\s+")

/** Keeps the link to a card for the words that survive an edit; inserted words get none. */
internal fun <T> linkGuideTokens(original: List<Pair<String, T>>, edited: List<String>): List<Pair<String, T?>> {
    val common = Array(original.size + 1) { IntArray(edited.size + 1) }
    for (i in original.indices.reversed()) for (j in edited.indices.reversed()) {
        common[i][j] = if (original[i].first == edited[j]) common[i + 1][j + 1] + 1
        else maxOf(common[i + 1][j], common[i][j + 1])
    }
    val result = mutableListOf<Pair<String, T?>>()
    var i = 0
    var j = 0
    while (j < edited.size) {
        if (i < original.size && original[i].first == edited[j]) {
            result.add(edited[j] to original[i].second)
            i++
            j++
        } else if (i < original.size && common[i + 1][j] >= common[i][j + 1]) {
            i++
        } else {
            result.add(edited[j] to null)
            j++
        }
    }
    return result
}
