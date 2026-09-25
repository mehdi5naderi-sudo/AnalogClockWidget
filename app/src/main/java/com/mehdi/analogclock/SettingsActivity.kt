package com.mehdi.analogclock

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("clock_widget_prefs", MODE_PRIVATE) }
    private val labels = arrayOf("بالا چپ", "بالا راست", "پایین راست", "پایین چپ")
    private lateinit var sizeValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
            setBackgroundColor(0xFF10141C.toInt())
        }
        root.addView(TextView(this).apply {
            text = "تنظیمات ساعت آنالوگ"
            textSize = 22f
            setTextColor(0xFFF3F5F7.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 22)
        }, LinearLayout.LayoutParams(-1, -2))

        val spinners = listOf(
            "عدد ۱۲" to prefs.getInt("pos_12", 0),
            "عدد ۳" to prefs.getInt("pos_3", 1),
            "عدد ۶" to prefs.getInt("pos_6", 2),
            "عدد ۹" to prefs.getInt("pos_9", 3)
        ).map { (label, selected) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(TextView(this).apply {
                text = label
                textSize = 17f
                setTextColor(0xFFF3F5F7.toInt())
            }, LinearLayout.LayoutParams(0, -2, 1f))
            val spinner = Spinner(this)
            spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
            spinner.setSelection(selected.coerceIn(0, 3))
            row.addView(spinner, LinearLayout.LayoutParams(180, -2))
            root.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 10 })
            spinner
        }

        root.addView(TextView(this).apply {
            text = "اندازه فونت عددها"
            textSize = 17f
            setTextColor(0xFFF3F5F7.toInt())
            setPadding(0, 18, 0, 6)
        })
        sizeValue = TextView(this).apply {
            setTextColor(0xFFFFC857.toInt())
            textSize = 16f
            gravity = Gravity.CENTER
        }
        root.addView(sizeValue)

        val seek = SeekBar(this).apply {
            max = 40
            progress = (prefs.getFloat("number_size", 30f) - 16f).toInt().coerceIn(0, 40)
        }
        fun showSize() { sizeValue.text = (seek.progress + 16).toString() + " sp" }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { showSize() }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        showSize()
        root.addView(seek, LinearLayout.LayoutParams(-1, -2))

        root.addView(TextView(this).apply {
            text = "جای هر عدد مستقل است؛ می‌توانید هر عدد را به هر گوشه منتقل کنید."
            textSize = 14f
            setTextColor(0xFF9AA6B2.toInt())
            setPadding(0, 12, 0, 18)
        })
        root.addView(Button(this).apply {
            text = "ذخیره و به‌روزرسانی ویجت"
            setOnClickListener {
                prefs.edit()
                    .putInt("pos_12", spinners[0].selectedItemPosition)
                    .putInt("pos_3", spinners[1].selectedItemPosition)
                    .putInt("pos_6", spinners[2].selectedItemPosition)
                    .putInt("pos_9", spinners[3].selectedItemPosition)
                    .putFloat("number_size", (seek.progress + 16).toFloat())
                    .apply()
                val manager = AppWidgetManager.getInstance(this@SettingsActivity)
                val ids = manager.getAppWidgetIds(
                    android.content.ComponentName(this@SettingsActivity, ClockWidgetProvider::class.java)
                )
                ClockWidgetProvider.update(this, manager, ids)
                Toast.makeText(this@SettingsActivity, "تنظیمات ذخیره شد", Toast.LENGTH_SHORT).show()
            }
        }, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
    }
}