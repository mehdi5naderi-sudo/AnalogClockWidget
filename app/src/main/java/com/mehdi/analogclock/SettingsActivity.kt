package com.mehdi.analogclock

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {

    private val prefs by lazy {
        getSharedPreferences("clock_widget_prefs", MODE_PRIVATE)
    }

    private val switches = mutableMapOf<String, Switch>()
    private val seekBars = mutableMapOf<String, Pair<SeekBar, Int>>() // key -> (bar, min)
    private var latEdit: EditText? = null
    private var lonEdit: EditText? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(0xFF0B0F17.toInt())
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 40, 36, 48)
        }
        scroll.addView(root)

        root.addView(header("تنظیمات ویجت ساعت و هوا"))

        // ── نمایش ──
        root.addView(sectionTitle("نمایش بخش‌ها"))
        addSwitch(root, "show_jalali", "تاریخ شمسی", true)
        addSwitch(root, "show_gregorian", "تاریخ میلادی", true)
        addSwitch(root, "show_solar", "طلوع و غروب خورشید", true)
        addSwitch(root, "show_current_weather", "آب‌وهوای فعلی", true)
        addSwitch(root, "show_feels_like", "احساس دما", true)
        addSwitch(root, "show_hourly", "پیش‌بینی ساعتی", true)
        addSwitch(root, "show_daily", "پیش‌بینی روزانه", true)

        // ── تعداد ──
        root.addView(sectionTitle("تعداد آیتم‌های پیش‌بینی"))
        addSeek(root, "hourly_count", "ساعات آینده", 4, 12, 7)
        addSeek(root, "daily_count", "روزهای آینده", 3, 7, 5)

        // ── ظاهر ──
        root.addView(sectionTitle("ظاهر"))
        addSeek(root, "digital_clock_size", "اندازه ساعت (۰=پیش‌فرض)", 0, 120, 0)
        addSeek(root, "date_size", "اندازه تاریخ (۰=پیش‌فرض)", 0, 48, 0)
        addSeek(root, "label_size", "اندازه طلوع/غروب (۰=پیش‌فرض)", 0, 40, 0)
        addSeek(root, "background_alpha", "شفافیت پس‌زمینه٪", 0, 100, 100)
        addSwitch(root, "use_24h", "ساعت ۲۴ ساعته", true)

        // ── آب‌وهوا ──
        root.addView(sectionTitle("آب‌وهوا"))
        addSwitch(root, "weather_enabled", "فعال بودن آب‌وهوا", true)
        addSeek(root, "weather_refresh_min", "فاصله به‌روزرسانی (دقیقه)", 15, 120, 30)

        // ── مکان ──
        root.addView(sectionTitle("مکان (پیش‌فرض تهران)"))
        root.addView(hint("عرض جغرافیایی (latitude)"))
        latEdit = EditText(this).apply {
            setText(prefs.getString("location_lat", SolarCalculator.LATITUDE.toString()))
            setTextColor(0xFFF3F5F7.toInt())
            setHintTextColor(0xFF8B9AAB.toInt())
            setBackgroundColor(0xFF1E2738.toInt())
            setPadding(24, 20, 24, 20)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or
                android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        root.addView(latEdit)

        root.addView(hint("طول جغرافیایی (longitude)"))
        lonEdit = EditText(this).apply {
            setText(prefs.getString("location_lon", SolarCalculator.LONGITUDE.toString()))
            setTextColor(0xFFF3F5F7.toInt())
            setHintTextColor(0xFF8B9AAB.toInt())
            setBackgroundColor(0xFF1E2738.toInt())
            setPadding(24, 20, 24, 20)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or
                android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        root.addView(lonEdit)

        root.addView(Button(this).apply {
            text = "بازنشانی مکان به تهران"
            setTextColor(0xFFF3F5F7.toInt())
            setBackgroundColor(0xFF2A3548.toInt())
            setOnClickListener {
                latEdit?.setText(SolarCalculator.LATITUDE.toString())
                lonEdit?.setText(SolarCalculator.LONGITUDE.toString())
            }
        }.also {
            (it.layoutParams as? LinearLayout.LayoutParams)?.topMargin = 12
        })

        // ── رفتار ──
        root.addView(sectionTitle("رفتار ویجت"))
        addSeek(root, "tick_interval_min", "فاصله تازه‌سازی ساعت (دقیقه)", 1, 15, 1)

        // Save
        root.addView(Button(this).apply {
            text = "ذخیره و به‌روزرسانی ویجت"
            setTextColor(0xFF0B0F17.toInt())
            setBackgroundColor(0xFFFFC857.toInt())
            typeface = Typeface.DEFAULT_BOLD
            setOnClickListener { saveAndRefresh() }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 36 })

        root.addView(Button(this).apply {
            text = "بازنشانی همه تنظیمات"
            setTextColor(0xFFFF8A3D.toInt())
            setBackgroundColor(0xFF1E2738.toInt())
            setOnClickListener {
                prefs.edit().clear().apply()
                Toast.makeText(this@SettingsActivity, "تنظیمات پاک شد — برنامه را دوباره باز کنید", Toast.LENGTH_LONG).show()
                finish()
            }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 16 })

        root.addView(TextView(this).apply {
            text = "نسخه ویجت دیجیتال · Open-Meteo · بدون نیاز به کلید API"
            textSize = 12f
            setTextColor(0xFF8B9AAB.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 28, 0, 8)
        })

        setContentView(scroll)
    }

    private fun header(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 22f
        setTextColor(0xFFF3F5F7.toInt())
        gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, 0, 0, 16)
    }

    private fun sectionTitle(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 16f
        setTextColor(0xFFFFC857.toInt())
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, 28, 0, 10)
    }

    private fun hint(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(0xFF8B9AAB.toInt())
        setPadding(0, 10, 0, 6)
    }

    private fun addSwitch(root: LinearLayout, key: String, label: String, default: Boolean) {
        val sw = Switch(this).apply {
            text = label
            textSize = 15f
            setTextColor(0xFFF3F5F7.toInt())
            isChecked = prefs.getBoolean(key, default)
            setPadding(0, 10, 0, 10)
        }
        switches[key] = sw
        root.addView(sw)
    }

    private fun addSeek(
        root: LinearLayout,
        key: String,
        label: String,
        min: Int,
        max: Int,
        default: Int
    ) {
        root.addView(TextView(this).apply {
            text = label
            textSize = 14f
            setTextColor(0xFFF3F5F7.toInt())
            setPadding(0, 12, 0, 4)
        })
        val valueLabel = TextView(this).apply {
            textSize = 13f
            setTextColor(0xFFFFC857.toInt())
            gravity = Gravity.CENTER
        }
        root.addView(valueLabel)
        val current = when {
            key == "background_alpha" || key.endsWith("_size") ->
                prefs.getFloat(key, default.toFloat()).toInt()
            else -> prefs.getInt(key, default)
        }.coerceIn(min, max)

        val bar = SeekBar(this).apply {
            this.max = max - min
            progress = current - min
        }
        fun refresh() {
            val v = bar.progress + min
            valueLabel.text = when {
                key.endsWith("_size") && v == 0 -> "پیش‌فرض"
                key == "background_alpha" -> "$v٪"
                else -> v.toString()
            }
        }
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) = refresh()
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        refresh()
        root.addView(bar)
        seekBars[key] = bar to min
    }

    private fun saveAndRefresh() {
        val editor = prefs.edit()
        switches.forEach { (key, sw) ->
            editor.putBoolean(key, sw.isChecked)
        }
        seekBars.forEach { (key, pair) ->
            val (bar, min) = pair
            val v = bar.progress + min
            when {
                key == "background_alpha" || key.endsWith("_size") ->
                    editor.putFloat(key, v.toFloat())
                else -> editor.putInt(key, v)
            }
        }
        val lat = latEdit?.text?.toString()?.trim().orEmpty()
        val lon = lonEdit?.text?.toString()?.trim().orEmpty()
        if (lat.isNotEmpty()) editor.putString("location_lat", lat) else editor.remove("location_lat")
        if (lon.isNotEmpty()) editor.putString("location_lon", lon) else editor.remove("location_lon")
        // Force weather refresh on next update
        editor.putLong("weather_updated_at", 0L)
        editor.apply()

        val manager = AppWidgetManager.getInstance(this)
        val ids = manager.getAppWidgetIds(
            ComponentName(this, ClockWidgetProvider::class.java)
        )
        ClockWidgetProvider.update(this, manager, ids)
        Toast.makeText(this, "ذخیره شد", Toast.LENGTH_SHORT).show()
    }
}
