package com.example.data.repository

import com.example.data.api.GeminiTransitEstimator
import com.example.data.db.JobDao
import com.example.data.db.ShiftDao
import com.example.data.model.Job
import com.example.data.model.Shift
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class JobTimelineRepository(
    private val jobDao: JobDao,
    private val shiftDao: ShiftDao
) {
    val allJobs: Flow<List<Job>> = jobDao.getAllJobs()
    val allShifts: Flow<List<Shift>> = shiftDao.getAllShifts()

    suspend fun getJobById(id: Int): Job? = jobDao.getJobById(id)
    
    suspend fun insertJob(job: Job): Long = jobDao.insertJob(job)
    
    suspend fun updateJob(job: Job) = jobDao.updateJob(job)
    
    suspend fun deleteJob(job: Job) {
        shiftDao.deleteShiftsByJobId(job.id)
        jobDao.deleteJob(job)
    }

    suspend fun insertShift(shift: Shift): Long {
        val id = shiftDao.insertShift(shift)
        recomputeTransitTimesForDate(shift.date)
        return id
    }

    suspend fun updateShift(shift: Shift) {
        shiftDao.updateShift(shift)
        recomputeTransitTimesForDate(shift.date)
    }

    suspend fun deleteShift(shift: Shift) {
        shiftDao.deleteShift(shift)
        recomputeTransitTimesForDate(shift.date)
    }

    suspend fun recomputeTransitTimesForDate(date: String) {
        val shifts = shiftDao.getShiftsByDate(date).first()
        if (shifts.isEmpty()) return

        for (i in 0 until shifts.size - 1) {
            val current = shifts[i]
            val next = shifts[i + 1]

            val currentJob = jobDao.getJobById(current.jobId)
            val nextJob = jobDao.getJobById(next.jobId)

            if (currentJob != null && nextJob != null) {
                if (currentJob.address.trim().lowercase() != nextJob.address.trim().lowercase()) {
                    val mode = current.transitMode ?: "Driving"
                    val result = GeminiTransitEstimator.estimateTransit(
                        fromAddress = currentJob.address,
                        toAddress = nextJob.address,
                        mode = mode
                    )
                    val updatedCurrent = current.copy(
                        transitTimeToNext = result.durationMinutes,
                        transitDistanceToNext = result.distanceMiles,
                        transitMode = mode,
                        transitSummary = result.routeSummary,
                        isAIEstimated = result.isAIEstimated
                    )
                    shiftDao.updateShift(updatedCurrent)
                } else {
                    val updatedCurrent = current.copy(
                        transitTimeToNext = 0,
                        transitDistanceToNext = 0.0,
                        transitSummary = "Same Location",
                        isAIEstimated = false
                    )
                    shiftDao.updateShift(updatedCurrent)
                }
            }
        }

        // Clean up last shift's transit details since there's no consecutive shift after it on this day
        val last = shifts.last()
        val updatedLast = last.copy(
            transitTimeToNext = null,
            transitDistanceToNext = null,
            transitSummary = null,
            isAIEstimated = false
        )
        shiftDao.updateShift(updatedLast)
    }
}
