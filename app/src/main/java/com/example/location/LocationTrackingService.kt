package com.example.location

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.WaymarkApplication
import com.example.domain.DistanceUnit
import com.example.domain.GeoMath
import com.example.domain.LiveTrackerState
import com.example.domain.LocationSample
import com.example.domain.TrackerPhase
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LocationTrackingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var coordinator: TrackerCoordinator
    private var tickerJob: Job? = null
    private var notificationJob: Job? = null
    private var isLocationRegistered = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            for (loc in result.locations) {
                val sample = LocationSample(
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    timestampMs = if (loc.time > 0L) loc.time else System.currentTimeMillis(),
                    speedMps = if (loc.hasSpeed()) loc.speed else null,
                    accuracyMeters = if (loc.hasAccuracy()) loc.accuracy else 20f,
                )
                coordinator.onLocationUpdate(sample)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val app = application as WaymarkApplication
        coordinator = app.trackerCoordinator
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopTrackingAndService()
                return START_NOT_STICKY
            }

            else -> {
                if (!hasLocationPermission()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForegroundWithNotification()
                coordinator.onServiceStarted()
                startLocationUpdates()
                startDwellTicker()
                observeStateForNotification()
                return START_STICKY
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopLocationUpdates()
        tickerJob?.cancel()
        notificationJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startForegroundWithNotification() {
        val notification = buildNotification(
            LiveTrackerState(isTrackingActive = true),
            DistanceUnit.MILES,
        )
        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgsType)
    }

    private fun startLocationUpdates() {
        if (isLocationRegistered) return
        if (!hasLocationPermission()) return

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(FASTEST_INTERVAL_MS)
            .setMinUpdateDistanceMeters(MIN_UPDATE_DISTANCE_METERS)
            .build()

        try {
            fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
            isLocationRegistered = true
        } catch (_: SecurityException) {
            stopTrackingAndService()
        }
    }

    private fun stopLocationUpdates() {
        if (isLocationRegistered) {
            fusedClient.removeLocationUpdates(locationCallback)
            isLocationRegistered = false
        }
    }

    private fun startDwellTicker() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            while (isActive) {
                delay(TICK_INTERVAL_MS)
                coordinator.onPeriodicTick(System.currentTimeMillis())
            }
        }
    }

    private fun observeStateForNotification() {
        notificationJob?.cancel()
        val app = application as WaymarkApplication
        notificationJob = serviceScope.launch {
            combine(
                coordinator.liveState,
                app.preferencesRepository.preferences,
            ) { state, prefs ->
                state to prefs.distanceUnit
            }.collect { (state, unit) ->
                val nm = getSystemService(NotificationManager::class.java)
                nm.notify(NOTIFICATION_ID, buildNotification(state, unit))
            }
        }
    }

    private fun stopTrackingAndService() {
        stopLocationUpdates()
        tickerJob?.cancel()
        notificationJob?.cancel()
        coordinator.onServiceStopped()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(state: LiveTrackerState, unit: DistanceUnit): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPending = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val title = when (state.phase) {
            TrackerPhase.IDLE -> "Waymark tracking active"
            TrackerPhase.MOVING -> "Traveling (${state.inferredMode.label}) • ${GeoMath.formatDistance(state.activeDistanceMeters, unit)}"
            TrackerPhase.DWELLING -> "Stopped • ${GeoMath.formatCountdown(state.dwellRemainingMs)} to lock destination"
            TrackerPhase.AT_DESTINATION -> "At destination • waiting for next departure"
        }

        val text = when (state.phase) {
            TrackerPhase.MOVING -> "Current speed ${GeoMath.formatSpeed(state.currentSpeedMps, unit)}"
            TrackerPhase.DWELLING -> "Leg distance ${GeoMath.formatDistance(state.activeDistanceMeters, unit)}"
            else -> "Measuring car & bike distance automatically"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_waymark)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentPending)
            .addAction(0, getString(R.string.stop_tracking), stopPending)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "waymark_tracking_channel"
        private const val NOTIFICATION_ID = 4102
        private const val ACTION_START = "com.example.location.ACTION_START"
        private const val ACTION_STOP = "com.example.location.ACTION_STOP"
        private const val UPDATE_INTERVAL_MS = 4_000L
        private const val FASTEST_INTERVAL_MS = 2_000L
        private const val MIN_UPDATE_DISTANCE_METERS = 3f
        private const val TICK_INTERVAL_MS = 5_000L

        fun start(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
