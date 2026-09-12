package com.example.aaharin.ui.kds

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aaharin.data.Kot
import com.example.aaharin.data.KotItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitchenDisplayScreen(
    kots: List<Kot>,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onKotDone: (Kot) -> Unit,
    onItemToggle: (Kot, KotItem) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kitchen Display (KDS)") },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout")
                    }
                }
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 300.dp),
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(kots.filter { !it.isDone }) { kot ->
                KotCard(
                    kot = kot,
                    onDone = { onKotDone(kot) }
                ) { item -> onItemToggle(kot, item) }
            }
        }
    }
}

@Composable
fun KotCard(
    kot: Kot,
    onDone: () -> Unit,
    onItemClick: (KotItem) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = kot.tableName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "KOT #${kot.kotNumber}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }
            Text(
                text = kot.timestamp,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                kot.items.forEach { item ->
                    KotItemRow(item = item, onClick = { onItemClick(item) })
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) {
                Text("ALL DONE", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun KotItemRow(item: KotItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val textColor = if (item.isPrepared) Color.Gray else MaterialTheme.colorScheme.onSurface
        val textDecoration = if (item.isPrepared) TextDecoration.LineThrough else TextDecoration.None
        
        Text(
            text = "×${item.quantity}",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = textColor,
            textDecoration = textDecoration
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = item.name + if (item.isHalf) " (Half)" else "",
            fontSize = 18.sp,
            color = textColor,
            textDecoration = textDecoration,
            modifier = Modifier.weight(1f)
        )
    }
}
