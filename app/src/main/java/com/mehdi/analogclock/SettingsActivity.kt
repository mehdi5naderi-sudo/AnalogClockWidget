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

        root.addView(TextView(this).apply {
            text = "موقعیت اطلاعات بیرون از دایره ساعت"
            textSize = 16f
            setTextColor(0xFF9AA6B2.toInt())
            setPadding(0, 0, 0, 12)
        })

        val spinners = listOf(
            "طلوع" to prefs.getInt("pos_sunrise", ClockWidgetProvider.CORNER_TOP_LEFT),
            "غروب" to prefs.getInt("pos_sunset", ClockWidgetProvider.CORNER_TOP_RIGHT),
            "طول روز" to prefs.getInt("pos_day_length", ClockWidgetProvider.CORNER_BOTTOM_LEFT),
            "بیشتر / کمتر" to prefs.getInt("pos_delta", ClockWidgetProvider.CORNER_BOTTOM_RIGHT)
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
            text = "اندازه فونت اطلاعات بیرون از دایره"
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
            max = 26
            progress = (prefs.getFloat("info_size", 26f) - 16f).toInt().coerceIn(0, 26)
        }

        fun showSize() {
            sizeValue.text = (seek.progress + 16).toString() + " sp"
        }

        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { showSize() }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        showSize()
        root.addView(seek, LinearLayout.LayoutParams(-1, -2))

        root.addView(TextView(this).apply {
            text = "عددهای ساعت فقط ۱۲، ۳، ۶ و ۹ هستند و داخل دایره قرار دارند."
            textSize = 14f
            setTextColor(0xFF9AA6B2.toInt())
            setPadding(0, 12, 0, 18)
        })

        root.addView(Button(this).apply {
            text = "ذخیره و به‌روزرسانی ویجت"
            setOnClickListener {
                prefs.edit()
                    .putInt("pos_sunrise", spinners[0].selectedItemPosition)
                    .putInt("pos_sunset", spinners[1].selectedItemPosition)
                    .putInt("pos_day_length", spinners[2].selectedItemPosition)
                    .putInt("pos_delta", spinners[3].selectedItemPosition)
                    .putFloat("info_size", (seek.progress + 16).toFloat())
                    .apply()

                val manager = AppWidgetManager.getInstance(this@SettingsActivity)
                val ids = manager.getAppWidgetIds(
                    android.content.ComponentName(
                        this@SettingsActivity,
                        ClockWidgetProvider::class.java
                    )
                )
                ClockWidgetProvider.update(this@SettingsActivity, manager, ids)
                Toast.makeText(this@SettingsActivity, "تنظیمات ذخیره شد", Toast.LENGTH_SHORT).show()
            }
        }, LinearLayout.LayoutParams(-1, -2))

        setContentView(root)
    }
}