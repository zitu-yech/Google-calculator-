package com.example.data

import kotlinx.coroutines.flow.Flow

class HistoryRepository(private val historyDao: HistoryDao) {
    val allHistory: Flow<List<HistoryEntity>> = historyDao.getAllHistory()

    suspend fun insert(item: HistoryEntity) {
        historyDao.insertHistory(item)
    }

    suspend fun clear() {
        historyDao.clearHistory()
    }

    suspend fun deleteById(id: Long) {
        historyDao.deleteHistoryById(id)
    }
}
