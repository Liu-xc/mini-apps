package com.leo.wardrobe.ui.records

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.leo.wardrobe.domain.model.itemsOf
import com.leo.wardrobe.domain.model.outfitsOf
import com.leo.wardrobe.domain.model.tagsUsedIn
import com.leo.wardrobe.ui.AppViewModel
import com.leo.wardrobe.ui.components.EmptyState
import com.leo.wardrobe.ui.components.FilterChipsRow
import com.leo.wardrobe.ui.components.TagRow
import com.leo.wardrobe.ui.detail.OutfitThumb
import com.leo.wardrobe.ui.theme.editorialColors

/**
 * W8 穿搭记录页（US-10/13）：网格（成品图优先/合成图拼贴占位）+ 标签筛选。
 */
@Composable
fun RecordsScreen(
    vm: AppViewModel,
    onOpenOutfit: (String) -> Unit,
) {
    val person by vm.currentPerson.collectAsState()
    val data by vm.data.collectAsState()
    var filterTag by remember { mutableStateOf<String?>(null) }

    val personId = person?.id
    val outfits = if (personId != null) data.outfitsOf(personId) else emptyList()
    val filtered = if (filterTag == null) outfits
    else outfits.filter { filterTag!! in it.tags }
    val tags = remember(personId, data) {
        if (personId != null) data.tagsUsedIn(personId) else emptyList()
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            "穿搭记录 · ${person?.name ?: ""}",
            style = MaterialTheme.typography.headlineMedium,
            color = editorialColors().ink,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 6.dp),
        )

        if (tags.isNotEmpty()) {
            FilterChipsRow(
                options = tags,
                selected = filterTag,
                onSelect = { filterTag = it },
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }

        if (outfits.isEmpty()) {
            EmptyState(
                title = "还没有穿搭记录",
                hint = "在搭配页「☆收藏这套」，或生成效果图后「＋录入成品图」，就会出现在这里",
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(filtered, key = { it.id }) { outfit ->
                    Box(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(top = 4.dp)) {
                            OutfitThumb(vm, outfit, modifier = Modifier.fillMaxWidth()) {
                                onOpenOutfit(outfit.id)
                            }
                            if (outfit.tags.isNotEmpty()) {
                                Row(Modifier.padding(top = 4.dp)) {
                                    TagRow(outfit.tags.take(3))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
