package com.leo.wardrobe.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

/** 4:5 照片卡（编辑风：大圆角、裁切填充、Coil 淡入）。file 可为 java.io.File 或 android.net.Uri */
@Composable
fun PhotoCard(
    file: Any?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    corner: Dp = 20.dp,
    contentScale: ContentScale = ContentScale.Crop,
) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(file)
            .crossfade(220)
            .build(),
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier.clip(RoundedCornerShape(corner)),
    )
}
