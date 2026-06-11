package com.example.data.db

import androidx.room.*
import com.example.data.model.Shift
import kotlinx.coroutines.flow.Flow

@Dao
interface ShiftDao {
    @Query("SELECT * FROM shifts ORDER BY date ASC, startTime ASC")
    fun getAllShifts(): Flow<List<Shift>>

    @Query("SELECT * FROM shifts WHERE date = :date ORDER BY startTime ASC")
    fun getShiftsByDate(date: String): Flow<List<Shift>>

    @Query("SELECT * FROM shifts WHERE id = :id")
    suspend fun getShiftById(id: Int): Shift?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShift(shift: Shift): Long

    @Update
    suspend fun updateShift(shift: Shift)

    @Delete
    suspend fun deleteShift(shift: Shift)

    @Query("DELETE FROM shifts WHERE jobId = :jobId")
    suspend fun deleteShiftsByJobId(jobId: Int)
}
