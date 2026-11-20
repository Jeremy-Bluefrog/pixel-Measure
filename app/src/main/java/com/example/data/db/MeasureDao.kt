package com.example.data.db

import androidx.room.*
import com.example.data.model.MeasureRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface MeasureDao {
    @Query("SELECT * FROM measure_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<MeasureRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: MeasureRecord)

    @Delete
    suspend fun deleteRecord(record: MeasureRecord)

    @Query("DELETE FROM measure_records WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM measure_records")
    suspend fun clearAll()
}
