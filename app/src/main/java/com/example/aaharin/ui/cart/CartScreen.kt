package com.example.aaharin.ui.cart

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aaharin.data.CartItem
import com.example.aaharin.data.TaxRates
import com.example.aaharin.data.gst
import com.example.aaharin.data.lineTotal
import com.example.aaharin.data.subtotal
import com.example.aaharin.data.unitPrice
import com.example.aaharin.ui.components.ItemThumbnail
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
    val totalQty = remember(cartItems) { cartItems.sumOf { it.quantity } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = "Cart", fontWeight = FontWeight.Bold, fontSize = 20.sp)
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
            // An empty cart still offered Save KOT / Print KOT, so a stray tap sent an
            // empty ticket to the kitchen. Nothing to send means nothing to press.
            val hasItems = cartItems.isNotEmpty()

            Surface(tonalElevation = 8.dp) {
                Column(
                    modifier = Modifier
                        // The app draws edge to edge; without this the buttons sit under
                        // the gesture bar.
                        .navigationBarsPadding()
                        .padding(16.dp)
                ) {
                    SummaryLine(
                        label = if (totalQty == 1) "Subtotal (1 item)" else "Subtotal ($totalQty items)",
                        value = subtotal.asRupees()
                    )
                    SummaryLine(label = "GST (${TaxRates.totalLabel})", value = tax.asRupees())

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Grand Total", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(
                            text = grandTotal.asRupees(),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Three 13sp buttons used to share one row; the two kitchen
                    // actions that are left keep it.
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = {
                                viewModel.saveKOT(accessToken, restaurantId, userId, tableId, cartItems, isPrint = false, onSuccess = onKOTSaved)
                            },
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = MaterialTheme.shapes.medium,
                            enabled = hasItems && !viewModel.isSaving,
                            // outlinedButtonColors keeps the transparent container that an
                            // OutlinedButton expects; the old hardcoded white broke dark mode.
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                        ) {
                            if (viewModel.isSaving) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Save KOT")
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.saveKOT(accessToken, restaurantId, userId, tableId, cartItems, isPrint = true, onSuccess = onKOTSaved)
                            },
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = MaterialTheme.shapes.medium,
                            enabled = hasItems && !viewModel.isSaving,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                        ) {
                            if (viewModel.isSaving) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Print KOT")
                            }
                        }
                    }
                    // "Proceed to Payment" sat here. Taking payment from the cart is
                    // parked for now; the settle screen is still reached from a green
                    // (billed) table on the table list, so nothing is orphaned.
                }
            }
        }
    ) { padding ->
        if (cartItems.isEmpty()) {
            // Previously a blank screen with a ₹0 total underneath it.
            Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingCart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Cart is empty", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Add items from the menu to start this order.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(onClick = onBack) { Text("Back to menu") }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
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

/** One "label ....... value" line of the bill summary. */
@Composable
private fun SummaryLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ItemThumbnail(imageUrl = item.menuItem.imageUrl, name = item.menuItem.name, size = 52.dp)
                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        VegMark(isVeg = item.menuItem.isVeg)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            // Variations all carry the parent's name, so without the label
                            // two different options read as the same line twice.
                            text = buildString {
                                append(item.menuItem.name)
                                if (item.menuItem.variantLabel.isNotBlank()) {
                                    append(" (").append(item.menuItem.variantLabel).append(")")
                                }
                                if (item.isHalf) append(" (Half)")
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    // The row showed the unit price only, so a line of 3 read as if it
                    // cost one item's worth.
                    Text(
                        text = "${item.quantity} × ${price.asRupees()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = item.lineTotal.asRupees(),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove",
                        // Was a fixed Color.Red, which clashed with the theme's own error red.
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Note chip: says what the note is when there is one, invites one when not.
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onAddNote() }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (item.notes.isEmpty()) "Add note" else item.notes,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Stepper, grouped so the two arrows read as one control.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape
                    )
                ) {
                    IconButton(
                        onClick = { if (item.quantity > 1) onUpdateQty(item.quantity - 1) else onRemove() },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            Icons.Default.Remove,
                            contentDescription = "Decrease",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = item.quantity.toString(),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.widthIn(min = 20.dp),
                        textAlign = TextAlign.Center
                    )
                    IconButton(
                        onClick = { onUpdateQty(item.quantity + 1) },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Increase",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/** The square veg / non-veg mark, matching the one on the menu list. */
@Composable
private fun VegMark(isVeg: Boolean) {
    val color = if (isVeg) Color(0xFF4CAF50) else Color(0xFFF44336)
    Box(
        modifier = Modifier
            .size(12.dp)
            .border(1.5.dp, color, RoundedCornerShape(3.dp)),
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier.size(6.dp).background(color, CircleShape))
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
