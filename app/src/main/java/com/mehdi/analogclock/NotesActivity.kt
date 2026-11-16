package com.mehdi.analogclock

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class NotesActivity : Activity() {
    private val prefs by lazy {
        getSharedPreferences("clock_widget_prefs", MODE_PRIVATE)
    }

    private val tabs = listOf(
        NoteTab("card_notes", "کارت", "یادداشت‌های مربوط به کارت…"),
        NoteTab("installment_notes", "قسط", "مبلغ، تاریخ سررسید و جزئیات قسط…"),
        NoteTab("other_notes", "سایر", "سایر یادداشت‌ها…")
    )

    private lateinit var editor: EditText
    private lateinit var editorScroll: ScrollView
    private lateinit var tabRow: LinearLayout
    private val tabButtons = mutableListOf<Button>()
    private var selectedTab = 0
    private var hasLoadedTab = false

    private data class NoteTab(
        val key: String,
        val title: String,
        val hint: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
            setBackgroundColor(0xFF0B0F17.toInt())
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        root.addView(TextView(this).apply {
            text = "یادداشت‌ها"
            textSize = 22f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFFFC857.toInt())
            setPadding(0, 0, 0, 22)
        })

        tabRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        tabs.forEachIndexed { index, tab ->
            val button = Button(this).apply {
                text = tab.title
                isAllCaps = false
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(4, 8, 4, 8)
                setOnClickListener { selectTab(index) }
            }
            tabButtons.add(button)
            tabRow.addView(button, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                marginStart = dp(4)
                marginEnd = dp(4)
            })
        }
        root.addView(tabRow)

        editor = EditText(this).apply {
            setHintTextColor(0xFF8B9AAB.toInt())
            setTextColor(0xFFF3F5F7.toInt())
            textSize = 16f
            gravity = Gravity.TOP or Gravity.START
            setPadding(18, 18, 18, 18)
            setBackgroundColor(0xFF1E2738.toInt())
            textDirection = View.TEXT_DIRECTION_RTL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            minHeight = dp(280)
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }

        // Let the outer ScrollView handle long-text scrolling with a smooth fling.
        editorScroll = ScrollView(this).apply {
            isFillViewport = true
            isSmoothScrollingEnabled = true
            isVerticalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            setFadingEdgeLength(dp(20))
            addView(editor, android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            ))
        }
        root.addView(editorScroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ).apply {
            topMargin = dp(16)
            bottomMargin = dp(16)
        })

        root.addView(Button(this).apply {
            text = "ذخیره یادداشت"
            isAllCaps = false
            setTextColor(0xFF0B0F17.toInt())
            setBackgroundColor(0xFFFFC857.toInt())
            typeface = Typeface.DEFAULT_BOLD
            setOnClickListener {
                saveCurrentNote()
                Toast.makeText(this@NotesActivity, "یادداشت ذخیره شد", Toast.LENGTH_SHORT).show()
            }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        setContentView(root)
        selectTab(0)
    }

    private fun selectTab(index: Int) {
        if (hasLoadedTab) saveCurrentNote()
        selectedTab = index.coerceIn(tabs.indices)
        val tab = tabs[selectedTab]
        val saved = prefs.getString(tab.key, null)
            ?: if (tab.key == "other_notes") prefs.getString("saved_notes", "") else ""
        editor.setText(saved.orEmpty())
        editor.hint = tab.hint
        editorScroll.scrollTo(0, 0)
        hasLoadedTab = true

        tabButtons.forEachIndexed { i, button ->
            val active = i == selectedTab
            button.setTextColor(if (active) 0xFF0B0F17.toInt() else 0xFFF3F5F7.toInt())
            button.setBackgroundColor(if (active) 0xFFFFC857.toInt() else 0xFF2A3548.toInt())
        }
    }

    private fun saveCurrentNote() {
        if (!hasLoadedTab || !::editor.isInitialized || tabs.isEmpty()) return
        // commit() ensures the active tab is written before another tab is loaded.
        prefs.edit()
            .putString(tabs[selectedTab].key, editor.text.toString())
            .commit()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onPause() {
        saveCurrentNote()
        super.onPause()
    }
}
