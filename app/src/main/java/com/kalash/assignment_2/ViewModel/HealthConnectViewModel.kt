package com.kalash.assignment_2.ViewModel

import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

class HealthConnectViewModel : ViewModel() {
    private val _heartRateRecords = MutableStateFlow<List<HeartRateRecord>>(emptyList())
    val heartRateRecords: StateFlow<List<HeartRateRecord>> = _heartRateRecords.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun clearError() {
        _error.value = null
    }

    suspend fun saveHeartRate(
        healthConnectClient: HealthConnectClient,
        heartRate: Int,
        timestamp: Instant
    ) {
        try {
            val heartRateRecord = HeartRateRecord(
                startTime = timestamp,
                startZoneOffset = ZoneOffset.systemDefault().getRules().getOffset(timestamp),
                endTime = timestamp,
                endZoneOffset = ZoneOffset.systemDefault().getRules().getOffset(timestamp),
                samples = listOf(
                    HeartRateRecord.Sample(
                        beatsPerMinute = heartRate.toLong(),
                        time = timestamp
                    )
                )
            )
            
            healthConnectClient.insertRecords(listOf(heartRateRecord))
            _error.value = null
            
            // Automatically load records after saving
            loadHeartRates(healthConnectClient)
        } catch (e: Exception) {
            Log.e("HealthConnectViewModel", "Error saving heart rate", e)
            _error.value = "Failed to save heart rate: ${e.message}"
        }
    }

    suspend fun loadHeartRates(healthConnectClient: HealthConnectClient) {
        try {
            val endTime = ZonedDateTime.now().toInstant()
            val startTime = endTime.minus(24, ChronoUnit.HOURS)
            
            val request = ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
            
            val response = healthConnectClient.readRecords(request)
            _heartRateRecords.value = response.records.sortedByDescending { it.startTime }
            _error.value = null
            
            Log.d("HealthConnectViewModel", "Loaded ${response.records.size} heart rate records")
        } catch (e: Exception) {
            Log.e("HealthConnectViewModel", "Error loading heart rates", e)
            _error.value = "Failed to load heart rates: ${e.message}"
        }
    }
}
