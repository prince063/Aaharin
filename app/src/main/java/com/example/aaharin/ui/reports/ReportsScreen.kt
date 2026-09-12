package com.example.aaharin.ui.reports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.aaharin.util.asAmount
import com.example.aaharin.util.asRupees

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(onBack: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Daily Sales", "Category Wise", "Settlements")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reports Dashboard") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // TabRow is deprecated in M3; PrimaryTabRow is the direct replacement.
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedTab) {
                0 -> DailySalesReport()
                1 -> CategorySalesReport()
                2 -> SettlementReport()
            }
        }
    }
}

@Composable
fun DailySalesReport() {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Text("Item Name", modifier = Modifier.weight(1.5f), fontWeight = FontWeight.Bold)
                Text("Qty", modifier = Modifier.weight(0.5f), fontWeight = FontWeight.Bold)
                Text("Total (₹)", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
            }
            HorizontalDivider()
        }
        items(SampleDailySales, key = { it.name }) { item ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(item.name, modifier = Modifier.weight(1.5f))
                Text(item.qty.toString(), modifier = Modifier.weight(0.5f))
                Text(item.total.asAmount(), modifier = Modifier.weight(1f))
            }
        }
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Grand Total", fontWeight = FontWeight.Bold)
                    Text(SampleDailySalesTotal.asRupees(), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun CategorySalesReport() {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SampleCategoryTotals.forEach { cat ->
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(cat.name, fontWeight = FontWeight.SemiBold)
                    Text(cat.total.asRupees())
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { (cat.total / CATEGORY_PROGRESS_CEILING).toFloat() }, // Mock progress
                    modifier = Modifier.fillMaxWidth().height(12.dp),
                    color = cat.color,
                    trackColor = cat.color.copy(alpha = 0.1f),
                    strokeCap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
fun SettlementReport() {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SampleSettlements.forEach { item ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(item.mode, fontWeight = FontWeight.Bold)
                    Text(item.amount.asRupees(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Immutable
data class ReportItem(val name: String, val category: String, val qty: Int, val total: Double)

@Immutable
data class CategoryTotal(val name: String, val total: Double, val color: Color)

@Immutable
data class SettlementItem(val mode: String, val amount: Double)

// ---------------------------------------------------------------------------
// Placeholder data. These were being re-allocated on every recomposition;
// hoisting them to top level makes each report body allocation-free.
// TODO: replace with a real reports endpoint.
// ---------------------------------------------------------------------------

private const val CATEGORY_PROGRESS_CEILING = 15_000.0

private val SampleDailySales = listOf(
    ReportItem("Paneer Tikka", "Starters", 12, 3360.0),
    ReportItem("Chicken Tikka", "Starters", 8, 2560.0),
    ReportItem("Dal Makhani", "Main Course", 15, 3300.0),
    ReportItem("Butter Naan", "Breads", 45, 1800.0)
)

private val SampleDailySalesTotal = SampleDailySales.sumOf { it.total }

private val SampleCategoryTotals = listOf(
    CategoryTotal("Starters", 5920.0, Color(0xFF2196F3)),
    CategoryTotal("Main Course", 8500.0, Color(0xFF4CAF50)),
    CategoryTotal("Breads", 2400.0, Color(0xFFFF9800)),
    CategoryTotal("Desserts", 1200.0, Color(0xFFE91E63))
)

private val SampleSettlements = listOf(
    SettlementItem("Cash", 4500.0),
    SettlementItem("UPI", 8200.0),
    SettlementItem("Card", 3100.0),
    SettlementItem("Credit", 1200.0)
)
