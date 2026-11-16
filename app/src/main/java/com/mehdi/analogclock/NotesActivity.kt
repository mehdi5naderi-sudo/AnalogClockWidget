package com.mehdi.analogclock

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class NotesActivity : Activity() {
    private val prefs by lazy {
        getSharedPreferences("clock_widget_prefs", MODE_PRIVATE)
    }
    private lateinit var editor: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 32, 28, 28)
            setBackgroundColor(0xFF0B0F17.toInt())
        }
        root.addView(TextView(this).apply {
            text = "یادداشت‌ها"
            textSize = 22f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFFFC857.toInt())
            setPadding(0, 0, 0, 20)
        })
        root.addView(TextView(this).apply {
            text = "یادداشت‌هایت را بنویس؛ می‌توانی چندین یادداشت را در این صفحه نگه داری."
            textSize = 14f
            setTextColor(0xFFB8C2D1.toInt())
            setPadding(0, 0, 0, 12)
        })
        editor = EditText(this).apply {
            setText(prefs.getString("saved_notes", "").orEmpty())
            hint = "یادداشت‌ها…"
            setHintTextColor(0xFF8B9AAB.toInt())
            setTextColor(0xFFF3F5F7.toInt())
            textSize = 16f
            gravity = Gravity.TOP or Gravity.START
            minLines = 10
            setPadding(18, 18, 18, 18)
            setBackgroundColor(0xFF1E2738.toInt())
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        root.addView(editor, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        root.addView(Button(this).apply {
            text = "ذخیره یادداشت‌ها"
            setTextColor(0xFF0B0F17.toInt())
            setBackgroundColor(0xFFFFC857.toInt())
            typeface = Typeface.DEFAULT_BOLD
            setOnClickListener {
                prefs.edit().putString("saved_notes", editor.text.toString()).apply()
                Toast.makeText(this@NotesActivity, "یادداشت‌ها ذخیره شد", Toast.LENGTH_SHORT).show()
            }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 16 })
        setContentView(root)
    }

    override fun onPause() {
        if (::editor.isInitialized) {
            prefs.edit().putString("saved_notes", editor.text.toString()).apply()
        }
        super.onPause()
    }
}
