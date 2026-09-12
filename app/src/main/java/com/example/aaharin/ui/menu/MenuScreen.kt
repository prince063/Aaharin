package com.example.aaharin.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aaharin.data.MenuItem
import com.example.aaharin.util.asRupees

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScreen(
    accessToken: String,
    restaurantId: Int,
    tableId: Int,
    tableName: String,
    viewModel: MenuViewModel = viewModel(),
    cartCount: Int,
    onBack: () -> Unit,
    onViewCart: () -> Unit,
    onAddItem: (MenuItem, Boolean) -> Unit,
) {
    LaunchedEffect(Unit) {
        viewModel.fetchMenu(accessToken, restaurantId, tableId)
    }

    var selectedCategoryId by remember { mutableIntStateOf(0) } 
    var searchQuery by remember { mutableStateOf("") }
    
    // Variant Selection State
    var itemToConfigure by remember { mutableStateOf<MenuItem?>(null) }
    val sheetState = rememberModalBottomSheetState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Menu - $tableName") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onViewCart) {
                        BadgedBox(
                            badge = { if (cartCount > 0) Badge { Text(cartCount.toString()) } }
                        ) {
                            Icon(Icons.Default.ShoppingCart, contentDescription = "Cart")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search items...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )

            // Category Tabs
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    FilterChip(
                        selected = selectedCategoryId == 0,
                        onClick = { selectedCategoryId = 0 },
                        label = { Text("All Items") }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedCategoryId == -1,
                        onClick = { selectedCategoryId = -1 },
                        label = { 
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Favourites") 
                            }
                        }
                    )
                }
                items(viewModel.categories) { category ->
                    FilterChip(
                        selected = selectedCategoryId == category.id,
                        onClick = { selectedCategoryId = category.id },
                        label = { Text(category.name) }
                    )
                }
            }

            // Items List
            if (viewModel.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (viewModel.errorMessage != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = viewModel.errorMessage!!, color = MaterialTheme.colorScheme.error)
                }
            } else {
                // Two full list passes were re-running on every recomposition (including
                // each cart badge update). Now they only re-run when an input changes.
                val filteredItems = remember(viewModel.items, selectedCategoryId, searchQuery) {
                    if (selectedCategoryId == 0) {
                        viewModel.items
                    } else {
                        viewModel.items.filter {
                            if (selectedCategoryId == -1) it.isFavourite else it.categoryId == selectedCategoryId
                        }
                    }.filter {
                        searchQuery.isEmpty() || it.name.contains(searchQuery, ignoreCase = true)
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Names are unique after the view model's groupBy(name), ids are not.
                    items(filteredItems, key = { it.name }) { item ->
                        MenuItemCard(
                            item = item, 
                            onAddClick = {
                                if (item.variants.isNotEmpty()) {
                                    itemToConfigure = item
                                } else {
                                    onAddItem(item, false)
                                }
                            }
                        )
                    }
                }
            }
        }
        
        // Variant Selection Bottom Sheet
        if (itemToConfigure != null) {
            ModalBottomSheet(
                onDismissRequest = { itemToConfigure = null },
                sheetState = sheetState,
                // Was hardcoded white, which made the sheet unreadable in dark mode.
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                VariantSelectionContent(
                    item = itemToConfigure!!,
                    onDismiss = { itemToConfigure = null },
                    onAddVariant = { variant, qty ->
                        repeat(qty) { onAddItem(variant, false) }
                        itemToConfigure = null
                    }
                )
            }
        }
    }
}

@Composable
fun MenuItemCard(
    item: MenuItem, 
    onAddClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VegNonVegIndicator(isVeg = item.isVeg)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                val priceText = if (item.variants.isNotEmpty()) {
                    "Starts from ${item.price.asRupees()}"
                } else {
                    item.price.asRupees()
                }
                Text(
                    text = priceText,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            Button(
                onClick = onAddClick,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(if (item.variants.isNotEmpty()) "OPTIONS" else "ADD")
            }
        }
    }
}

@Composable
fun VariantSelectionContent(
    item: MenuItem,
    onDismiss: () -> Unit,
    onAddVariant: (MenuItem, Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = item.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
        }
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), alpha = 0.1f)
        
        item.variants.forEach { variant ->
            // Keyed on the variant: an unkeyed remember() binds to slot position, so
            // counts hopped between rows whenever the variant list changed.
            var quantity by remember(item.name, variant.id, variant.price) { mutableIntStateOf(0) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    VegNonVegIndicator(isVeg = item.isVeg)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = item.name, fontWeight = FontWeight.Medium)
                }
                
                Text(
                    text = variant.price.asRupees(),
                    modifier = Modifier.padding(horizontal = 16.dp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                if (quantity == 0) {
                    Button(
                        onClick = { quantity = 1; onAddVariant(variant, 1) },
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("ADD")
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                    ) {
                        IconButton(onClick = { if (quantity > 0) quantity-- }) {
                            Icon(Icons.Default.Remove, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Text(text = quantity.toString(), fontWeight = FontWeight.Bold)
                        IconButton(onClick = { quantity++; onAddVariant(variant, 1) }) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp), alpha = 0.05f)
        }
    }
}

@Composable
fun VegNonVegIndicator(isVeg: Boolean) {
    val color = if (isVeg) Color(0xFF4CAF50) else Color(0xFFF44336)
    Box(
        modifier = Modifier
            .size(14.dp)
            .background(Color.White, shape = MaterialTheme.shapes.extraSmall)
            .padding(1.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(color, shape = MaterialTheme.shapes.extraSmall)
        )
    }
}

/**
 * Thin divider tuned for this screen. Derives from the theme outline instead of
 * a fixed black so it stays visible on dark surfaces.
 */
@Composable
private fun HorizontalDivider(modifier: Modifier = Modifier, alpha: Float = 0.2f) {
    androidx.compose.material3.HorizontalDivider(
        modifier = modifier,
        color = MaterialTheme.colorScheme.outline.copy(alpha = alpha)
    )
}
