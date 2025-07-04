package com.example.gpstracking

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
import com.example.gpstracking.ui.theme.GPSTrackingTheme
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

class RegisterActivity : ComponentActivity() {
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
                            title = { Text("Register", color = Color.White) },
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
                            text = "Create a New Account",
                            style = MaterialTheme.typography.h6,
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
                                performRegister(username, password) { result ->
                                    loading = false
                                    Toast.makeText(this@RegisterActivity, result, Toast.LENGTH_SHORT).show()
                                    if (result == "Registration successful") {
                                        finish()
                                    }
                                }
                            },
                            enabled = !loading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF1565C0))
                        ) {
                            if (loading) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Register", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }


    private fun performRegister(username: String, password: String, onResult: (String) -> Unit) {
        Thread {
            try {
                val url = URL("http://mudithappl-001-site1.dtempurl.com/php-login-app/public/Register.php")
                val postData = "username=${URLEncoder.encode(username, "UTF-8")}&password=${URLEncoder.encode(password, "UTF-8")}"


                with(url.openConnection() as HttpURLConnection)
                {
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
                    onResult("Error: ${e.message}")
                }
            }
        }.start()
    }
}
