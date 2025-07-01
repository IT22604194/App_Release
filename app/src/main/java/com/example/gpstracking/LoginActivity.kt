package com.example.gpstracking

// Core Compose
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

// Material Icons
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff

// Optional if using Scaffold or TopAppBar
import androidx.compose.material.Scaffold
import androidx.compose.material.TopAppBar

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

import com.example.gpstracking.ui.theme.GPSTrackingTheme
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.net.ssl.HttpsURLConnection

class LoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GPSTrackingTheme {
                var username by remember { mutableStateOf("") }
                var password by remember { mutableStateOf("") }
                var loading by remember { mutableStateOf(false) }
                var showPassword by remember { mutableStateOf(false) }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Login", color = Color.White) },
                            backgroundColor = Color(0xFF0D47A1)
                        )
                    }
                ) { paddingValues ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Welcome Back!",
                            style = MaterialTheme.typography.h5,
                            color = Color(0xFF0D47A1)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Username") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password") },
                            singleLine = true,
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                val icon = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(icon, contentDescription = "Toggle password visibility")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                loading = true
                                performLogin(username, password) { result ->
                                    loading = false
                                    if (result == "Login successful") {
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
                                        val loginTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(
                                            Date()
                                        )

                                        sharedPref.edit()
                                            .putString("username", username)
                                            .putString("login_time", loginTime)
                                            .apply()
                                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                                        finish()
                                    } else {
                                        Toast.makeText(this@LoginActivity, result, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            enabled = !loading,
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF1565C0))
                        ) {
                            if (loading) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Login", color = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        TextButton(
                            onClick = {
                                startActivity(Intent(this@LoginActivity, RegisterActivity::class.java))
                            }
                        ) {
                            Text("Don't have an account? Register here", color = Color(0xFF0D47A1))
                        }
                    }
                }
            }
        }

    }

    fun performLogin(username: String, password: String, onResult: (String) -> Unit) {
        Thread {
            try {
                val url = URL("https://ashani.fwh.is/Login.php")
                val postData = "username=$username&password=$password"

                with(url.openConnection() as HttpsURLConnection) {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

                    outputStream.use { it.write(postData.toByteArray()) }

                    val response = inputStream.bufferedReader().readText()
                    Handler(Looper.getMainLooper()).post {
                        onResult(response.trim())
                    }
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    //onResult("Error: ${e.message}")
                    onResult("Error: ${e.javaClass.name} - ${e.localizedMessage}")
                    e.printStackTrace()

                }
            }
        }.start()
    }
}
