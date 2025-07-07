package com.example.gpstracking

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.example.gpstracking.ui.theme.GPSTrackingTheme
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private val LOCATION_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_BACKGROUND_LOCATION
    )
    private val PERMISSION_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val masterKey = MasterKey.Builder(applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val sharedPref = EncryptedSharedPreferences.create(
            applicationContext,
            "UserSession",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        val repId = sharedPref.getString("username", null)

        if (repId == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        if (!hasLocationPermissions()) {
            ActivityCompat.requestPermissions(this, LOCATION_PERMISSIONS, PERMISSION_REQUEST_CODE)
        }

        setContent {
            GPSTrackingTheme {
                var isTracking by remember { mutableStateOf(false) }
                var clockInInProgress by remember { mutableStateOf(false) }
                val clockInTimePref = remember { mutableStateOf(sharedPref.getString("clock_in_time", null)) }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("GPS Tracking", color = Color.White) },
                            backgroundColor = Color(0xFF0D47A1)
                        )
                    },
                    content = { paddingValues ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(paddingValues)
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("Welcome back, $repId!", style = MaterialTheme.typography.h6, color = Color(0xFF0D47A1))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Rep ID: $repId", style = MaterialTheme.typography.body1, color = Color.DarkGray)

                            Spacer(modifier = Modifier.height(24.dp))
                            //Clocking button
                            Button(
                                onClick = {

                                    if (clockInInProgress) return@Button  // Prevent double taps
                                    clockInInProgress = true

                                    if (hasLocationPermissions()) {
                                        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this@MainActivity)

                                        if (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                                            ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                                            Toast.makeText(this@MainActivity, "Location permission not granted", Toast.LENGTH_SHORT).show()
                                            clockInInProgress = false
                                            return@Button
                                        }

                                        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)// get current location
                                            .addOnSuccessListener { location ->
                                                if (location != null) {
                                                    val lat = location.latitude.toString()
                                                    val lon = location.longitude.toString()
                                                    val clockInTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

                                                    sharedPref.edit().putString("clock_in_time", clockInTime).apply()// clockin
                                                    clockInTimePref.value = clockInTime

                                                    val url = "http://mudithappl-001-site1.dtempurl.com/php-login-app/public/location_handler.php"
                                                    val requestQueue = Volley.newRequestQueue(applicationContext)

                                                    val stringRequest = object : StringRequest(Method.POST, url,
                                                        Response.Listener { response ->
                                                            Log.d("ClockInSuccess", "Server response: $response")
                                                            Toast.makeText(this@MainActivity, "Clocked in successfully", Toast.LENGTH_SHORT).show()
                                                            startTracking(repId)
                                                            isTracking = true
                                                            clockInInProgress = false
                                                        },
                                                        Response.ErrorListener { error ->
                                                            Log.e("ClockInError", "Error: ${error.message}")
                                                            Toast.makeText(this@MainActivity, "Clock in failed", Toast.LENGTH_SHORT).show()
                                                            clockInInProgress = false
                                                        }
                                                    ) {
                                                        override fun getParams(): MutableMap<String, String> {
                                                            return hashMapOf(
                                                                "rep_id" to repId,
                                                                "latitude" to lat,
                                                                "longitude" to lon,
                                                                "clock_in_time" to clockInTime,
                                                                "action" to "clock_in"
                                                            )
                                                        }
                                                    }
                                                    //Disable retries
                                                    stringRequest.retryPolicy = DefaultRetryPolicy(
                                                        10000, 0, 1f
                                                    )

                                                    requestQueue.add(stringRequest)

                                                    //startTracking(repId)// starts LocationService with ACTION_START
                                                    //isTracking = true
                                                } else {
                                                    Toast.makeText(this@MainActivity, "Location not available", Toast.LENGTH_SHORT).show()
                                                    clockInInProgress = false
                                                }
                                            }
                                    } else {
                                        ActivityCompat.requestPermissions(
                                            this@MainActivity,
                                            LOCATION_PERMISSIONS,
                                            PERMISSION_REQUEST_CODE
                                        )
                                        clockInInProgress = false
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF1565C0))
                            ) {
                                Text("Clock In", color = Color.White)
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            //Clock Out Button
                            Button(
                                onClick = {
                                    stopTracking(repId)
                                    isTracking = false
                                    sharedPref.edit().remove("clock_in_time").apply()
                                    clockInTimePref.value = null
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFD32F2F))
                            ) {
                                Text("Clock Out", color = Color.White)
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            if (clockInTimePref.value != null) {
                                Text("You clocked in at: ${clockInTimePref.value}", color = Color(0xFF2E7D32))
                            } else {
                                Text("You are not clocked in", color = Color(0xFFD32F2F))
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = if (isTracking) "Tracking is ON" else "Tracking is OFF",
                                color = if (isTracking) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                            )

                            Spacer(modifier = Modifier.height(32.dp))
                            //Logout Button
                            Button(
                                onClick = {
                                    sharedPref.edit().clear().apply()
                                    startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                                    finish()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(45.dp),
                                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF78909C))
                            ) {
                                Text("Logout", color = Color.White)
                            }
                        }
                    }
                )
            }
        }

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }
    }

    private fun hasLocationPermissions(): Boolean {
        return LOCATION_PERMISSIONS.all {
            ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun startTracking(repId: String) {
        Intent(applicationContext, LocationService::class.java).apply {
            action = LocationService.ACTION_START
            putExtra("rep_id", repId)
            startService(this)
        }
    }

    private fun stopTracking(repId: String) {
        Intent(applicationContext, LocationService::class.java).apply {
            action = LocationService.ACTION_STOP
            putExtra("rep_id", repId)
            startService(this)
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "Location permission not granted", Toast.LENGTH_SHORT).show()
            return
        }

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location != null) {
                    val lat = location.latitude.toString()
                    val lon = location.longitude.toString()
                    val clockOutTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

                    val url = "http://mudithappl-001-site1.dtempurl.com/php-login-app/public/location_handler.php"
                    val requestQueue = Volley.newRequestQueue(applicationContext)

                    val stringRequest = object : StringRequest(Method.POST, url,
                        Response.Listener { response ->
                            Log.d("ClockOutSuccess", "Server response: $response")
                            Toast.makeText(this, "Clocked out successfully", Toast.LENGTH_SHORT).show()
                        },
                        Response.ErrorListener { error ->
                            Log.e("ClockOutError", "Error: ${error.message}")
                            Toast.makeText(this, "Clock out failed", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        override fun getParams(): MutableMap<String, String> {
                            return hashMapOf(
                                "rep_id" to repId,
                                "latitude" to lat,
                                "longitude" to lon,
                                "clock_out_time" to clockOutTime,
                                "action" to "clock_out"
                            )
                        }
                    }

                    requestQueue.add(stringRequest)
                } else {
                    Toast.makeText(this, "Location not available", Toast.LENGTH_SHORT).show()
                }
            }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            val masterKey = MasterKey.Builder(applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val sharedPref = EncryptedSharedPreferences.create(
                applicationContext,
                "UserSession",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            val repId = sharedPref.getString("username", "unknown") ?: "unknown"
            startTracking(repId)
        }
    }
}
