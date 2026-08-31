package com.mehdi.analogclock

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Build
import android.widget.RemoteViews
import java.util.Calendar

class ClockWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        update(context, manager, ids)
    }

    companion object {
        fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val c = Calendar.getInstance()
            val hour = c.get(Calendar.HOUR)
            val minute = c.get(Calendar.MINUTE)
            val second = c.get(Calendar.SECOND)
            val hAngle = (hour + minute / 60f) * 30f
            val mAngle = (minute + second / 60f) * 6f
            val sAngle = second * 6f
            ids.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.widget_clock)
                if (Build.VERSION.SDK_INT >= 31) {
                    views.setViewRotation(R.id.hour, hAngle)
                    views.setViewRotation(R.id.minute, mAngle)
                    views.setViewRotation(R.id.second, sAngle)
                }
                manager.updateAppWidget(id, views)
            }
        }
    }
}
