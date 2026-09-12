package com.example.aaharin.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WhatsAppDialog(
    onDismiss: () -> Unit,
    onSend: (String) -> Unit
) {
    var mobileNumber by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send Bill via WhatsApp") },
        text = {
            Column {
                Text("Enter customer's 10-digit mobile number", fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = mobileNumber,
                    // Reject non-digits up front: the field is phone-typed, but a
                    // physical keyboard or paste could still land letters in it.
                    onValueChange = { input ->
                        if (input.length <= 10 && input.all(Char::isDigit)) mobileNumber = input
                    },
                    label = { Text("Mobile Number") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSend(mobileNumber) },
                enabled = mobileNumber.length == 10
            ) {
                Text("SEND")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("SKIP")
            }
        }
    )
}
