package com.example.aaharin.ui.tables

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aaharin.data.Table
import com.example.aaharin.data.TableStatus
import com.example.aaharin.data.ServiceCategory
import kotlinx.coroutines.delay

/** How often the table list reloads itself while the screen is open. */
private const val TABLES_AUTO_REFRESH_MS = 10_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableListingScreen(
    accessToken: String,
    restaurantId: Int,
    viewModel: TablesViewModel = viewModel(),
    isDarkMode: Boolean,
    /** Shown in the profile menu; blank when the login response carried no name. */
    userName: String = "",
    userMobile: String = "",
    restaurantName: String = "",
    onTableClick: (Table) -> Unit,
    onLogout: () -> Unit,
    onViewSettings: () -> Unit,
    onToggleTheme: () -> Unit,
) {
    // Keep track of which service category is expanded
    var expandedServiceId by remember { mutableIntStateOf(-1) }
    var showProfileMenu by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    // Loads on arrival, then keeps the list current on its own: a bill printed or an
    // order placed on another device shows up without a pull-to-refresh. Runs only while
    // the screen is in front, so it stops when the app is backgrounded or another screen
    // is open, and reloads straight away on return.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, accessToken, restaurantId) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.fetchTables(accessToken, restaurantId)
            while (true) {
                delay(TABLES_AUTO_REFRESH_MS)
                viewModel.fetchTables(accessToken, restaurantId, silent = true)
            }
        }
    }

    // Open the first category once the list arrives: every card used to start collapsed,
    // so the screen came up empty and needed a tap before a single table was visible.
    var hasAutoExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel.services) {
        if (!hasAutoExpanded && viewModel.services.isNotEmpty()) {
            expandedServiceId = viewModel.services.first().id
            hasAutoExpanded = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Tables",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        if (restaurantName.isNotBlank()) {
                            Text(
                                text = restaurantName,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    // No refresh button: the list is refreshed by pulling it down.
                    Box {
                        IconButton(
                            onClick = { showProfileMenu = true },
                            // The avatar is a drawn initial, not an Icon, so it carries no
                            // description of its own for TalkBack to read.
                            modifier = Modifier.semantics { contentDescription = "Profile" }
                        ) {
                            ProfileAvatar(name = userName, size = 34.dp)
                        }

                        // Everything that is not a table now lives behind this one icon -
                        // reports and settings had no way in at all before, and logout sat
                        // one stray tap away from the refresh button.
                        DropdownMenu(
                            expanded = showProfileMenu,
                            onDismissRequest = { showProfileMenu = false }
                        ) {
                            Row(
                                modifier = Modifier
                                    .widthIn(min = 220.dp)
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ProfileAvatar(name = userName, size = 42.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = userName.ifBlank { "Signed in" },
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (userMobile.isNotBlank()) {
                                        Text(
                                            text = userMobile,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            HorizontalDivider()

                            DropdownMenuItem(
                                text = { Text("Settings") },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                onClick = {
                                    showProfileMenu = false
                                    onViewSettings()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (isDarkMode) "Light mode" else "Dark mode") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    showProfileMenu = false
                                    onToggleTheme()
                                }
                            )

                            HorizontalDivider()

                            DropdownMenuItem(
                                text = { Text("Logout", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    showProfileMenu = false
                                    showLogoutConfirm = true
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            // Only while a reload runs over a list that is already on screen - the first
            // load has the spinner below and would otherwise show two indicators at once.
            isRefreshing = viewModel.isLoading && viewModel.services.isNotEmpty(),
            onRefresh = { viewModel.fetchTables(accessToken, restaurantId) },
            modifier = Modifier.padding(padding).fillMaxSize()
        ) {
        if (viewModel.isLoading && viewModel.services.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (viewModel.errorMessage != null && viewModel.services.isEmpty()) {
            // Scrollable so the pull-to-refresh gesture still works here - this is the
            // one screen state where the user most wants to retry.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(120.dp))
                Text(
                    text = "Connection error: ${viewModel.errorMessage}",
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp)
                )
                Text(
                    text = "Pull down to try again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // Bucket the tables once per data change instead of re-filtering the whole
            // list inside every service card on every recomposition (was O(services × tables)).
            val tablesByService = remember(viewModel.tables) {
                viewModel.tables.groupBy { it.serviceCategoryId }
            }

            Column(modifier = Modifier.fillMaxSize()) {
                StatusSummaryRow(tables = viewModel.tables, isDarkMode = isDarkMode)

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Keyed by name: services are built with distinctBy(name), so names are
                    // unique while ids can legitimately collide (both coerce to 0 when absent).
                    items(viewModel.services, key = { it.name }) { service ->
                        val isExpanded = expandedServiceId == service.id
                        val tablesInService = tablesByService[service.id].orEmpty()

                        ServiceExpandableCard(
                            service = service,
                            tables = tablesInService,
                            isExpanded = isExpanded,
                            onExpandClick = {
                                expandedServiceId = if (isExpanded) -1 else service.id
                            }
                        ) {
                            // Table Grid inside the expanded area
                            if (tablesInService.isEmpty()) {
                                Text(
                                    text = "No tables in this category",
                                    modifier = Modifier.padding(20.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                // Using a simple Column with Rows to simulate a grid inside LazyColumn
                                val columns = 3
                                val rows = (tablesInService.size + columns - 1) / columns

                                Column(
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    for (i in 0 until rows) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            for (j in 0 until columns) {
                                                val index = i * columns + j
                                                if (index < tablesInService.size) {
                                                    Box(modifier = Modifier.weight(1f)) {
                                                        TableItem(
                                                            table = tablesInService[index],
                                                            isDarkMode = isDarkMode
                                                        ) {
                                                            onTableClick(tablesInService[index])
                                                        }
                                                    }
                                                } else {
                                                    Spacer(modifier = Modifier.weight(1f))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Logout?") },
            text = { Text("You will need your mobile number and password to sign back in.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutConfirm = false
                        onLogout()
                    }
                ) {
                    Text("Logout", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

/** Round initials badge. Falls back to a single dash when no name came back from login. */
@Composable
private fun ProfileAvatar(name: String, size: androidx.compose.ui.unit.Dp) {
    val initials = remember(name) {
        name.trim()
            .split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .map { it.first().uppercaseChar() }
            .joinToString("")
            .ifBlank { "-" }
    }

    Box(
        modifier = Modifier
            .size(size)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value / 2.6f).sp
        )
    }
}

/** How many tables sit in each state right now - the one number a floor manager wants. */
@Composable
private fun StatusSummaryRow(tables: List<Table>, isDarkMode: Boolean) {
    if (tables.isEmpty()) return

    val counts = remember(tables) { tables.groupingBy { it.status }.eachCount() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TableStatus.entries.forEach { status ->
            val style = TableStatusStyle.of(status, isDarkMode)
            Surface(
                modifier = Modifier.weight(1f),
                color = style.background,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, style.border)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = (counts[status] ?: 0).toString(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = style.text
                    )
                    Text(
                        text = style.label,
                        fontSize = 11.sp,
                        color = style.text.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
fun ServiceExpandableCard(
    service: ServiceCategory,
    tables: List<Table>,
    isExpanded: Boolean,
    onExpandClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val rotation by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f, label = "Arrow")
    val accent = AccentRed

    // "4 tables · 1 running" beats a bare count: collapsed cards now say whether anything
    // inside them needs attention.
    val busyCount = remember(tables) { tables.count { it.status != TableStatus.VACANT } }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandClick() }
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(accent, shape = CircleShape)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = service.name.uppercase(),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            letterSpacing = 1.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = buildString {
                                append(if (tables.size == 1) "1 table" else "${tables.size} tables")
                                if (busyCount > 0) append(" · $busyCount running")
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        // Tinted from the accent rather than a fixed near-white, so the
                        // pill stays readable in dark mode.
                        color = accent.copy(alpha = 0.12f),
                        shape = CircleShape,
                        border = BorderStroke(1.dp, accent)
                    ) {
                        Text(
                            text = tables.size.toString(),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = accent
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        modifier = Modifier.rotate(rotation),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                content()
            }
        }
    }
}

@Composable
fun TableItem(table: Table, isDarkMode: Boolean, onClick: () -> Unit) {
    val style = TableStatusStyle.of(table.status, isDarkMode)

    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = style.background),
        border = BorderStroke(1.5.dp, style.border),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            // Status is readable at a glance from the corner dot even when the tile is
            // crowded by a long item summary.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(8.dp)
                    .background(style.border, CircleShape)
            )

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = table.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = style.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = style.label,
                    fontSize = 11.sp,
                    color = style.text.copy(alpha = 0.75f)
                )

                if (!table.itemsSummary.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = table.itemsSummary,
                        fontSize = 10.sp,
                        color = style.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                }
            }
        }
    }
}

/** Accent used by the service headers. */
private val AccentRed = Color(0xFFE57373)

/**
 * Status palette for a table tile. Replaces the ad-hoc `Quad` destructuring —
 * same colours and labels, just named instead of positional.
 *
 * Each status carries a dark-theme variant too: the light tints are near-white, so on a
 * dark background the tiles used to glare while the rest of the screen stayed dim.
 */
@Immutable
private data class TableStatusStyle(
    val background: Color,
    val border: Color,
    val text: Color,
    val label: String
) {
    companion object {
        private val Vacant = TableStatusStyle(Color(0xFFE8F5E9), Color(0xFF4CAF50), Color(0xFF2E7D32), "Vacant")
        private val Occupied = TableStatusStyle(Color(0xFFFFF3E0), Color(0xFFFF9800), Color(0xFFE65100), "Occupied")
        private val BillPrinted = TableStatusStyle(Color(0xFFE3F2FD), Color(0xFF2196F3), Color(0xFF0D47A1), "Unsettled")

        private val VacantDark = TableStatusStyle(Color(0xFF16321F), Color(0xFF4CAF50), Color(0xFFA5D6A7), "Vacant")
        private val OccupiedDark = TableStatusStyle(Color(0xFF3A2A12), Color(0xFFFF9800), Color(0xFFFFCC80), "Occupied")
        private val BillPrintedDark = TableStatusStyle(Color(0xFF122A3F), Color(0xFF2196F3), Color(0xFF90CAF9), "Unsettled")

        fun of(status: TableStatus, isDarkMode: Boolean): TableStatusStyle = when (status) {
            TableStatus.VACANT -> if (isDarkMode) VacantDark else Vacant
            TableStatus.OCCUPIED -> if (isDarkMode) OccupiedDark else Occupied
            TableStatus.BILL_PRINTED -> if (isDarkMode) BillPrintedDark else BillPrinted
        }
    }
}
