package com.mehdi.analogclock

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
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
    private val editors = linkedMapOf<String, EditText>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(0xFF0B0F17.toInt())
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
            setLayoutDirection(android.view.View.LAYOUT_DIRECTION_RTL)
        }
        scroll.addView(root)

        root.addView(TextView(this).apply {
            text = "یادداشت‌ها"
            textSize = 22f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFFFC857.toInt())
            setPadding(0, 0, 0, 20)
        })

        addNoteSection(root, "card_notes", "کارت", "یادداشت‌های مربوط به کارت…")
        addNoteSection(root, "installment_notes", "قسط", "مبلغ، تاریخ سررسید و جزئیات قسط…")
        addNoteSection(root, "other_notes", "سایر", "سایر یادداشت‌ها…")

        root.addView(Button(this).apply {
            text = "ذخیره هر سه یادداشت"
            setTextColor(0xFF0B0F17.toInt())
            setBackgroundColor(0xFFFFC857.toInt())
            typeface = Typeface.DEFAULT_BOLD
            setOnClickListener {
                saveNotes()
                Toast.makeText(this@NotesActivity, "یادداشت‌ها ذخیره شد", Toast.LENGTH_SHORT).show()
            }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 18 })

        setContentView(scroll)
    }

    private fun addNoteSection(root: LinearLayout, key: String, title: String, hintText: String) {
        root.addView(TextView(this).apply {
            text = title
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFFFC857.toInt())
            setPadding(0, 16, 0, 8)
        })
        val edit = EditText(this).apply {
            setText((prefs.getString(key, null) ?: if (key == "other_notes") prefs.getString("saved_notes", "") else "").orEmpty())
            hint = hintText
            setHintTextColor(0xFF8B9AAB.toInt())
            setTextColor(0xFFF3F5F7.toInt())
            textSize = 16f
            gravity = Gravity.TOP or Gravity.START
            minLines = 4
            setPadding(16, 16, 16, 16)
            setBackgroundColor(0xFF1E2738.toInt())
            textDirection = android.view.View.TEXT_DIRECTION_RTL
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        editors[key] = edit
        root.addView(edit, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
    }

    private fun saveNotes() {
        val editor = prefs.edit()
        editors.forEach { (key, field) ->
            editor.putString(key, field.text.toString())
        }
        editor.apply()
    }

    override fun onPause() {
        if (editors.isNotEmpty()) saveNotes()
        super.onPause()
    }
}
