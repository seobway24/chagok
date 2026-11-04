package com.team.chagok.core.privacy

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 차 밖으로 나간 요청 한 건. 엔딩 화면 "밖으로 나간 데이터"에 그대로 보인다. */
data class EgressRecord(
    val atMillis: Long,
    val engineId: String,
    val bytesOut: Int,
    val bytesIn: Int,
    val maskedText: String,
)

/** 회차 동안 메모리에만 쌓는다. 회차가 끝나면 비운다. */
class EgressLog {
    private val _records = MutableStateFlow<List<EgressRecord>>(emptyList())
    val records: StateFlow<List<EgressRecord>> = _records.asStateFlow()

    fun add(record: EgressRecord) = _records.update { it + record }

    fun clear() {
        _records.value = emptyList()
    }

    val totalBytes: Int get() = _records.value.sumOf { it.bytesOut + it.bytesIn }
}
