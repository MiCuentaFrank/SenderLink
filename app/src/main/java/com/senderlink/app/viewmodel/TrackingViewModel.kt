package com.senderlink.app.viewmodel

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.senderlink.app.network.CompleteRouteRequest
import com.senderlink.app.network.CreateRouteRequest
import com.senderlink.app.network.PointData
import com.senderlink.app.model.Route
import com.senderlink.app.repository.RouteRepository
import com.senderlink.app.service.LocationTrackingService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TrackingViewModel : ViewModel() {

    private val routeRepo = RouteRepository()

    // ── Estado del servicio (lectura directa del companion object) ──
    val trackPoints:    StateFlow<List<LatLng>> = LocationTrackingService.trackPoints
    val totalDistanceM: StateFlow<Double>       = LocationTrackingService.totalDistanceM
    val isRunning:      StateFlow<Boolean>      = LocationTrackingService.isRunning
    val isPaused:       StateFlow<Boolean>      = LocationTrackingService.isPaused
    val elapsedSeconds: StateFlow<Long>         = LocationTrackingService.elapsedSeconds
    val currentLocation:StateFlow<LatLng?>      = LocationTrackingService.currentLocation

    // ── Resultados de operaciones de red ──
    private val _createRouteResult = MutableStateFlow<CreateRouteState>(CreateRouteState.Idle)
    val createRouteResult: StateFlow<CreateRouteState> = _createRouteResult

    private val _completeRouteResult = MutableStateFlow<CompleteRouteState>(CompleteRouteState.Idle)
    val completeRouteResult: StateFlow<CompleteRouteState> = _completeRouteResult

    // ─────────────────────────────────────
    // Grabación (nueva ruta)
    // ─────────────────────────────────────
    fun startRecording(context: Context) = sendAction(context, LocationTrackingService.ACTION_START_RECORDING)
    fun stopRecording(context: Context)  = sendAction(context, LocationTrackingService.ACTION_STOP_RECORDING)
    fun pauseRecording(context: Context) = sendAction(context, LocationTrackingService.ACTION_PAUSE_RECORDING)
    fun resumeRecording(context: Context)= sendAction(context, LocationTrackingService.ACTION_RESUME_RECORDING)

    // ─────────────────────────────────────
    // Seguimiento (ruta existente)
    // ─────────────────────────────────────
    fun startTracking(context: Context)  = sendAction(context, LocationTrackingService.ACTION_START_TRACKING)
    fun stopTracking(context: Context)   = sendAction(context, LocationTrackingService.ACTION_STOP_TRACKING)

    private fun sendAction(context: Context, action: String) {
        val intent = Intent(context, LocationTrackingService::class.java).apply { this.action = action }
        ContextCompat.startForegroundService(context, intent)
    }

    // ─────────────────────────────────────
    // Subir ruta al backend
    // ─────────────────────────────────────
    fun createRoute(uid: String, name: String, description: String, difficulty: String) {
        val points = trackPoints.value
        if (points.size < 2) {
            _createRouteResult.value = CreateRouteState.Error("La ruta debe tener al menos 2 puntos GPS")
            return
        }

        viewModelScope.launch {
            _createRouteResult.value = CreateRouteState.Loading
            try {
                val request = CreateRouteRequest(
                    uid         = uid,
                    name        = name,
                    description = description.ifBlank { null },
                    difficulty  = difficulty,
                    points      = points.map { PointData(it.latitude, it.longitude) },
                    distanceKm  = totalDistanceM.value / 1000.0
                )
                val response = routeRepo.createRoute(request)
                _createRouteResult.value = if (response.ok)
                    CreateRouteState.Success(response.route)
                else
                    CreateRouteState.Error(response.message ?: "Error al guardar la ruta")
            } catch (e: Exception) {
                _createRouteResult.value = CreateRouteState.Error(e.message ?: "Error de conexión")
            }
        }
    }

    // ─────────────────────────────────────
    // Registrar finalización de ruta
    // ─────────────────────────────────────
    fun completeRoute(uid: String, routeId: String) {
        val durationMin = (elapsedSeconds.value / 60).toInt()
        viewModelScope.launch {
            _completeRouteResult.value = CompleteRouteState.Loading
            try {
                val response = routeRepo.completeRoute(routeId, CompleteRouteRequest(uid, durationMin))
                _completeRouteResult.value = if (response.ok)
                    CompleteRouteState.Success(response.xpGained, response.newLevel, response.newRankTitle)
                else
                    CompleteRouteState.Error(response.message ?: "Error al registrar la finalización")
            } catch (e: Exception) {
                _completeRouteResult.value = CompleteRouteState.Error(e.message ?: "Error de conexión")
            }
        }
    }

    fun resetCreateResult()   { _createRouteResult.value   = CreateRouteState.Idle }
    fun resetCompleteResult() { _completeRouteResult.value = CompleteRouteState.Idle }

    // ─────────────────────────────────────
    // Estados
    // ─────────────────────────────────────
    sealed class CreateRouteState {
        object Idle    : CreateRouteState()
        object Loading : CreateRouteState()
        data class Success(val route: Route)      : CreateRouteState()
        data class Error(val message: String)     : CreateRouteState()
    }

    sealed class CompleteRouteState {
        object Idle    : CompleteRouteState()
        object Loading : CompleteRouteState()
        data class Success(val xpGained: Int, val newLevel: Int, val rankTitle: String) : CompleteRouteState()
        data class Error(val message: String) : CompleteRouteState()
    }
}
