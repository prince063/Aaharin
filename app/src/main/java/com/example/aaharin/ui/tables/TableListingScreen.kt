package com.example.aaharin.ui.tables

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aaharin.data.Table
import com.example.aaharin.data.TableStatus
import com.example.aaharin.data.ServiceCategory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableListingScreen(
    accessToken: String,
    restaurantId: Int,
    viewModel: TablesViewModel = viewModel(),
    isDarkMode: Boolean,
    onTableClick: (Table) -> Unit,
    onLogout: () -> Unit,
    onViewReports: () -> Unit,
    onViewSettings: () -> Unit,
    onToggleTheme: () -> Unit,
) {
    // Keep track of which service category is expanded
    var expandedServiceId by remember { mutableIntStateOf(-1) }

    LaunchedEffect(Unit) {
        viewModel.fetchTables(accessToken, restaurantId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Aahar - Tables") },
                actions = {
                    IconButton(onClick = onToggleTheme) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle Theme",
                        )
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout")
                    }
                }
            )
        }
    ) { padding ->
        if (viewModel.isLoading && viewModel.services.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (viewModel.errorMessage != null && viewModel.services.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    text = "Connection error: ${viewModel.errorMessage}", 
                    color = MaterialTheme.colorScheme.error, 
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp)
                )
            }
        } else {
            // Bucket the tables once per data change instead of re-filtering the whole
            // list inside every service card on every recomposition (was O(services × tables)).
            val tablesByService = remember(viewModel.tables) {
                viewModel.tables.groupBy { it.serviceCategoryId }
            }

            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Keyed by name: services are built with distinctBy(name), so names are
                // unique while ids can legitimately collide (both coerce to 0 when absent).
                items(viewModel.services, key = { it.name }) { service ->
                    val isExpanded = expandedServiceId == service.id
                    val tablesInService = tablesByService[service.id].orEmpty()

                    ServiceExpandableCard(
                        service = service,
                        tableCount = tablesInService.size,
                        isExpanded = isExpanded,
                        onExpandClick = {
                            expandedServiceId = if (isExpanded) -1 else service.id
                        }
                    ) {
                        // Table Grid inside the expanded area
                        if (tablesInService.isEmpty()) {
                            Text(
                                text = "No tables in this category",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        } else {
                            // Using a simple Column with Rows to simulate a grid inside LazyColumn
                            val columns = 3
                            val rows = (tablesInService.size + columns - 1) / columns
                            
                            Column(modifier = Modifier.padding(16.dp)) {
                                for (i in 0 until rows) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        for (j in 0 until columns) {
                                            val index = i * columns + j
                                            if (index < tablesInService.size) {
                                                Box(modifier = Modifier.weight(1f)) {
                                                    TableItem(table = tablesInService[index]) {
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

@Composable
fun ServiceExpandableCard(
    service: ServiceCategory,
    tableCount: Int,
    isExpanded: Boolean,
    onExpandClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val rotation by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f, label = "Arrow")
    val accent = AccentRed

    Card(
        modifier = Modifier.fillMaxWidth(),
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(accent, shape = CircleShape)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = service.name.uppercase(),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        letterSpacing = 1.sp
                    )
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
                            text = tableCount.toString(),
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
fun TableItem(table: Table, onClick: () -> Unit) {
    val style = TableStatusStyle.of(table.status)

    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = style.background),
        border = BorderStroke(2.dp, style.border)
    ) {
        val textColor = style.text
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = table.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = textColor
                )
                Text(
                    text = style.label,
                    fontSize = 12.sp,
                    color = textColor.copy(alpha = 0.7f)
                )
                
                if (!table.itemsSummary.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = table.itemsSummary,
                        fontSize = 10.sp,
                        color = textColor,
                        maxLines = 2,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 4.dp)
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

        fun of(status: TableStatus): TableStatusStyle = when (status) {
            TableStatus.VACANT -> Vacant
            TableStatus.OCCUPIED -> Occupied
            TableStatus.BILL_PRINTED -> BillPrinted
        }
    }
}
