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

        data class Control(
            val label: String,
            val key: String,
            val min: Int,
            val max: Int,
            val default: Float,
            val isOffset: Boolean = false
        )

        val sizeControls = listOf(
            Control("ساعت دیجیتال (۰=۷۲)", "digital_clock_size", 0, 160, 0f),
            Control("تاریخ شمسی (۰=۲۸)", "date_size", 0, 80, 0f),
            Control("طلوع / غروب (۰=۲۶)", "label_size", 0, 70, 0f)
        )

        val offsetControls = listOf(
            Control("طلوع (راست)", "sunrise_offset_y", -80, 80, 0f, isOffset = true),
            Control("غروب (چپ)", "sunset_offset_y", -80, 80, 0f, isOffset = true),
            Control("تاریخ شمسی", "date_offset_y", -80, 80, 0f, isOffset = true)
        )

        val appearanceControls = listOf(
            Control("شفافیت پس‌زمینه", "background_alpha", 0, 100, 100f)
        )

        val allControls = sizeControls + appearanceControls + offsetControls
        val bars = mutableListOf<SeekBar>()

        fun addSeekBar(c: Control) {
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
                valueLabel.text = when {
                    c.isOffset && v == 0 -> "۰ (جای پیش‌فرض)"
                    c.isOffset && v > 0 -> "+$v (پایین‌تر)"
                    c.isOffset -> "$v (بالاتر)"
                    v == 0 -> "پیش‌فرض"
                    else -> "$v px"
                }
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

        // ── اندازه فونت‌ها ──
        root.addView(TextView(this).apply {
            text = "اندازه فونت"
            textSize = 16f
            setTextColor(0xFFFFC857.toInt())
            setPadding(0, 12, 0, 4)
        })
        root.addView(TextView(this).apply {
            text = "۰ = پیش‌فرض · عدد بزرگ‌تر = متن درشت‌تر"
            textSize = 13f
            setTextColor(0xFF8B9AAB.toInt())
            setPadding(0, 0, 0, 8)
        })
        sizeControls.forEach { addSeekBar(it) }

        // ── شفافیت پس‌زمینه ──
        root.addView(TextView(this).apply {
            text = "ظاهر ویجت"
            textSize = 16f
            setTextColor(0xFFFFC857.toInt())
            setPadding(0, 28, 0, 4)
        })
        root.addView(TextView(this).apply {
            text = "۰٪ = کاملاً شفاف · ۱۰۰٪ = کاملاً مات"
            textSize = 13f
            setTextColor(0xFF8B9AAB.toInt())
            setPadding(0, 0, 0, 8)
        })
        appearanceControls.forEach { addSeekBar(it) }

        // ── جابه‌جایی عمودی ──
        root.addView(TextView(this).apply {
            text = "جابه‌جایی عمودی"
            textSize = 16f
            setTextColor(0xFFFFC857.toInt())
            setPadding(0, 28, 0, 4)
        })
        root.addView(TextView(this).apply {
            text = "منفی = بالاتر · مثبت = پایین‌تر · ۰ = جای پیش‌فرض"
            textSize = 13f
            setTextColor(0xFF8B9AAB.toInt())
            setPadding(0, 0, 0, 8)
        })
        offsetControls.forEach { addSeekBar(it) }

        root.addView(Button(this).apply {
            text = "ذخیره و به‌روزرسانی ویجت"
            setTextColor(0xFF0B0F17.toInt())
            setBackgroundColor(0xFFFFC857.toInt())
            setOnClickListener {
                val editor = prefs.edit()
                allControls.forEachIndexed { i, c ->
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
