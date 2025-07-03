package com.example.gpstracking

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class LocationService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var locationClient: LocationClient
    private var repId: String = "unknown"

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
        val notification = NotificationCompat.Builder(this, "location")
            .setContentTitle("Tracking location...")
            .setContentText("Location: null")
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setOngoing(true)

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        locationClient
            .getLocationUpdates(5 * 60 * 1000L) // every 5 mins
            .catch { e -> e.printStackTrace() }
            .onEach { location ->
                val lat = location.latitude.toString()
                val lon = location.longitude.toString()


                Log.d("LocationService", "Lat: $lat, Lon: $lon, repId: $repId")

                // Send periodic location update
                val url = "https://php-login-app-production.up.railway.app/location_handler.php"
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
                            "action" to "location_update"
                        )
                    }
                }

                stringRequest.retryPolicy = DefaultRetryPolicy(
                    10000, // timeout in ms (10 seconds)
                    3,     // max retry count
                    1.0f   // backoff multiplier
                )


                requestQueue.add(stringRequest)

                // Update ongoing notification
                val updatedNotification = notification.setContentText("Location: ($lat, $lon)")
                notificationManager.notify(1, updatedNotification.build())
            }
            .launchIn(serviceScope)

        startForeground(1, notification.build())
    }

    private fun stop() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
    }
}
