package com.leo.wardrobe.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * 照片卡（编辑风：大圆角、Coil 淡入）。file 可为 java.io.File 或 android.net.Uri。
 *
 * it-011 C5「统一浅底容器」：mat=true 时改为「衬纸」模式——淡底色圆角容器 +
 * ContentScale.Fit 完整呈现衣物轮廓，无论原图背景（纸袋/衣架/卧室）如何观感统一；
 * 后续接抠图能力时替换衬底为透明即可。成品图（生图产出）仍走默认全幅 Crop。
 */
@Composable
fun PhotoCard(
    file: Any?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    corner: Dp = 20.dp,
    contentScale: ContentScale = ContentScale.Crop,
    mat: Boolean = false,
    matColor: Color? = null,
) {
    if (!mat) {
        // it-058 C1：灰阶占位兜住解码期（crossfade 只管占位→成图渐入，无占位即白块闪现）
        // it-071：error 同占位——文件缺失/解码失败落灰底（imageFileOf 已不再主线程 stat）
        val fallback = ColorPainter(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(file)
                .crossfade(220)
                .build(),
            contentDescription = contentDescription,
            contentScale = contentScale,
            placeholder = fallback,
            error = fallback,
            modifier = modifier.clip(RoundedCornerShape(corner)),
        )
        return
    }
    Surface(
        shape = RoundedCornerShape(corner),
        color = matColor ?: MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = modifier,
    ) {
        Box(contentAlignment = Alignment.Center) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(file)
                    .crossfade(220)
                    // it-061 修1：衬纸承图先去透明边——透明素材内容充满格，
                    // 非透明图（成品图）在 Transformation 内原样返回零影响
                    .transformations(TrimAlphaTransformation())
                    .build(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                // it-071：失败画透明，让衬纸底色兜出（灰底会盖掉 it-011 的衬纸语义）
                placeholder = ColorPainter(Color.Transparent),
                error = ColorPainter(Color.Transparent),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(2.dp), // it-012：扁槽（鞋）内照片占比提升
            )
        }
    }
}
