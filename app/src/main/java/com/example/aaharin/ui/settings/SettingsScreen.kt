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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    var apiUrl by remember { mutableStateOf("https://ahaar.vaiditech.in/apis/backend/web/") }
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
                value = apiUrl,
                onValueChange = { apiUrl = it },
                label = { Text("API Base URL") },
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
}
