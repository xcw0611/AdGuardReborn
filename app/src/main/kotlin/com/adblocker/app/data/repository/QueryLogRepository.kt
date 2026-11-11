package com.adblocker.app.data.repository

import com.adblocker.app.data.db.QueryLogDao
import com.adblocker.app.data.db.QueryLogEntity
import com.adblocker.core.model.QueryLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedDeque

class QueryLogRepository(
    private val dao: QueryLogDao,
    private val scope: CoroutineScope
) {
    private val maxMemoryLogs = 150
    private val memoryDeque = ConcurrentLinkedDeque<QueryLog>()

    private val _logsFlow = MutableStateFlow<List<QueryLog>>(emptyList())
    val logsFlow: StateFlow<List<QueryLog>> = _logsFlow.asStateFlow()

    fun emitEvent(log: QueryLog) {
        memoryDeque.addFirst(log)
        while (memoryDeque.size > maxMemoryLogs) {
            memoryDeque.removeLast()
        }
        _logsFlow.value = memoryDeque.toList()

        // Asynchronously persist to database
        scope.launch(Dispatchers.IO) {
            try {
                dao.insert(QueryLogEntity.fromModel(log))
            } catch (e: Exception) {
                // Ignore DB write errors
            }
        }
    }

    fun clear() {
        memoryDeque.clear()
        _logsFlow.value = emptyList()
        scope.launch(Dispatchers.IO) {
            dao.clearAll()
        }
    }
}
