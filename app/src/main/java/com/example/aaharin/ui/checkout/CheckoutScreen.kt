package com.example.aaharin.ui.checkout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aaharin.data.PaymentMode
import com.example.aaharin.util.asRupees

import androidx.compose.ui.tooling.preview.Preview
import com.example.aaharin.ui.theme.AaharinTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutScreen(
    accessToken: String,
    orderId: String,
    tableId: Int = 0,
    tableName: String,
    totalAmount: Double,
    viewModel: CheckoutViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onBack: () -> Unit,
    onBillSettled: (PaymentMode) -> Unit
) {
    var selectedMode by remember { mutableStateOf(PaymentMode.CASH) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.errorMessage) {
        viewModel.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.errorMessage = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = "Checkout", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(
                            text = tableName,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 8.dp) {
                Column(
                    modifier = Modifier
                        // Edge-to-edge again: the button used to end up under the gesture bar.
                        .navigationBarsPadding()
                        .padding(16.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.settleOrder(accessToken, orderId, tableId, selectedMode, totalAmount) {
                                onBillSettled(selectedMode)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = MaterialTheme.shapes.medium,
                        enabled = !viewModel.isSettling
                    ) {
                        if (viewModel.isSettling) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            // The amount rides on the button, so the last thing read before
                            // taking money is what is being taken.
                            Text(
                                text = "Settle ${totalAmount.asRupees()} · ${selectedMode.label}",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(20.dp).fillMaxWidth()) {
                    Text(
                        text = "Amount due",
                        // Was colorScheme.primary on primaryContainer - two close tones
                        // stacked on each other.
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = totalAmount.asRupees(),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 34.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (orderId.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Order $orderId",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(text = "Payment mode", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(12.dp))

            // A column of radio buttons made every mode look the same and gave a 56dp-tall
            // strip as the tap target. These are cards you can hit without looking.
            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PaymentMode.entries.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { mode ->
                            PaymentModeCard(
                                mode = mode,
                                selected = selectedMode == mode,
                                onSelect = { selectedMode = mode },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // Keeps the last row aligned when the count is odd.
                        if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            if (selectedMode == PaymentMode.CREDIT) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Credit leaves the amount outstanding against the customer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** One tappable payment-mode tile. */
@Composable
private fun PaymentModeCard(
    mode: PaymentMode,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val border = if (selected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    }

    Surface(
        modifier = modifier
            .height(76.dp)
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = border
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = mode.icon,
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = mode.label,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Display name - the enum's own name is upper-case shouting ("CASH"). */
private val PaymentMode.label: String
    get() = when (this) {
        PaymentMode.CASH -> "Cash"
        PaymentMode.UPI -> "UPI"
        PaymentMode.CARD -> "Card"
        PaymentMode.CREDIT -> "Credit"
    }

private val PaymentMode.icon: ImageVector
    get() = when (this) {
        PaymentMode.CASH -> Icons.Default.Payments
        PaymentMode.UPI -> Icons.Default.QrCode2
        PaymentMode.CARD -> Icons.Default.CreditCard
        PaymentMode.CREDIT -> Icons.Default.Schedule
    }

@Preview(showBackground = true)
@Composable
fun CheckoutScreenPreview() {
    AaharinTheme {
        CheckoutScreen(
            accessToken = "fake_token",
            orderId = "123",
            tableName = "Table 5",
            totalAmount = 1250.0,
            onBack = {},
            onBillSettled = {}
        )
    }
}
