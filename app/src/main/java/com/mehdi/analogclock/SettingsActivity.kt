package com.mehdi.analogclock

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {

    private val prefs by lazy {
        getSharedPreferences("clock_widget_prefs", MODE_PRIVATE)
    }

    private val switches = mutableMapOf<String, Switch>()
    private val seekBars = mutableMapOf<String, Pair<SeekBar, Int>>()
    private var latEdit: EditText? = null
    private var lonEdit: EditText? = null
    private var stationSpinner: Spinner? = null
    private var stations: List<IrimoWeatherData.Station> = emptyList()

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

        root.addView(header("تنظیمات ویجت ساعت"))

        root.addView(sectionTitle("نمایش بخش‌ها"))
        addSwitch(root, "show_jalali", "تاریخ شمسی", true)
        addSwitch(root, "show_gregorian", "تاریخ میلادی", true)
        addSwitch(root, "show_solar", "طلوع و غروب خورشید", true)

        root.addView(sectionTitle("ظاهر"))
        addSeek(root, "digital_clock_size", "اندازه ساعت (۰=پیش‌فرض)", 0, 120, 0)
        addSeek(root, "condition_size", "اندازه اعداد هوا (۰=پیش‌فرض)", 0, 40, 0)
        addSeek(root, "meta_size", "اندازه ساعت به‌روزرسانی (۰=پیش‌فرض)", 0, 36, 0)
        addSeek(root, "date_size", "اندازه تاریخ (۰=پیش‌فرض)", 0, 48, 0)
        addSeek(root, "label_size", "اندازه طلوع/غروب (۰=پیش‌فرض)", 0, 40, 0)
        addSeek(root, "notes_size", "اندازه عنوان یادداشت‌ها (۰=پیش‌فرض)", 0, 40, 0)
        addSeek(root, "background_alpha", "شفافیت پس‌زمینه٪", 0, 100, 100)
        addSwitch(root, "use_24h", "ساعت ۲۴ ساعته", true)

        root.addView(sectionTitle("مکان (پیش‌فرض پونک)"))
        root.addView(hint("عرض جغرافیایی (latitude)"))
        latEdit = editField(prefs.getString("location_lat", SolarCalculator.LATITUDE.toString()).orEmpty())
        root.addView(latEdit)
        root.addView(hint("طول جغرافیایی (longitude)"))
        lonEdit = editField(prefs.getString("location_lon", SolarCalculator.LONGITUDE.toString()).orEmpty())
        root.addView(lonEdit)

        root.addView(Button(this).apply {
            text = "بازنشانی مکان به پونک"
            setTextColor(0xFFF3F5F7.toInt())
            setBackgroundColor(0xFF2A3548.toInt())
            setOnClickListener {
                latEdit?.setText(SolarCalculator.LATITUDE.toString())
                lonEdit?.setText(SolarCalculator.LONGITUDE.toString())
            }
        })

        root.addView(sectionTitle("ایستگاه هواشناسی"))
        root.addView(hint("اطلاعات ویجت مستقیماً از سازمان هواشناسی ایران دریافت می‌شود."))
        stationSpinner = Spinner(this).apply {
            setBackgroundColor(0xFF1E2738.toInt())
        }
        root.addView(stationSpinner)
        root.addView(Button(this).apply {
            text = "بارگذاری فهرست ایستگاه‌ها"
            setTextColor(0xFFF3F5F7.toInt())
            setBackgroundColor(0xFF2A3548.toInt())
            setOnClickListener { loadStations() }
        })
        addSeek(root, "irimo_refresh_min", "فاصله به‌روزرسانی هوا (دقیقه)", 5, 120, 15)

        root.addView(sectionTitle("رفتار ویجت"))
        addSeek(root, "tick_interval_min", "فاصله تازه‌سازی ساعت (دقیقه)", 1, 15, 1)

        root.addView(Button(this).apply {
            text = "دریافت دوباره داده‌های هوا"
            setTextColor(0xFFF3F5F7.toInt())
            setBackgroundColor(0xFF2A3548.toInt())
            setOnClickListener {
                IrimoWeatherData.refreshIfNeeded(this@SettingsActivity, force = true)
                Toast.makeText(this@SettingsActivity, "در حال دریافت داده‌های IRIMO…", Toast.LENGTH_SHORT).show()
            }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 12 })

        root.addView(Button(this).apply {
            text = "ذخیره و به‌روزرسانی ویجت"
            setTextColor(0xFF0B0F17.toInt())
            setBackgroundColor(0xFFFFC857.toInt())
            typeface = Typeface.DEFAULT_BOLD
            setOnClickListener { saveAndRefresh() }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 28 })

        root.addView(Button(this).apply {
            text = "بازنشانی همه تنظیمات"
            setTextColor(0xFFFF8A3D.toInt())
            setBackgroundColor(0xFF1E2738.toInt())
            setOnClickListener {
                prefs.edit().clear().commit()
                Toast.makeText(this@SettingsActivity, "تنظیمات پاک شد — برنامه را دوباره باز کنید", Toast.LENGTH_LONG).show()
                finish()
            }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 16 })

        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        } catch (_: Exception) { "?" }
        val versionCode = try {
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                packageManager.getPackageInfo(packageName, 0).longVersionCode.toString()
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0).versionCode.toString()
            }
        } catch (_: Exception) { "?" }

        root.addView(TextView(this).apply {
            text = "نسخه $versionName  ·  build $versionCode\nمنبع هواشناسی: IRIMO"
            textSize = 12f
            setTextColor(0xFF8B9AAB.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 28, 0, 8)
        })

        setContentView(scroll)
        loadStations()
    }

    private fun loadStations() {
        IrimoWeatherData.loadStations(this) { list ->
            stations = list
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, list)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            stationSpinner?.adapter = adapter
            val saved = prefs.getString("irimo_station_url", null)
            val index = list.indexOfFirst { it.url == saved }.let { if (it >= 0) it else 0 }
            if (list.isNotEmpty()) stationSpinner?.setSelection(index)
        }
    }

    private fun editField(value: String): EditText = EditText(this).apply {
        setText(value)
        setTextColor(0xFFF3F5F7.toInt())
        setHintTextColor(0xFF8B9AAB.toInt())
        setBackgroundColor(0xFF1E2738.toInt())
        setPadding(24, 20, 24, 20)
        inputType = android.text.InputType.TYPE_CLASS_NUMBER or
            android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or
            android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
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
            val value = bar.progress + min
            valueLabel.text = when {
                key.endsWith("_size") && value == 0 -> "پیش‌فرض"
                key == "background_alpha" -> "$value٪"
                else -> value.toString()
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
        switches.forEach { (key, sw) -> editor.putBoolean(key, sw.isChecked) }
        seekBars.forEach { (key, pair) ->
            val (bar, min) = pair
            val value = bar.progress + min
            when {
                key == "background_alpha" || key.endsWith("_size") -> editor.putFloat(key, value.toFloat())
                else -> editor.putInt(key, value)
            }
        }

        val lat = latEdit?.text?.toString()?.trim().orEmpty()
        val lon = lonEdit?.text?.toString()?.trim().orEmpty()
        if (lat.isNotEmpty()) editor.putString("location_lat", lat) else editor.remove("location_lat")
        if (lon.isNotEmpty()) editor.putString("location_lon", lon) else editor.remove("location_lon")

        val station = stations.getOrNull(stationSpinner?.selectedItemPosition ?: 0)
        if (station != null) {
            editor.putString("irimo_station_url", station.url)
            editor.putString("irimo_station_name", station.name)
        }

        // Remove all legacy Open-Meteo / IQAir weather values and settings.
        listOf(
            "weather_compact_v6", "weather_compact_v5", "weather_compact_v4",
            "weather_compact_v3", "weather_updated_at", "weather_attempt_at",
            "weather_enabled", "weather_refresh_min", "iqair_api_key",
            "show_current_weather", "show_feels_like"
        ).forEach { editor.remove(it) }

        editor.commit()
        IrimoWeatherData.refreshIfNeeded(this, force = true)

        val manager = AppWidgetManager.getInstance(this)
        val ids = manager.getAppWidgetIds(ComponentName(this, ClockWidgetProvider::class.java))
        ClockWidgetProvider.update(this, manager, ids)
        Toast.makeText(this, "ذخیره شد — داده‌های IRIMO در حال دریافت است", Toast.LENGTH_SHORT).show()
    }
}
