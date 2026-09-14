package com.example.aaharin

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.aaharin.data.*
import com.example.aaharin.ui.cart.CartScreen
import com.example.aaharin.ui.checkout.CheckoutScreen
import com.example.aaharin.ui.components.WhatsAppDialog
import com.example.aaharin.ui.login.LoginScreen
import com.example.aaharin.ui.menu.MenuScreen
import com.example.aaharin.ui.orders.OrderSummaryScreen
import com.example.aaharin.ui.reports.ReportsScreen
import com.example.aaharin.ui.settings.SettingsScreen
import com.example.aaharin.ui.tables.TableListingScreen
import com.example.aaharin.ui.theme.AaharinTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            // rememberSaveable so the chosen theme survives rotation / process death.
            var isDarkMode by rememberSaveable { mutableStateOf(false) }

            AaharinTheme(darkTheme = isDarkMode) {
                AaharinApp(
                    isDarkMode = isDarkMode,
                    onToggleTheme = { isDarkMode = !isDarkMode }
                ) { message ->
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

@Composable
fun AaharinApp(
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit,
    showToast: (String) -> Unit
) {
    val navController = rememberNavController()

    var cartItems by remember { mutableStateOf(listOf<CartItem>()) }
    var selectedTable by remember { mutableStateOf<Table?>(null) }
    var showWhatsAppDialog by remember { mutableStateOf(false) }

    // Auth State
    var currentUser by remember { mutableStateOf<LoginResponse?>(null) }
    
    // Running Order State (for Checkout)
    var runningOrderId by remember { mutableStateOf("") }
    var runningOrderTotal by remember { mutableDoubleStateOf(0.0) }

    NavHost(navController = navController, startDestination = "login") {
        composable("login") {
            LoginScreen { response ->
                currentUser = response
                navController.navigate("tables") {
                    popUpTo("login") { inclusive = true }
                }
            }
        }
        
        composable("tables") {
            TableListingScreen(
                accessToken = currentUser?.accessToken ?: "",
                restaurantId = currentUser?.restaurant?.id ?: 0,
                isDarkMode = isDarkMode,
                onTableClick = { table ->
                    selectedTable = table
                    when (table.status) {
                        TableStatus.VACANT -> navController.navigate("menu")
                        TableStatus.OCCUPIED -> navController.navigate("order_summary")
                        TableStatus.BILL_PRINTED -> navController.navigate("checkout")
                    }
                },
                onLogout = {
                    navController.navigate("login") {
                        popUpTo("tables") { inclusive = true }
                    }
                },
                onViewReports = {
                    navController.navigate("reports")
                },
                onViewSettings = {
                    navController.navigate("settings")
                },
                onToggleTheme = onToggleTheme
            )
        }
        
        composable("reports") {
            ReportsScreen(onBack = { navController.popBackStack() })
        }
        
        composable("settings") {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        
        composable("order_summary") {
            OrderSummaryScreen(
                accessToken = currentUser?.accessToken ?: "",
                restaurantId = currentUser?.restaurant?.id ?: 0,
                tableId = selectedTable?.id ?: 0,
                tableName = selectedTable?.name ?: "Unknown",
                runningOrderId = selectedTable?.orderId, // Passing the real Order ID
                userId = currentUser?.id ?: 0,
                orderTableId = selectedTable?.orderTableId ?: 0,
                kotNumber = selectedTable?.kotNumber ?: 1,
                itemQuantities = selectedTable?.itemQuantities ?: emptyMap(),
                // The billing call reads the tax rates off the request, so they come from
                // the logged-in restaurant rather than being assumed.
                cgst = currentUser?.restaurant?.cgst?.toDoubleOrNull() ?: 2.5,
                sgst = currentUser?.restaurant?.sgst?.toDoubleOrNull() ?: 2.5,
                igst = currentUser?.restaurant?.igst?.toDoubleOrNull() ?: 0.0,
                onBack = { navController.popBackStack() },
                onAddMore = { navController.navigate("menu") },
                onPrintBill = { _, _ ->
                    // Billing closes the order and frees the table, so go back to the list
                    // where it shows as green. The amount stays outstanding.
                    showToast("Bill generated")
                    navController.popBackStack("tables", inclusive = false)
                },
                onShiftTable = { showToast("Shift Table for ${selectedTable?.name}") }
            )
        }
        
        composable("menu") {
            // Recomputed only when the cart actually changes, not on every recomposition.
            val cartCount = remember(cartItems) { cartItems.sumOf { it.quantity } }
            MenuScreen(
                accessToken = currentUser?.accessToken ?: "",
                restaurantId = currentUser?.restaurant?.id ?: 0,
                tableId = selectedTable?.id ?: 0,
                tableName = selectedTable?.name ?: "Unknown",
                cartCount = cartCount,
                onBack = { navController.popBackStack() },
                onViewCart = { navController.navigate("cart") },
                onAddItem = { item, isHalf ->
                    val existing = cartItems.find { (it.menuItem.id == item.id) && (it.isHalf == isHalf) }
                    cartItems = if (existing != null) {
                        cartItems.map { 
                            if ((it.menuItem.id == item.id) && (it.isHalf == isHalf)) it.copy(quantity = it.quantity + 1) else it 
                        }
                    } else {
                        cartItems + CartItem(item, 1, isHalf = isHalf)
                    }
                    showToast("Added ${item.name}${if (isHalf) " (Half)" else ""} to cart")
                },
            )
        }
        
        composable("cart") {
            CartScreen(
                accessToken = currentUser?.accessToken ?: "",
                restaurantId = currentUser?.restaurant?.id ?: 0,
                userId = currentUser?.id ?: 0,
                tableId = selectedTable?.id ?: 0,
                tableName = selectedTable?.name ?: "Unknown",
                cartItems = cartItems,
                onBack = { navController.popBackStack() },
                onRemoveItem = { item ->
                    cartItems = cartItems.filter { (it.menuItem.id != item.menuItem.id) || (it.isHalf != item.isHalf) }
                },
                onUpdateQuantity = { item, newQty ->
                    cartItems = cartItems.map { 
                        if ((it.menuItem.id == item.menuItem.id) && (it.isHalf == item.isHalf)) it.copy(quantity = newQty) else it 
                    }
                },
                onUpdateNotes = { item, newNote ->
                    cartItems = cartItems.map { 
                        if ((it.menuItem.id == item.menuItem.id) && (it.isHalf == item.isHalf)) it.copy(notes = newNote) else it 
                    }
                },
                onKOTSaved = {
                    showToast("KOT Printed successfully")
                    // Mark occupied locally immediately
                    selectedTable?.let { DummyData.setTableOccupiedLocal(it.id) }
                    
                    navController.navigate("tables") {
                        popUpTo("tables") { inclusive = true }
                    }
                    cartItems = emptyList()
                },
                onCheckout = {
                    navController.navigate("checkout")
                },
            )
        }
        
        composable("checkout") {
            CheckoutScreen(
                accessToken = currentUser?.accessToken ?: "",
                orderId = runningOrderId,
                tableName = selectedTable?.name ?: "Unknown",
                totalAmount = runningOrderTotal,
                onBack = { navController.popBackStack() },
                onBillSettled = { mode ->
                    selectedTable?.let { table ->
                        DummyData.updateTableStatus(table.id, TableStatus.VACANT)
                    }
                    showToast("Bill settled. Payment: ${mode.name}")
                    showWhatsAppDialog = true
                },
            )
        }
    }

    if (showWhatsAppDialog) {
        WhatsAppDialog(
            onDismiss = { 
                showWhatsAppDialog = false
                cartItems = emptyList()
                navController.navigate("tables") {
                    popUpTo("tables") { inclusive = true }
                }
            },
            onSend = { mobile ->
                showToast("Bill sent to $mobile via WhatsApp")
                showWhatsAppDialog = false
                cartItems = emptyList()
                navController.navigate("tables") {
                    popUpTo("tables") { inclusive = true }
                }
            }
        )
    }
}
