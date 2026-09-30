package com.example.aaharin.ui.kiosk

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aaharin.data.CartItem
import com.example.aaharin.data.Category
import com.example.aaharin.data.MenuItem
import com.example.aaharin.data.lineTotal
import com.example.aaharin.data.menuTotal
import com.example.aaharin.ui.menu.VegNonVegIndicator
import com.example.aaharin.util.asRupees

@Composable
fun KioskScreen(
    categories: List<Category>,
    items: List<MenuItem>,
    onLogout: () -> Unit
) {
    var kioskState by remember { mutableStateOf(KioskState.WELCOME) }
    var cartItems by remember { mutableStateOf(listOf<CartItem>()) }
    var selectedCategoryId by remember { mutableIntStateOf(categories.firstOrNull()?.id ?: -1) }
    var tokenNumber by remember { mutableStateOf("") }

    when (kioskState) {
        KioskState.WELCOME -> {
            KioskWelcomeScreen(
                onStart = { kioskState = KioskState.MENU },
                onAdminExit = onLogout
            )
        }
        KioskState.MENU -> {
            KioskMenuScreen(
                categories = categories,
                items = items,
                selectedCategoryId = selectedCategoryId,
                cartItems = cartItems,
                onCategorySelect = { selectedCategoryId = it },
                onAddItem = { item ->
                    val existing = cartItems.find { it.menuItem.id == item.id && !it.isHalf }
                    cartItems = if (existing != null) {
                        cartItems.map { if (it.menuItem.id == item.id && !it.isHalf) it.copy(quantity = it.quantity + 1) else it }
                    } else {
                        cartItems + CartItem(item, 1)
                    }
                },
                onRemoveItem = { item ->
                    cartItems = cartItems.filter { it != item }
                },
                onCheckout = { kioskState = KioskState.CHECKOUT }
            )
        }
        KioskState.CHECKOUT -> {
            KioskCheckoutScreen(
                cartItems = cartItems,
                onBack = { kioskState = KioskState.MENU },
                onConfirm = {
                    tokenNumber = "#${(1..999).random().toString().padStart(3, '0')}"
                    kioskState = KioskState.SUCCESS
                }
            )
        }
        KioskState.SUCCESS -> {
            KioskSuccessScreen(
                tokenNumber = tokenNumber,
                onDone = {
                    cartItems = emptyList()
                    kioskState = KioskState.WELCOME
                }
            )
        }
    }
}

enum class KioskState {
    WELCOME, MENU, CHECKOUT, SUCCESS
}

@Composable
fun KioskWelcomeScreen(onStart: () -> Unit, onAdminExit: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onStart)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Welcome to Aahar",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Touch Screen to Start",
                fontSize = 24.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
        }
        
        // Hidden admin exit button (matching Aahar guide: tap logo 5 times)
        // Here we just put it in a corner for now
        TextButton(
            onClick = onAdminExit,
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
        ) {
            Text("Admin Exit", color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f))
        }
    }
}

@Composable
fun KioskMenuScreen(
    categories: List<Category>,
    items: List<MenuItem>,
    selectedCategoryId: Int,
    cartItems: List<CartItem>,
    onCategorySelect: (Int) -> Unit,
    onAddItem: (MenuItem) -> Unit,
    onRemoveItem: (CartItem) -> Unit,
    onCheckout: () -> Unit
) {
    Row(modifier = Modifier.fillMaxSize()) {
        // Categories Sidebar
        Column(
            modifier = Modifier
                .width(160.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(vertical = 16.dp)
        ) {
            categories.forEach { category ->
                KioskCategoryItem(
                    name = category.name,
                    isSelected = selectedCategoryId == category.id,
                    onClick = { onCategorySelect(category.id) }
                )
            }
        }

        // Items Grid and Cart
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            val filteredItems = remember(items, selectedCategoryId) {
                items.filter { it.categoryId == selectedCategoryId }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 200.dp),
                modifier = Modifier.weight(1f).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(filteredItems, key = { it.name }) { item ->
                    KioskItemCard(item = item, onAdd = { onAddItem(item) })
                }
            }

            // Kiosk Cart Bar
            if (cartItems.isNotEmpty()) {
                Surface(
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "${cartItems.sumOf { it.quantity }} Items in Cart",
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                            val total = cartItems.menuTotal()
                            Text(
                                text = "Total: ${total.asRupees()}",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Button(
                            onClick = onCheckout,
                            modifier = Modifier.height(64.dp).width(200.dp),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Icon(Icons.Default.ShoppingCart, contentDescription = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("CHECKOUT", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun KioskCategoryItem(name: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clickable(onClick = onClick)
            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun KioskItemCard(item: MenuItem, onAdd: () -> Unit) {
    Card(
        onClick = onAdd,
        modifier = Modifier.fillMaxWidth().aspectRatio(0.8f),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
            VegNonVegIndicator(isVeg = item.isVeg)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = item.name,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                maxLines = 2,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = item.price.asRupees(),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KioskCheckoutScreen(
    cartItems: List<CartItem>,
    onBack: () -> Unit,
    onConfirm: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Complete Your Order") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Order Summary", fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(32.dp))
            
            Card(modifier = Modifier.widthIn(max = 600.dp).weight(1f)) {
                Column(modifier = Modifier.padding(24.dp).fillMaxWidth()) {
                    cartItems.forEach { item ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${item.quantity}x ${item.menuItem.name}", fontSize = 18.sp)
                            Text(item.lineTotal.asRupees(), fontSize = 18.sp)
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                    val total = cartItems.menuTotal()
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total Amount", fontWeight = FontWeight.Bold, fontSize = 24.sp)
                        Text(total.asRupees(), fontWeight = FontWeight.Bold, fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            
            Button(
                onClick = onConfirm,
                modifier = Modifier.width(400.dp).height(80.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Text("PLACE ORDER", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun KioskSuccessScreen(tokenNumber: String, onDone: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Thank You!",
            fontSize = 56.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Your order has been placed.",
            fontSize = 24.sp,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
        )
        Spacer(modifier = Modifier.height(64.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.onPrimary)
        ) {
            Column(modifier = Modifier.padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("YOUR TOKEN", fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                Text(
                    text = tokenNumber,
                    fontSize = 96.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(modifier = Modifier.height(64.dp))
        Button(
            onClick = onDone,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onPrimary, contentColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.width(300.dp).height(64.dp)
        ) {
            Text("DONE", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}
