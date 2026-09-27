package com.mehdi.analogclock

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("clock_widget_prefs", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
            setBackgroundColor(0xFF10141C.toInt())
        }

        root.addView(TextView(this).apply {
            text = "تنظیمات ویجت ساعت"
            textSize = 22f
            setTextColor(0xFFF3F5F7.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 18)
        })

        root.addView(TextView(this).apply {
            text = "ساعت دیجیتال بالا و چهار مقدار پایین؛ اندازه هر بخش جداگانه قابل تنظیم است."
            textSize = 15f
            setTextColor(0xFF9AA6B2.toInt())
            setPadding(0, 0, 0, 12)
        })

        fun control(label: String, key: String, min: Int, max: Int, default: Float): SeekBar {
            root.addView(TextView(this).apply {
                text = label
                textSize = 17f
                setTextColor(0xFFF3F5F7.toInt())
                setPadding(0, 8, 0, 2)
            })
            val value = TextView(this).apply {
                textSize = 14f
                setTextColor(0xFFFFC857.toInt())
                gravity = Gravity.CENTER
            }
            root.addView(value)
            val bar = SeekBar(this).apply {
                this.max = max - min
                progress = (prefs.getFloat(key, default) - min).toInt().coerceIn(0, this.max)
            }
            fun refresh() { value.text = (bar.progress + min).toString() + " sp" }
            bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { refresh() }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
            refresh()
            root.addView(bar)
            return bar
        }

        val clock = control("اندازه ساعت دیجیتال", "digital_clock_size", 42, 300, 76f)
        val title = control("اندازه عنوان‌ها", "info_title_size", 11, 124, 18f)
        val v1 = control("اندازه مقدار طلوع", "info_value1_size", 16, 198, 30f)
        val v2 = control("اندازه مقدار غروب", "info_value2_size", 16, 198, 30f)
        val v3 = control("اندازه مقدار طول روز", "info_value3_size", 16, 198, 30f)
        val v4 = control("اندازه مقدار فردا", "info_value4_size", 16, 198, 27f)

        root.addView(Button(this).apply {
            text = "ذخیره و به‌روزرسانی ویجت"
            setOnClickListener {
                prefs.edit()
                    .putFloat("digital_clock_size", (clock.progress + 42).toFloat())
                    .putFloat("info_title_size", (title.progress + 11).toFloat())
                    .putFloat("info_value1_size", (v1.progress + 16).toFloat())
                    .putFloat("info_value2_size", (v2.progress + 16).toFloat())
                    .putFloat("info_value3_size", (v3.progress + 16).toFloat())
                    .putFloat("info_value4_size", (v4.progress + 16).toFloat())
                    .apply()

                val manager = AppWidgetManager.getInstance(this@SettingsActivity)
                val ids = manager.getAppWidgetIds(
                    android.content.ComponentName(this@SettingsActivity, ClockWidgetProvider::class.java)
                )
                ClockWidgetProvider.update(this@SettingsActivity, manager, ids)
                Toast.makeText(this@SettingsActivity, "تنظیمات ذخیره شد", Toast.LENGTH_SHORT).show()
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 16 })

        setContentView(root)
    }
}
