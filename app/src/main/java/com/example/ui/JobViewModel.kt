package com.example.ui

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.Job
import com.example.data.model.Shift
import com.example.data.repository.JobTimelineRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class JobViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = JobTimelineRepository(database.jobDao(), database.shiftDao())

    val jobs: StateFlow<List<Job>> = repository.allJobs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val shifts: StateFlow<List<Shift>> = repository.allShifts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedDate = MutableStateFlow(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE))
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val selectedDateShifts: StateFlow<List<Shift>> = combine(shifts, selectedDate) { allShifts, date ->
        allShifts.filter { it.date == date }.sortedBy { it.startTime }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun updateWidget() {
        val context = getApplication<Application>()
        try {
            val intent = Intent(context, ShiftWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, ShiftWidgetProvider::class.java))
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun selectDate(date: String) {
        _selectedDate.value = date
    }

    fun addJob(title: String, company: String, address: String, hourlyRate: Double, colorHex: String, notes: String = "") {
        viewModelScope.launch {
            _isLoading.value = true
            repository.insertJob(
                Job(
                    title = title.trim(),
                    company = company.trim(),
                    address = address.trim(),
                    hourlyRate = hourlyRate,
                    colorHex = colorHex,
                    notes = notes
                )
            )
            _isLoading.value = false
            updateWidget()
        }
    }

    fun deleteJob(job: Job) {
        viewModelScope.launch {
            _isLoading.value = true
            repository.deleteJob(job)
            _isLoading.value = false
            updateWidget()
        }
    }

    fun addShift(jobId: Int, date: String, startTime: String, endTime: String, transitMode: String, notes: String = "") {
        viewModelScope.launch {
            _isLoading.value = true
            val shift = Shift(
                jobId = jobId,
                date = date,
                startTime = startTime,
                endTime = endTime,
                notes = notes,
                transitMode = transitMode
            )
            repository.insertShift(shift)
            _isLoading.value = false
            updateWidget()
        }
    }

    fun deleteShift(shift: Shift) {
        viewModelScope.launch {
            _isLoading.value = true
            repository.deleteShift(shift)
            _isLoading.value = false
            updateWidget()
        }
    }

    fun recalculateTransit(date: String) {
        viewModelScope.launch {
            _isLoading.value = true
            repository.recomputeTransitTimesForDate(date)
            _isLoading.value = false
            updateWidget()
        }
    }
}
