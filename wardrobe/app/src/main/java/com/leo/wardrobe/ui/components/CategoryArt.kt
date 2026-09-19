package com.leo.wardrobe.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.leo.wardrobe.R
import com.leo.wardrobe.domain.model.WardrobeCategory
import com.leo.wardrobe.ui.theme.editorialColors

/** 品类 3D 图标（thiings.co 素材，README 署名）；UI 层映射，domain 保持无 Android 依赖 */
val WardrobeCategory.iconRes: Int
    get() = when (this) {
        WardrobeCategory.TOP -> R.drawable.cat_top
        WardrobeCategory.OUTERWEAR -> R.drawable.cat_coat
        WardrobeCategory.BOTTOM -> R.drawable.cat_bottom
        WardrobeCategory.DRESS -> R.drawable.cat_dress
        WardrobeCategory.SHOES -> R.drawable.cat_shoes
        WardrobeCategory.BAG -> R.drawable.cat_bag
        WardrobeCategory.HAT -> R.drawable.cat_hat
        WardrobeCategory.ACCESSORY -> R.drawable.cat_accessory
    }

/** 品类 3D 图标 + 文案 */
@Composable
fun CategoryLabel(category: WardrobeCategory, count: Int? = null, size: Int = 16) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(category.iconRes),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(size.dp),
        )
        Text(
            buildString {
                append(category.label)
                if (count != null) append("（$count）")
            },
            style = MaterialTheme.typography.labelMedium,
            color = editorialColors().inkFaint,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}
