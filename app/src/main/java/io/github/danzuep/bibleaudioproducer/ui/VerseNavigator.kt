package io.github.danzuep.bibleaudioproducer.ui

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.app.Dialog
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import io.github.danzuep.bibleaudioproducer.R
import androidx.appcompat.app.AppCompatActivity
import io.github.danzuep.bibleaudioproducer.workbench.AudioVerse

/**
 * Book and chapter list on the left; once a chapter is chosen the left side narrows and the
 * verses of that chapter fill the right.
 */
internal class VerseNavigator(
    private val activity: AppCompatActivity,
    private val books: List<String>,
    private val versesOf: (String) -> List<AudioVerse>,
    private val onPick: (AudioVerse) -> Unit
) {
    private val dialog = Dialog(activity, R.style.Theme_ClipEditor)
    private val leftPane = ScrollView(activity)
    private val rightPane = ScrollView(activity)
    private val bookList = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private var chapter: Int? = null
    private val chapterGrids = mutableMapOf<String, GridLayout>()

    init {
        leftPane.addView(bookList)
        val panes = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(leftPane, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            addView(rightPane, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
        rightPane.visibility = View.GONE
        books.forEach(::addBook)
        val toolbar = MaterialToolbar(activity).apply {
            title = "Select verse"
            setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
            setNavigationContentDescription(R.string.audio_back)
            setNavigationOnClickListener { dialog.dismiss() }
        }
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
            addView(toolbar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(panes, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        dialog.setContentView(root)
    }

    fun show(reference: String) {
        val current = reference.substringBefore(' ')
        if (current in books) {
            expand(current)
            reference.substringAfter(' ').substringBefore(':').toIntOrNull()?.let { selectChapter(current, it) }
        }
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    private fun addBook(code: String) {
        bookList.addView(TextView(activity).apply {
            text = code
            textSize = 18f
            minHeight = dp(48)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(8), 0)
            contentDescription = "Book $code"
            setOnClickListener { chapter = null; expand(code) }
        })
        val grid = GridLayout(activity).apply { visibility = View.GONE; columnCount = 2 }
        chapterGrids[code] = grid
        bookList.addView(grid)
    }

    private fun expand(code: String) {
        chapterGrids.forEach { (other, grid) -> if (other != code) grid.visibility = View.GONE }
        val grid = chapterGrids.getValue(code)
        if (grid.childCount == 0) fillChapters(code, null)
        grid.visibility = if (grid.visibility == View.VISIBLE && chapter == null) View.GONE else View.VISIBLE
        if (chapter == null) {
            leftPane.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            rightPane.visibility = View.GONE
        }
    }

    private fun fillChapters(code: String, selected: Int?) {
        val grid = chapterGrids.getValue(code)
        grid.removeAllViews()
        grid.columnCount = if (selected == null) 5 else 2
        versesOf(code).map { it.chapter }.distinct().sorted().forEach { number ->
            grid.addView(numberButton("$number:", number == selected) { selectChapter(code, number) })
        }
    }

    private fun selectChapter(code: String, number: Int) {
        chapter = number
        chapterGrids.keys.filter { it != code }.forEach { chapterGrids.getValue(it).visibility = View.GONE }
        fillChapters(code, number)
        chapterGrids.getValue(code).visibility = View.VISIBLE
        leftPane.layoutParams = LinearLayout.LayoutParams(dp(100), ViewGroup.LayoutParams.MATCH_PARENT)
        rightPane.visibility = View.VISIBLE
        rightPane.removeAllViews()
        rightPane.addView(GridLayout(activity).apply {
            columnCount = 5
            setPadding(dp(4), dp(4), dp(4), dp(4))
            versesOf(code).filter { it.chapter == number }.forEach { verse ->
                val label = verse.reference.substringAfter(':').substringBefore('!')
                addView(numberButton(label, false) {
                    dialog.dismiss()
                    onPick(verse)
                })
            }
        })
    }

    private fun numberButton(label: String, selected: Boolean, click: () -> Unit) = MaterialButton(
        activity, null,
        if (selected) com.google.android.material.R.attr.materialButtonStyle
        else com.google.android.material.R.attr.materialButtonOutlinedStyle
    ).apply {
        text = label
        textSize = 14f
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = 0
        insetTop = 0
        insetBottom = 0
        setPadding(0, 0, 0, 0)
        contentDescription = if (selected) "Chapter $label selected" else label
        layoutParams = GridLayout.LayoutParams().apply {
            width = dp(44); height = dp(44)
            setMargins(dp(2), dp(2), dp(2), dp(2))
        }
        setOnClickListener { click() }
    }

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
}
