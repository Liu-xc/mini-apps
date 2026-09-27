package com.leo.darkroom.ui.pick

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leo.darkroom.DarkroomViewModel
import com.leo.darkroom.DarkroomViewModel.UiState
import com.leo.darkroom.card.SampleArt
import com.leo.darkroom.ui.pageInsets
import com.leo.darkroom.ui.theme.editorialColors

/**
 * W1 选图页：无权限入口（相册 Photo Picker / 拍照 intent / 三张内置示例）。
 * 空态插画由示例图充当（DESIGN.md §5.8：空态必须有图形 + 行动按钮）。
 */
@Composable
fun PickScreen(vm: DarkroomViewModel, state: UiState) {
    val colors = editorialColors()

    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? -> vm.onPhotoPicked(uri) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { ok -> vm.onCameraResult(ok) }

    val cameraUri = remember { { vm.prepareCamera() } }

    Column(
        Modifier
            .fillMaxSize()
            .pageInsets()
            .padding(20.dp),
    ) {
        // it-003 O6：内容可滚，footer 常驻视口底（weight 仅在有界父级可用）
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
        // 顶行：品牌 + 设置入口
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "DARKROOM",
                style = MaterialTheme.typography.labelSmall,
                color = colors.inkFaint,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = vm::openSettings, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Outlined.Tune, contentDescription = "设置", tint = colors.inkFaint)
            }
        }

        Spacer(Modifier.height(8.dp))

        // Display 大标题（衬线）
        Text(
            "显影",
            style = MaterialTheme.typography.displayLarge,
            color = colors.ink,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "把回忆洗出来——传一张图，看它像拍立得相纸一样慢慢显影。",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.inkFaint,
        )

        Spacer(Modifier.height(28.dp))

        // 主行动
        Button(
            onClick = {
                pickLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("从相册选一张")
        }

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = { cameraLauncher.launch(cameraUri()) },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text("拍一张")
        }

        Spacer(Modifier.height(28.dp))

        Text(
            "或者，先拿示例图试试手感",
            style = MaterialTheme.typography.labelSmall,
            color = colors.inkFaint,
        )
        Spacer(Modifier.height(10.dp))

        // 示例图行（内置程序化风景，零权限即体验全流程）
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(SampleArt.titles) { index, title ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(104.dp)
                        .clickable { vm.onSamplePicked(index) },
                ) {
                    SampleThumb(index) {
                        Image(
                            bitmap = it,
                            contentDescription = title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(104.dp)
                                .clip(RoundedCornerShape(14.dp)),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        title,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.inkFaint,
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        } // end scroll content

        // footer：贴视口底（it-003 O6 / US-10）
        Spacer(Modifier.height(16.dp))
        Text(
            "全程离线 · 照片不上传",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
            color = colors.inkFaint,
        )
    }
}

/** 示例缩略图：512px 轻量版（会话用的全尺寸图由 PhotoRepository 另行缓存） */
@Composable
private fun SampleThumb(index: Int, content: @Composable (ImageBitmap) -> Unit) {
    val bmp = remember(index) { SampleArt.render(index, 512) }
    content(bmp.asImageBitmap())
}
