package com.example.aaharin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

/**
 * Item photo, or its initial when the item has none.
 *
 * Most menus are only part-photographed, so the fallback has to look deliberate rather
 * than like a failed load - hence a tinted tile with the initial instead of a grey box.
 *
 * Shared by the menu list, the variant sheet and the cart so a given item looks the same
 * wherever it turns up.
 */
@Composable
fun ItemThumbnail(
    imageUrl: String,
    name: String,
    size: Dp = 56.dp
) {
    val shape = RoundedCornerShape(12.dp)
    val modifier = Modifier.size(size).clip(shape)

    if (imageUrl.isBlank()) {
        InitialTile(name = name, modifier = modifier)
        return
    }

    SubcomposeAsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(imageUrl)
            .crossfade(true)
            .build(),
        contentDescription = name,
        contentScale = ContentScale.Crop,
        modifier = modifier,
        // The initial stands in while the photo is on its way and stays put if it never
        // arrives (a 404 or an unreachable server), so a row never shows an empty tile.
        loading = { InitialTile(name = name, modifier = Modifier.fillMaxSize()) },
        error = { InitialTile(name = name, modifier = Modifier.fillMaxSize()) }
    )
}

@Composable
private fun InitialTile(name: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )
    }
}
