package com.leo.darkroom.ui.pick

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leo.darkroom.DarkroomViewModel
import com.leo.darkroom.DarkroomViewModel.UiState
import com.leo.darkroom.card.SampleArt
import com.leo.darkroom.ui.pageInsets
import com.leo.darkroom.ui.theme.editorialColors

/** W1: a quiet, photo-led entrance to the local darkroom. */
@Composable
fun PickScreen(vm: DarkroomViewModel, state: UiState) {
    val colors = editorialColors()
    val context = LocalContext.current
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
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("DARKROOM", style = MaterialTheme.typography.labelSmall, color = colors.inkFaint)
                    Text("私人暗房", style = MaterialTheme.typography.bodySmall, color = colors.inkFaint)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = vm::openSettings, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Outlined.Tune, contentDescription = "设置", tint = colors.inkFaint)
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                "显影",
                style = MaterialTheme.typography.displayLarge,
                color = colors.ink,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                "把这一刻，慢慢洗成一张可以带走的相纸。",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.inkFaint,
            )

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    pickLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                enabled = !state.loadingPhoto,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.ink,
                    contentColor = colors.paper,
                ),
            ) {
                Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (state.loadingPhoto) "正在读取照片…" else "从相册挑一张")
            }

            TextButton(
                onClick = { cameraLauncher.launch(cameraUri()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = colors.ink),
            ) {
                Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("现在拍一张")
            }

            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("先从一张样片开始", style = MaterialTheme.typography.labelSmall, color = colors.inkFaint)
                Spacer(Modifier.weight(1f))
                Text("离线可试", style = MaterialTheme.typography.bodySmall, color = colors.inkFaint)
            }
            Spacer(Modifier.height(12.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(SampleArt.titles) { index, title ->
                    val bitmap = remember(context, index) { SampleArt.load(context, index, 512) }
                    Surface(
                        onClick = { vm.onSamplePicked(index) },
                        modifier = Modifier.width(112.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = colors.cardPaper,
                        border = BorderStroke(1.dp, colors.cardHairline),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(7.dp),
                        ) {
                            Image(
                                bitmap = remember(bitmap) { bitmap.asImageBitmap() },
                                contentDescription = title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(98.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                            )
                            Spacer(Modifier.height(7.dp))
                            Text(
                                title,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.cardInk,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "全程离线 · 照片不上传",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
            color = colors.inkFaint,
        )
    }
}
