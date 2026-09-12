package com.example.aaharin.ui.cart

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aaharin.data.CartItem
import com.example.aaharin.data.gst
import com.example.aaharin.data.subtotal
import com.example.aaharin.data.unitPrice
import com.example.aaharin.util.asRupees

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartScreen(
    accessToken: String,
    restaurantId: Int,
    userId: Int,
    tableId: Int,
    tableName: String,
    cartItems: List<CartItem>,
    viewModel: CartViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onBack: () -> Unit,
    onRemoveItem: (CartItem) -> Unit,
    onUpdateQuantity: (CartItem, Int) -> Unit,
    onUpdateNotes: (CartItem, String) -> Unit,
    onKOTSaved: () -> Unit,
    onCheckout: () -> Unit,
) {
    var showNoteDialogItem by remember { mutableStateOf<CartItem?>(null) }
    
    // Show error if any
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel.errorMessage) {
        viewModel.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.errorMessage = null
        }
    }

    // Same arithmetic as before, now shared via data/Pricing.kt and only
    // recomputed when the cart contents actually change.
    val subtotal = remember(cartItems) { cartItems.subtotal() }
    val tax = remember(cartItems) { cartItems.gst() }
    val grandTotal = subtotal + tax

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Cart - $tableName") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 8.dp) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Subtotal", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Text("GST (5%)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(subtotal.asRupees(), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Text(tax.asRupees(), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                    }
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Grand Total", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(grandTotal.asRupees(), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { 
                                viewModel.saveKOT(accessToken, restaurantId, userId, tableId, cartItems, isPrint = false, onSuccess = onKOTSaved) 
                            },
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = MaterialTheme.shapes.medium,
                            enabled = !viewModel.isSaving,
                            // outlinedButtonColors keeps the transparent container that an
                            // OutlinedButton expects; the old hardcoded white broke dark mode.
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                        ) {
                            if (viewModel.isSaving) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            } else {
                                Text("Save KOT", fontSize = 13.sp)
                            }
                        }

                        OutlinedButton(
                            onClick = { 
                                viewModel.saveKOT(accessToken, restaurantId, userId, tableId, cartItems, isPrint = true, onSuccess = onKOTSaved) 
                            },
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = MaterialTheme.shapes.medium,
                            enabled = !viewModel.isSaving,
                            // outlinedButtonColors keeps the transparent container that an
                            // OutlinedButton expects; the old hardcoded white broke dark mode.
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                        ) {
                            if (viewModel.isSaving) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            } else {
                                Text("Print KOT", fontSize = 13.sp)
                            }
                        }
                        
                        Button(
                            onClick = onCheckout,
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text("Print Bill", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Stable identity = the same (id, portion) pair the add/remove logic matches on.
            items(cartItems, key = { "${it.menuItem.id}-${it.isHalf}" }) { item ->
                CartItemRow(
                    item = item,
                    onRemove = { onRemoveItem(item) },
                    onUpdateQty = { onUpdateQuantity(item, it) },
                    onAddNote = { showNoteDialogItem = item }
                )
            }
        }
    }

    if (showNoteDialogItem != null) {
        AddNoteDialog(
            initialNote = showNoteDialogItem!!.notes,
            onDismiss = { showNoteDialogItem = null },
            onSave = { 
                onUpdateNotes(showNoteDialogItem!!, it)
                showNoteDialogItem = null
            }
        )
    }
}

@Composable
fun CartItemRow(
    item: CartItem,
    onRemove: () -> Unit,
    onUpdateQty: (Int) -> Unit,
    onAddNote: () -> Unit
) {
    val price = item.unitPrice

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.menuItem.name + (if (item.isHalf) " (Half)" else ""),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(text = price.asRupees(), color = MaterialTheme.colorScheme.primary)
                    
                    Row(
                        modifier = Modifier.clickable { onAddNote() }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (item.notes.isEmpty()) "Add Note" else "Note: ${item.notes}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (item.quantity > 1) onUpdateQty(item.quantity - 1) else onRemove() }) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease")
                    }
                    Text(text = item.quantity.toString(), fontWeight = FontWeight.Bold)
                    IconButton(onClick = { onUpdateQty(item.quantity + 1) }) {
                        Icon(Icons.Default.Add, contentDescription = "Increase")
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color.Red)
                    }
                }
            }
        }
    }
}

@Composable
fun AddNoteDialog(
    initialNote: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var note by remember { mutableStateOf(initialNote) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Note") },
        text = {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Special instructions...") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(note) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/** Theme-aware divider (the old fixed light grey vanished on dark surfaces). */
@Composable
private fun HorizontalDivider(modifier: Modifier = Modifier) {
    androidx.compose.material3.HorizontalDivider(
        modifier = modifier,
        color = MaterialTheme.colorScheme.outlineVariant
    )
}
