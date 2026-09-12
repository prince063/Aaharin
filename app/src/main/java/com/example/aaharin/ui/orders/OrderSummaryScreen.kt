package com.example.aaharin.ui.orders

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aaharin.data.CartItem
import com.example.aaharin.util.asRupees

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderSummaryScreen(
    accessToken: String,
    restaurantId: Int,
    tableId: Int,
    tableName: String,
    runningOrderId: String? = null,
    viewModel: OrderSummaryViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onBack: () -> Unit,
    onAddMore: () -> Unit,
    onPrintBill: (String, Double) -> Unit,
    onShiftTable: () -> Unit
) {
    val context = LocalContext.current
    var showCustomerDialog by remember { mutableStateOf(false) }

    // Single effect to trigger fetch
    LaunchedEffect(tableId, runningOrderId) {
        viewModel.fetchOrderDetails(accessToken, restaurantId, tableId, runningOrderId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Order Summary - $tableName") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 8.dp) {
                Column {
                    if (viewModel.totalAmount > 0) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total Amount", fontWeight = FontWeight.Bold)
                            Text(viewModel.totalAmount.asRupees(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ActionFab(icon = Icons.Default.Add, label = "Add Items", color = MaterialTheme.colorScheme.primary, onClick = onAddMore)
                        ActionFab(icon = Icons.Default.SwapHoriz, label = "Shift", color = MaterialTheme.colorScheme.secondary, onClick = onShiftTable)
                        ActionFab(
                            icon = Icons.Default.Print, 
                            label = "Print Bill", 
                            color = Color(0xFF4CAF50), 
                            onClick = { 
                                showCustomerDialog = true
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (viewModel.isLoading && viewModel.orderedItems.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (viewModel.errorMessage != null) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            ) {
                                Text(
                                    text = viewModel.errorMessage!!,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    item {
                        Text("Current Items", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    // Server rows all come back with id 0, so position is the only
                    // stable identity available here.
                    itemsIndexed(
                        viewModel.orderedItems,
                        key = { index, item -> "$index-${item.menuItem.name}" }
                    ) { _, item ->
                        OrderedItemRow(item = item)
                    }
                }
            }
        }
    }

    if (showCustomerDialog) {
        CustomerDetailsPrintDialog(
            onDismiss = { showCustomerDialog = false },
            onSubmit = { name, mobile, email, gst, address, discount, paymentType ->
                showCustomerDialog = false
                val idStr = viewModel.orderId?.toString() ?: ""
                viewModel.printBillWithDetails(
                    context = context,
                    accessToken = accessToken,
                    restaurantId = restaurantId,
                    orderId = idStr,
                    tableId = tableId,
                    custName = name,
                    custMobile = mobile,
                    custEmail = email,
                    custGst = gst,
                    custAddress = address,
                    discount = discount,
                    paymentType = paymentType,
                    onSuccess = { pdfUrl ->
                        onPrintBill(idStr, viewModel.totalAmount)
                    }
                )
            }
        )
    }
}

@Composable
fun CustomerDetailsPrintDialog(
    onDismiss: () -> Unit,
    onSubmit: (custName: String, custMobile: String, custEmail: String, custGst: String, custAddress: String, discount: Double, paymentType: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var gst by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var discountText by remember { mutableStateOf("0") }
    var paymentType by remember { mutableStateOf("Cash") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Customer Details & Print Bill", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Customer Name (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = mobile,
                    onValueChange = { mobile = it },
                    label = { Text("Mobile Number (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = gst,
                    onValueChange = { gst = it },
                    label = { Text("GST Number (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = discountText,
                    onValueChange = { discountText = it },
                    label = { Text("Discount Amount (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val discount = discountText.toDoubleOrNull() ?: 0.0
                    onSubmit(name, mobile, email, gst, address, discount, paymentType)
                }
            ) {
                Text("Submit & Download Bill")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun OrderedItemRow(item: CartItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${item.quantity}x ${item.menuItem.name}",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = item.status.name,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
fun ActionFab(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        modifier = Modifier.height(48.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label)
    }
}
