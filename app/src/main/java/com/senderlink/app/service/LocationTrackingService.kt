package com.senderlink.app.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.senderlink.app.R
import com.senderlink.app.db.AppDatabase
import com.senderlink.app.db.TrackPointEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class LocationTrackingService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var timerJob: Job? = null
    private lateinit var db: AppDatabase

    companion object {
        const val ACTION_START_RECORDING = "ACTION_START_RECORDING"
        const val ACTION_STOP_RECORDING  = "ACTION_STOP_RECORDING"
        const val ACTION_PAUSE_RECORDING = "ACTION_PAUSE_RECORDING"
        const val ACTION_RESUME_RECORDING = "ACTION_RESUME_RECORDING"
        const val ACTION_START_TRACKING  = "ACTION_START_TRACKING"
        const val ACTION_STOP_TRACKING   = "ACTION_STOP_TRACKING"

        private const val CHANNEL_ID      = "tracking_channel"
        const val NOTIFICATION_ID         = 1001

        // Estado compartido (leído por TrackingViewModel)
        val trackPoints      = MutableStateFlow<List<LatLng>>(emptyList())
        val totalDistanceM   = MutableStateFlow(0.0)
        val isRunning        = MutableStateFlow(false)
        val isPaused         = MutableStateFlow(false)
        val elapsedSeconds   = MutableStateFlow(0L)
        val currentLocation  = MutableStateFlow<LatLng?>(null)
        var currentSessionId = ""
    }

    // ─────────────────────────────────────────
    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        db = AppDatabase.getInstance(this)
        setupLocationCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RECORDING  -> startRecording()
            ACTION_STOP_RECORDING   -> stopRecording()
            ACTION_PAUSE_RECORDING  -> pauseRecording()
            ACTION_RESUME_RECORDING -> resumeRecording()
            ACTION_START_TRACKING   -> startTracking()
            ACTION_STOP_TRACKING    -> stopTracking()
        }
        return START_STICKY
    }

    // ─────────────────────────────────────────
    private fun startRecording() {
        currentSessionId = UUID.randomUUID().toString()
        trackPoints.value    = emptyList()
        totalDistanceM.value = 0.0
        elapsedSeconds.value = 0L
        isRunning.value      = true
        isPaused.value       = false

        startForeground(NOTIFICATION_ID, buildNotification("Grabando ruta..."))
        startLocationUpdates()
        startTimer()
    }

    private fun stopRecording() {
        stopLocationUpdates()
        timerJob?.cancel()
        isRunning.value = false
        isPaused.value  = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun pauseRecording() {
        stopLocationUpdates()
        timerJob?.cancel()
        isPaused.value = true
        updateNotification("Grabación pausada")
    }

    private fun resumeRecording() {
        startLocationUpdates()
        startTimer()
        isPaused.value = false
        updateNotification("Grabando ruta...")
    }

    private fun startTracking() {
        trackPoints.value    = emptyList()
        totalDistanceM.value = 0.0
        elapsedSeconds.value = 0L
        isRunning.value      = true
        isPaused.value       = false

        startForeground(NOTIFICATION_ID, buildNotification("Siguiendo ruta..."))
        startLocationUpdates()
        startTimer()
    }

    private fun stopTracking() {
        stopLocationUpdates()
        timerJob?.cancel()
        isRunning.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ─────────────────────────────────────────
    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (isPaused.value) return
                val location = result.lastLocation ?: return
                val newLatLng = LatLng(location.latitude, location.longitude)
                currentLocation.value = newLatLng

                val current = trackPoints.value.toMutableList()

                if (current.isNotEmpty()) {
                    val last = current.last()
                    val dist = FloatArray(1)
                    Location.distanceBetween(
                        last.latitude, last.longitude,
                        newLatLng.latitude, newLatLng.longitude,
                        dist
                    )
                    // Filtrar jitter GPS < 5m y saltos > 300m
                    if (dist[0] < 5f || dist[0] > 300f) return
                    totalDistanceM.value += dist[0]
                }

                current.add(newLatLng)
                trackPoints.value = current

                // Persistir en Room solo si estamos grabando (tiene sessionId)
                if (currentSessionId.isNotBlank()) {
                    serviceScope.launch {
                        db.trackPointDao().insert(
                            TrackPointEntity(
                                sessionId = currentSessionId,
                                lat       = location.latitude,
                                lng       = location.longitude,
                                altitude  = location.altitude,
                                speed     = location.speed
                            )
                        )
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
            .setMinUpdateDistanceMeters(5f)
            .build()
        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun startTimer() {
        timerJob = serviceScope.launch {
            while (true) {
                delay(1_000)
                elapsedSeconds.value++
            }
        }
    }

    // ─────────────────────────────────────────
    private fun buildNotification(content: String): Notification {
        createChannel()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SenderLink")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(content: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(content))
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Rastreo GPS",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Notificación activa mientras se graba o sigue una ruta" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        timerJob?.cancel()
        serviceScope.cancel()
        isRunning.value = false
    }
}
