package com.example.gpstracking

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.gpstracking.ui.theme.GPSTrackingTheme
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import java.text.SimpleDateFormat
import java.util.*

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
                                        // Save session securely
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
                                        val loginTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

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

    private fun performLogin(username: String, password: String, onResult: (String) -> Unit) {
        Thread {
            try {
                val url = URL("http://mudithappl-001-site1.dtempurl.com/php-login-app/public/Login.php")
                val postData = "username=${username}&password=${password}"

                with(url.openConnection() as HttpURLConnection) {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Android) MyApp/1.0")

                    outputStream.use { it.write(postData.toByteArray()) }

                    val responseCode = responseCode
                    val responseText = if (responseCode == HttpURLConnection.HTTP_OK) {
                        inputStream.bufferedReader().readText()
                    } else {
                        errorStream?.bufferedReader()?.readText() ?: "HTTP error code: $responseCode"
                    }

                    val message = try {
                        val json = JSONObject(responseText)
                        json.optString("message", "Unknown response")
                    } catch (e: Exception) {
                        "Invalid response from server"
                    }

                    Handler(Looper.getMainLooper()).post {
                        onResult(message)
                    }
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    onResult("Exception: ${e.javaClass.simpleName} - ${e.localizedMessage}")
                    e.printStackTrace()
                }
            }
        }.start()
    }
}
