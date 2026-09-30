package com.example.aaharin.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.aaharin.network.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private sealed interface TestState {
    data object Idle : TestState
    data object Running : TestState
    data class Ok(val message: String) : TestState
    data class Failed(val message: String) : TestState
}

/**
 * Reachability check for an address before it is saved.
 *
 * A GET at the API root is answered by the login route, which is POST-only, so a working
 * server replies with a JSON error rather than nothing. Looking for that `code` field is
 * what separates "the Aaharin server answered" from "something else is on this address".
 */
private suspend fun testServer(baseUrl: String): TestState = withContext(Dispatchers.IO) {
    try {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        client.newCall(Request.Builder().url(baseUrl).get().build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (body.contains("\"code\"")) {
                TestState.Ok("Server found")
            } else {
                TestState.Failed("Something answered, but it is not the Aaharin server")
            }
        }
    } catch (e: Exception) {
        TestState.Failed(e.localizedMessage ?: "Could not reach the server")
    }
}

/**
 * Lets each device be pointed at its own server.
 *
 * Without this the address is fixed at build time, so a tester on another network gets
 * "Failed to connect" against an address that only exists on the developer's Wi-Fi, and
 * every change of address means a new APK.
 */
@Composable
fun ServerSetupDialog(
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    var address by remember { mutableStateOf(ServerConfig.address) }
    var state by remember { mutableStateOf<TestState>(TestState.Idle) }
    val scope = rememberCoroutineScope()

    val preview = remember(address) { ServerConfig.toBaseUrl(address) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Dns, contentDescription = null) },
        title = { Text("Server Address", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Enter the address of the machine running Aaharin - a local IP, a " +
                        "Tailscale address, or a tunnel URL.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = {
                        address = it
                        state = TestState.Idle
                    },
                    label = { Text("IP or address") },
                    placeholder = { Text("localhost:20080") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (address.isNotBlank()) {
                    Text(
                        text = preview,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                when (val current = state) {
                    is TestState.Running -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Checking...", style = MaterialTheme.typography.bodySmall)
                    }

                    is TestState.Ok -> StatusLine(
                        icon = Icons.Default.CheckCircle,
                        text = current.message,
                        color = MaterialTheme.colorScheme.primary
                    )

                    is TestState.Failed -> StatusLine(
                        icon = Icons.Default.ErrorOutline,
                        text = current.message,
                        color = MaterialTheme.colorScheme.error
                    )

                    TestState.Idle -> Unit
                }

                TextButton(
                    onClick = {
                        state = TestState.Running
                        scope.launch { state = testServer(ServerConfig.toBaseUrl(address)) }
                    },
                    enabled = address.isNotBlank() && state != TestState.Running
                ) {
                    Text("Test connection")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    ServerConfig.save(address)
                    onSaved()
                },
                enabled = address.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun StatusLine(icon: ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}
