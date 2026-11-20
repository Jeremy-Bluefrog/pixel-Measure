package com.example.data.repository

import com.example.data.db.MeasureDao
import com.example.data.model.MeasureRecord
import kotlinx.coroutines.flow.Flow

class MeasureRepository(private val measureDao: MeasureDao) {
    val allRecords: Flow<List<MeasureRecord>> = measureDao.getAllRecords()

    suspend fun insert(record: MeasureRecord) {
        measureDao.insertRecord(record)
    }

    suspend fun delete(record: MeasureRecord) {
        measureDao.deleteRecord(record)
    }

    suspend fun deleteById(id: Int) {
        measureDao.deleteById(id)
    }

    suspend fun clearAll() {
        measureDao.clearAll()
    }
}
