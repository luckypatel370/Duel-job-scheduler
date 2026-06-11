package com.example.ui

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.example.R
import com.example.data.db.AppDatabase
import com.example.data.model.Job
import com.example.data.model.Shift
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class ShiftWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val shifts = db.shiftDao().getAllShifts().first()
                val jobs = db.jobDao().getAllJobs().first()

                val nowStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                val upcomingShift = shifts.mapNotNull { s ->
                    try {
                        val combinedStr = "${s.date} ${s.startTime}"
                        combinedStr to s
                    } catch (e: Exception) {
                        null
                    }
                }.filter { it.first >= nowStr }
                .minByOrNull { it.first }?.second

                val matchedJob = upcomingShift?.let { s -> jobs.firstOrNull { it.id == s.jobId } }

                for (appWidgetId in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.shift_widget)

                    if (upcomingShift != null && matchedJob != null) {
                        views.setTextViewText(R.id.widget_job_title, matchedJob.title)
                        views.setTextViewText(R.id.widget_company, matchedJob.company)
                        views.setTextViewText(R.id.widget_time, "${upcomingShift.startTime} - ${upcomingShift.endTime}")
                        
                        val dateFormatted = try {
                            val parsedDate = LocalDate.parse(upcomingShift.date)
                            parsedDate.format(DateTimeFormatter.ofPattern("EEEE, MMM dd", Locale.US))
                        } catch (e: Exception) {
                            upcomingShift.date
                        }
                        views.setTextViewText(R.id.widget_date, dateFormatted)
                        
                        try {
                            val parsedColor = android.graphics.Color.parseColor(matchedJob.colorHex)
                            views.setInt(R.id.widget_badge, "setTextColor", parsedColor)
                        } catch (e: Exception) {}
                    } else {
                        views.setTextViewText(R.id.widget_job_title, "No Shifts Scheduled")
                        views.setTextViewText(R.id.widget_company, "Enjoy your free time!")
                        views.setTextViewText(R.id.widget_time, "--")
                        views.setTextViewText(R.id.widget_date, "--")
                    }

                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
