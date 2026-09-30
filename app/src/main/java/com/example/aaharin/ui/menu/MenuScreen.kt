package com.example.aaharin.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import com.example.aaharin.data.MenuItem
import com.example.aaharin.ui.components.ItemThumbnail
import com.example.aaharin.util.asRupees

/** Long enough to swallow a burst of typing, short enough not to feel laggy on a LAN. */
private const val SEARCH_DEBOUNCE_MS = 350L

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

    // The search runs on the server, which matches the item code as well as the name -
    // the code never reaches the device, so it cannot be matched here. Debounced so a
    // fast typist fires one request instead of one per keystroke, and the query is only
    // sent when it differs from the one the current list was fetched with (which also
    // stops this re-running the initial load above).
    LaunchedEffect(searchQuery) {
        val query = searchQuery.trim()
        if (query == viewModel.appliedSearch) return@LaunchedEffect
        delay(SEARCH_DEBOUNCE_MS)
        // A code match usually sits in some other category, so searching looks at the
        // whole menu rather than silently returning nothing under the active chip.
        if (query.isNotEmpty()) selectedCategoryId = 0
        viewModel.fetchMenu(accessToken, restaurantId, tableId, query)
    }

    // Variant Selection State
    var itemToConfigure by remember { mutableStateOf<MenuItem?>(null) }
    var itemForPortion by remember { mutableStateOf<MenuItem?>(null) }
    val sheetState = rememberModalBottomSheetState()

    // Item rows only carry a category id; the chip row is where the names live.
    val categoryNames = remember(viewModel.categories) {
        viewModel.categories.associate { it.id to it.name }
    }
    val categoryCounts = remember(viewModel.items) {
        viewModel.items.groupingBy { it.categoryId }.eachCount()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = "Menu", fontWeight = FontWeight.Bold, fontSize = 20.sp)
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
        },
        bottomBar = {
            // The cart was reachable only through a small badge in the corner. Once
            // something is in it, the way out of this screen is a full-width bar.
            if (cartCount > 0) {
                Surface(tonalElevation = 3.dp, shadowElevation = 12.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            // The app draws edge to edge, so without this the second line
                            // of text sits underneath the gesture bar.
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = if (cartCount == 1) "1 item in cart" else "$cartCount items in cart",
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Review before sending the KOT",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(onClick = onViewCart, shape = MaterialTheme.shapes.medium) {
                            Icon(
                                Icons.Default.ShoppingCart,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("View Cart")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search by name or item code...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )

            // Thin bar instead of the full-screen spinner: the previous results stay
            // readable while the server answers.
            if (viewModel.isSearching) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
            }

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
                        label = { Text("All Items") },
                        trailingIcon = { ChipCount(viewModel.items.size) },
                        shape = CircleShape
                    )
                }
                item {
                    FilterChip(
                        selected = selectedCategoryId == -1,
                        onClick = { selectedCategoryId = -1 },
                        label = { Text("Favourites") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        shape = CircleShape
                    )
                }
                items(viewModel.categories) { category ->
                    FilterChip(
                        selected = selectedCategoryId == category.id,
                        onClick = { selectedCategoryId = category.id },
                        label = { Text(category.name) },
                        trailingIcon = { ChipCount(categoryCounts[category.id] ?: 0) },
                        shape = CircleShape
                    )
                }
            }

            // Items List
            if (viewModel.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (viewModel.errorMessage != null) {
                // This was a line of red text in the middle of an otherwise empty screen,
                // with no way to retry short of leaving and coming back.
                MenuMessage(
                    icon = Icons.Default.WifiOff,
                    title = viewModel.errorMessage!!,
                    subtitle = "Check that the server is reachable, then try again.",
                    tint = MaterialTheme.colorScheme.error,
                    action = {
                        Button(onClick = {
                            viewModel.fetchMenu(accessToken, restaurantId, tableId, searchQuery.trim())
                        }) {
                            Text("Retry")
                        }
                    }
                )
            } else {
                // Two full list passes were re-running on every recomposition (including
                // each cart badge update). Now they only re-run when an input changes.
                val filteredItems = remember(
                    viewModel.items,
                    selectedCategoryId,
                    searchQuery,
                    viewModel.appliedSearch
                ) {
                    val byCategory = if (selectedCategoryId == 0) {
                        viewModel.items
                    } else {
                        viewModel.items.filter {
                            if (selectedCategoryId == -1) it.isFavourite else it.categoryId == selectedCategoryId
                        }
                    }
                    val query = searchQuery.trim()
                    // Once the server has answered for this exact query the list is already
                    // filtered on name OR item code; matching on the name again here would
                    // drop every row that matched by code. Until then, filter by name so
                    // typing still narrows the list instantly.
                    if (query.isEmpty() || query == viewModel.appliedSearch) {
                        byCategory
                    } else {
                        byCategory.filter { it.name.contains(query, ignoreCase = true) }
                    }
                }

                if (filteredItems.isEmpty()) {
                    // An empty category or a search with no local match used to leave the
                    // screen blank, which reads as a broken load.
                    MenuMessage(
                        icon = Icons.Default.SearchOff,
                        title = if (searchQuery.isBlank()) {
                            "Nothing in this category"
                        } else {
                            "No match for \"${searchQuery.trim()}\""
                        },
                        subtitle = if (searchQuery.isBlank()) {
                            "Pick another category above."
                        } else {
                            "Search matches the item name and its code."
                        },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 4.dp,
                            bottom = 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Names are unique after the view model's groupBy(name), ids are not.
                        items(filteredItems, key = { it.name }) { item ->
                            MenuItemCard(
                                item = item,
                                categoryName = categoryNames[item.categoryId],
                                onAddClick = {
                                    if (item.variants.isNotEmpty()) {
                                        itemToConfigure = item
                                    } else if (item.hasHalfOption) {
                                        itemForPortion = item
                                    } else {
                                        onAddItem(item, false)
                                    }
                                }
                            )
                        }
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

        // Full / Half portion choice
        itemForPortion?.let { item ->
            ModalBottomSheet(
                onDismissRequest = { itemForPortion = null },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                PortionSelectionContent(
                    item = item,
                    onDismiss = { itemForPortion = null },
                    onAddPortion = { isHalf ->
                        onAddItem(item, isHalf)
                        itemForPortion = null
                    }
                )
            }
        }
    }
}

/** Small count that rides along inside a category chip. */
@Composable
private fun ChipCount(count: Int) {
    Text(
        text = count.toString(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Centred icon + message, shared by the error and empty-list states. */
@Composable
private fun MenuMessage(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    action: @Composable (() -> Unit)? = null
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint.copy(alpha = 0.7f),
                modifier = Modifier.size(44.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = tint
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (action != null) {
                Spacer(modifier = Modifier.height(16.dp))
                action()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuItemCard(
    item: MenuItem,
    categoryName: String? = null,
    onAddClick: () -> Unit
) {
    Card(
        // The whole row is the tap target now, not just the button - one less thing to
        // aim at during a rush.
        onClick = onAddClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ItemThumbnail(imageUrl = item.imageUrl, name = item.name)
            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VegNonVegIndicator(isVeg = item.isVeg)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // A search spans every category, so a result has to say which one it
                // came out of.
                if (!categoryName.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = categoryName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.price.asRupees(),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    if (item.hasHalfOption && item.halfPrice != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = CircleShape
                        ) {
                            Text(
                                text = "Half ${item.halfPrice.asRupees()}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                    if (item.variants.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = CircleShape
                        ) {
                            Text(
                                text = "${item.variants.size} options",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Button(
                onClick = onAddClick,
                shape = MaterialTheme.shapes.medium,
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
            ) {
                if (item.variants.isEmpty()) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
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
            ItemThumbnail(imageUrl = item.imageUrl, name = item.name, size = 56.dp)
            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Choose an option",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), alpha = 0.1f)

        item.variants.forEachIndexed { index, variant ->
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
                    // A variation without its own photo shows the parent's, which is what
                    // the server does for the web POS too.
                    ItemThumbnail(
                        imageUrl = variant.imageUrl.ifBlank { item.imageUrl },
                        name = item.name,
                        size = 44.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    VegNonVegIndicator(isVeg = item.isVeg)
                    Spacer(modifier = Modifier.width(10.dp))
                    // The server's own label when it has one ("Half", "500ml"); menus
                    // whose children are only priced differently fall back to a number.
                    // They are ordered cheapest first.
                    Text(
                        text = variant.variantLabel.ifBlank { "Option ${index + 1}" },
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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

/** Full or half portion of an item the server allows halves on. */
@Composable
fun PortionSelectionContent(
    item: MenuItem,
    onDismiss: () -> Unit,
    onAddPortion: (isHalf: Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ItemThumbnail(imageUrl = item.imageUrl, name = item.name, size = 56.dp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Choose a portion",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), alpha = 0.1f)

        listOf(false to item.price, true to (item.halfPrice ?: item.price)).forEach { (isHalf, price) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                VegNonVegIndicator(isVeg = item.isVeg)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (isHalf) "Half" else "Full",
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = price.asRupees(),
                    modifier = Modifier.padding(horizontal = 16.dp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Button(
                    onClick = { onAddPortion(isHalf) },
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("ADD")
                }
            }
        }
    }
}

/**
 * The standard square veg / non-veg mark: a coloured outline around a matching dot.
 *
 * The inner square used to sit on a hardcoded white background, which showed up as a
 * white chip on every row in dark mode.
 */
@Composable
fun VegNonVegIndicator(isVeg: Boolean) {
    val color = if (isVeg) Color(0xFF4CAF50) else Color(0xFFF44336)
    Box(
        modifier = Modifier
            .size(14.dp)
            .border(1.5.dp, color, RoundedCornerShape(3.dp)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(color, CircleShape)
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
