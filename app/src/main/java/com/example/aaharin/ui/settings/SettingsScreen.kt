package com.example.aaharin.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.example.aaharin.network.ServerConfig
import com.example.aaharin.ui.components.ServerSetupDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    // Reflects what is actually saved. This field used to hold a hardcoded string that
    // nothing stored and nothing read.
    var serverAddress by remember { mutableStateOf(ServerConfig.address) }
    var showServerSetup by remember { mutableStateOf(false) }
    var kotPrinter by remember { mutableStateOf("EPSON TM-T82III") }
    var billPrinter by remember { mutableStateOf("Billing Printer 80mm") }
    var whatsappNumber by remember { mutableStateOf("919876543210") }
    var autoPrintBill by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("General Configuration", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            OutlinedTextField(
                value = if (serverAddress.isBlank()) "Not set" else serverAddress,
                onValueChange = {},
                label = { Text("Server Address") },
                supportingText = {
                    Text(
                        if (serverAddress.isBlank()) "Tap Change to point the app at your server"
                        else ServerConfig.toBaseUrl(serverAddress)
                    )
                },
                readOnly = true,
                trailingIcon = {
                    TextButton(onClick = { showServerSetup = true }) { Text("Change") }
                },
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider()
            Text("Printer Settings", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            OutlinedTextField(
                value = kotPrinter,
                onValueChange = { kotPrinter = it },
                label = { Text("KOT Printer Name") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = billPrinter,
                onValueChange = { billPrinter = it },
                label = { Text("Bill Printer Name") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Auto-print Bill PDF")
                Switch(checked = autoPrintBill, onCheckedChange = { autoPrintBill = it })
            }

            HorizontalDivider()
            Text("Communication Settings", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            OutlinedTextField(
                value = whatsappNumber,
                onValueChange = { whatsappNumber = it },
                label = { Text("Default WhatsApp Number") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("SAVE SETTINGS")
            }
        }
    }

    if (showServerSetup) {
        ServerSetupDialog(
            onDismiss = { showServerSetup = false },
            onSaved = {
                serverAddress = ServerConfig.address
                showServerSetup = false
            }
        )
    }
}
