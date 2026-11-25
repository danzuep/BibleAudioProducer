package io.github.danzuep.bibleaudioproducer.ui

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
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
    private val dialog: AlertDialog
    private val leftPane = ScrollView(activity)
    private val rightPane = ScrollView(activity)
    private val bookList = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private var book: String? = null
    private var chapter: Int? = null
    private val chapterGrids = mutableMapOf<String, GridLayout>()

    init {
        leftPane.addView(bookList)
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(leftPane, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            addView(rightPane, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
        rightPane.visibility = View.GONE
        books.forEach(::addBook)
        dialog = AlertDialog.Builder(activity).setView(root)
            .setNegativeButton(android.R.string.cancel, null).create()
    }

    fun show(reference: String) {
        val current = reference.substringBefore(' ')
        if (current in books) {
            expand(current)
            reference.substringAfter(' ').substringBefore(':').toIntOrNull()?.let { selectChapter(current, it) }
        }
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, dp(480))
    }

    private fun addBook(code: String) {
        bookList.addView(TextView(activity).apply {
            text = code
            textSize = 18f
            minHeight = dp(48)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(8), 0)
            contentDescription = "Book $code"
            setOnClickListener { expand(code) }
        })
        val grid = GridLayout(activity).apply { visibility = View.GONE }
        chapterGrids[code] = grid
        bookList.addView(grid)
    }

    private fun expand(code: String) {
        book = code
        chapterGrids.forEach { (other, grid) -> if (other != code) grid.visibility = View.GONE }
        val grid = chapterGrids.getValue(code)
        if (grid.childCount == 0) {
            val chapters = versesOf(code).map { it.chapter }.distinct().sorted()
            chapters.forEach { number -> grid.addView(numberButton(number) { selectChapter(code, number) }) }
        }
        grid.columnCount = if (chapter == null) 5 else 2
        grid.visibility = if (grid.visibility == View.VISIBLE && chapter == null) View.GONE else View.VISIBLE
    }

    private fun selectChapter(code: String, number: Int) {
        book = code
        chapter = number
        val chapterGrid = chapterGrids.getValue(code)
        if (chapterGrid.childCount == 0) expand(code)
        chapterGrids.values.forEach { it.columnCount = 2 }
        chapterGrid.visibility = View.VISIBLE
        (leftPane.layoutParams as LinearLayout.LayoutParams).apply { width = dp(120); weight = 0f }
        leftPane.requestLayout()
        rightPane.visibility = View.VISIBLE
        val verses = versesOf(code).filter { it.chapter == number }
        rightPane.removeAllViews()
        rightPane.addView(GridLayout(activity).apply {
            columnCount = 4
            verses.forEach { verse ->
                addView(numberButton(verse.reference.substringAfter(':').substringBefore('!').toIntOrNull() ?: 0) {
                    dialog.dismiss()
                    onPick(verse)
                })
            }
        })
    }

    private fun numberButton(number: Int, click: () -> Unit) = Button(activity).apply {
        text = number.toString()
        minWidth = 0
        minimumWidth = 0
        setPadding(0, 0, 0, 0)
        layoutParams = GridLayout.LayoutParams().apply { width = dp(48); height = dp(48) }
        setOnClickListener { click() }
    }

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
}
