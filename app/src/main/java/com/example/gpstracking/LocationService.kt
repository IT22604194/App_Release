package com.example.gpstracking

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class LocationService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var locationClient: LocationClient
    private var repId: String = "unknown"

    private var trackingJob: Job? = null
    private var intervalCheckerJob: Job? = null
    private var currentIntervalMillis: Long = 5 * 60 * 1000L // default 5 minutes

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        locationClient = DefaultLocationClient(
            applicationContext,
            LocationServices.getFusedLocationProviderClient(applicationContext)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        repId = intent?.getStringExtra("rep_id") ?: "unknown"

        when (intent?.action) {
            ACTION_START -> start()
            ACTION_STOP -> stop()
        }

        return START_STICKY
    }

    private fun start() {
        val notificationBuilder = NotificationCompat.Builder(this, "location")
            .setContentTitle("Tracking location...")
            .setContentText("Location: null")
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setOngoing(true)

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        startForeground(1, notificationBuilder.build())

        // Initial fetch and start tracking
        fetchTrackingInterval { intervalMillis ->
            currentIntervalMillis = intervalMillis
            startTracking(currentIntervalMillis, notificationManager, notificationBuilder)
        }

        // Periodically check if interval has changed in DB
        intervalCheckerJob = serviceScope.launch {
            while (true) {
                delay(1 * 60 * 1000L) // check every 5 minutes
                fetchTrackingInterval { newIntervalMillis ->
                    if (newIntervalMillis != currentIntervalMillis) {
                        Log.d("LocationService", "Interval changed: $currentIntervalMillis -> $newIntervalMillis")
                        currentIntervalMillis = newIntervalMillis
                        startTracking(currentIntervalMillis, notificationManager, notificationBuilder)
                    }
                }
            }
        }
    }

    private fun startTracking(
        intervalMillis: Long,
        notificationManager: NotificationManager,
        notificationBuilder: NotificationCompat.Builder
    ) {
        trackingJob?.cancel()
        trackingJob = null // force reset

        Log.d("LocationService", "Starting tracking with interval: $intervalMillis ms")

        trackingJob = locationClient
            .getLocationUpdates(intervalMillis)
            .catch { e -> e.printStackTrace() }
            .onEach { location ->
                val lat = location.latitude.toString()
                val lon = location.longitude.toString()
                val batteryLevel = getBatteryLevel().toString()

                Log.d("LocationService", "Lat: $lat, Lon: $lon, repId: $repId, Battery: $batteryLevel")

                val url = "http://mudithappl-001-site1.dtempurl.com/php-login-app/public/location_handler.php"
                val requestQueue = Volley.newRequestQueue(applicationContext)

                val stringRequest = object : StringRequest(Method.POST, url,
                    Response.Listener { response ->
                        Log.d("VolleySuccess", "Server response: $response")
                    },
                    Response.ErrorListener { error ->
                        Log.e("VolleyError", "Error: ${error.message}")
                    }
                ) {
                    override fun getParams(): MutableMap<String, String> {
                        return hashMapOf(
                            "rep_id" to repId,
                            "latitude" to lat,
                            "longitude" to lon,
                            "battery_level" to batteryLevel,
                            "action" to "location_update"
                        )
                    }
                }

                stringRequest.retryPolicy = DefaultRetryPolicy(10000, 3, 1.0f)
                requestQueue.add(stringRequest)

                val updatedNotification = notificationBuilder.setContentText("Location: ($lat, $lon)")
                notificationManager.notify(1, updatedNotification.build())
            }
            .launchIn(serviceScope)
    }


    private fun fetchTrackingInterval(onResult: (Long) -> Unit) {
        Thread {
            try {
                val url = URL("http://mudithappl-001-site1.dtempurl.com/php-login-app/public/get_tracking_interval.php?t=${System.currentTimeMillis()}")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val intervalMinutes = json.getInt("tracking_interval_min")

                onResult(intervalMinutes * 60 * 1000L) // convert to ms
            } catch (e: Exception) {
                Log.e("IntervalFetch", "Error: ${e.message}")
                onResult(5 * 60 * 1000L) // fallback
            }
        }.start()
    }

    private fun getBatteryLevel(): Int {
        val batteryStatus: Intent? = registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return ((level / scale.toFloat()) * 100).toInt()
    }

    private fun stop() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        trackingJob?.cancel()
        intervalCheckerJob?.cancel()
    }

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
    }
}
