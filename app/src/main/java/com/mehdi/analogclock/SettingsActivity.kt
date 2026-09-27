package com.mehdi.analogclock

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {

    private val prefs by lazy {
        getSharedPreferences("clock_widget_prefs", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 36, 32, 36)
            setBackgroundColor(0xFF0B0F17.toInt())
        }

        root.addView(TextView(this).apply {
            text = "تنظیمات ویجت"
            textSize = 24f
            setTextColor(0xFFF3F5F7.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 8)
        })

        root.addView(TextView(this).apply {
            text = "۰ = خودکار (پر کردن کامل جعبه). یا دستی بزرگ‌تر تنظیم کنید."
            textSize = 14f
            setTextColor(0xFF8B9AAB.toInt())
            setPadding(0, 0, 0, 20)
        })

        data class Control(val label: String, val key: String, val min: Int, val max: Int, val default: Float)

        // 0 = auto-fit. Slider goes high so user can force very large text.
        val controls = listOf(
            Control("ساعت دیجیتال (۰=خودکار)", "digital_clock_size", 0, 200, 0f),
            Control("طلوع (۰=خودکار)", "info_value1_size", 0, 120, 0f),
            Control("غروب (۰=خودکار)", "info_value2_size", 0, 120, 0f),
            Control("طول روز (۰=خودکار)", "info_value3_size", 0, 120, 0f),
            Control("فردا (۰=خودکار)", "info_value4_size", 0, 120, 0f)
        )

        val bars = mutableListOf<SeekBar>()

        controls.forEach { c ->
            root.addView(TextView(this).apply {
                text = c.label
                textSize = 15f
                setTextColor(0xFFF3F5F7.toInt())
                setPadding(0, 14, 0, 4)
            })

            val valueLabel = TextView(this).apply {
                textSize = 13f
                setTextColor(0xFFFFC857.toInt())
                gravity = Gravity.CENTER
            }
            root.addView(valueLabel)

            val bar = SeekBar(this).apply {
                max = c.max - c.min
                progress = (prefs.getFloat(c.key, c.default) - c.min)
                    .toInt()
                    .coerceIn(0, max)
            }

            fun refresh() {
                val v = bar.progress + c.min
                valueLabel.text = if (v == 0) "خودکار (پر جعبه)" else "$v sp"
            }
            bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) = refresh()
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
            refresh()
            root.addView(bar)
            bars.add(bar)
        }

        root.addView(Button(this).apply {
            text = "ذخیره و به‌روزرسانی ویجت"
            setTextColor(0xFF0B0F17.toInt())
            setBackgroundColor(0xFFFFC857.toInt())
            setOnClickListener {
                val editor = prefs.edit()
                controls.forEachIndexed { i, c ->
                    editor.putFloat(c.key, (bars[i].progress + c.min).toFloat())
                }
                editor.apply()

                val manager = AppWidgetManager.getInstance(this@SettingsActivity)
                val ids = manager.getAppWidgetIds(
                    ComponentName(this@SettingsActivity, ClockWidgetProvider::class.java)
                )
                ClockWidgetProvider.update(this@SettingsActivity, manager, ids)
                Toast.makeText(this@SettingsActivity, "تنظیمات ذخیره شد", Toast.LENGTH_SHORT).show()
            }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 28
        })

        setContentView(root)
    }
}
