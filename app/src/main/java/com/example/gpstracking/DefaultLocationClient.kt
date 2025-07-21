package com.example.yourapp.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
import com.example.gpstracking.LocationClient
import com.google.android.gms.location.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class DefaultLocationClient(
    private val context: Context,
    private val fusedLocationProviderClient: FusedLocationProviderClient
): LocationClient {

    @SuppressLint("MissingPermission")
    override fun getLocationUpdates(interval: Long): Flow<Location> {
        return callbackFlow {
            val request = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                interval
            )
                .setMinUpdateIntervalMillis(interval)
                .setMaxUpdateDelayMillis(interval + 60 * 1000L) // optional buffer
                .build()

            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    super.onLocationResult(result)
                    for (location in result.locations) {
                        Log.d("LocationFlow", "Location: ${location.latitude}, ${location.longitude}")
                        trySend(location)
                    }
                }
            }

            fusedLocationProviderClient.requestLocationUpdates(
                request,
                callback,
                Looper.getMainLooper()
            )

            awaitClose {
                fusedLocationProviderClient.removeLocationUpdates(callback)
            }
        }
    }
}
