package com.leo.wardrobe.ui.records

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import com.leo.wardrobe.ui.components.StaggeredEntrance
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.leo.wardrobe.domain.model.Outfit
import com.leo.wardrobe.domain.model.itemById
import com.leo.wardrobe.domain.model.itemsOf
import com.leo.wardrobe.domain.model.outfitsOf
import com.leo.wardrobe.domain.model.tagsUsedIn
import com.leo.wardrobe.ui.AppViewModel
import com.leo.wardrobe.ui.components.EmptyState
import com.leo.wardrobe.ui.components.FilterChipsRow
import com.leo.wardrobe.ui.components.TagRow
import com.leo.wardrobe.ui.components.rememberHaptics
import com.leo.wardrobe.ui.detail.OutfitThumb
import com.leo.wardrobe.ui.theme.editorialColors
import com.leo.libs.carddeck.CardDeck
import com.leo.libs.carddeck.CardDeckController
import kotlinx.coroutines.launch

/**
 * W8 穿搭记录页（it-007：顶部侧滑卡组快速浏览/随机抽一套 + 下方全量网格）。
 */
@Composable
fun RecordsScreen(
    vm: AppViewModel,
    onOpenOutfit: (String) -> Unit,
    /** it-066：打卡/记录空态「去搭配一套」→ 切搭配 Tab（接线先例 it-031 C10 onGoRecords） */
    onGoOutfit: () -> Unit = {},
    // it-077 修订九：拼贴卡「生成效果图」角标未配置时点击直达 W11 生图模型设置
    onOpenSettings: () -> Unit = {},
) {
    val person by vm.currentPerson.collectAsState()
    val data by vm.data.collectAsState()
    // it-069 修2：筛选状态 rememberSaveable——Tab 往返不丢（HomeTabs 切换销毁页面）
    var filterTag by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    var deck by remember { mutableStateOf<CardDeckController<Outfit>?>(null) }
    var generateFor by remember { mutableStateOf<Outfit?>(null) } // it-077：卡片生成效果图
    // it-077 修订九：连接对象级解析（角标双态与生成 sheet 共用；角标常驻不再按配置隐藏）
    val imageGenConnection = rememberImageGenConnection(vm)
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()

    val personId = person?.id
    val outfits = remember(personId, data) { if (personId != null) data.outfitsOf(personId) else emptyList() }
    val filtered = remember(outfits, filterTag) {
        if (filterTag == null) outfits else outfits.filter { filterTag!! in it.tags }
    }
    val tags = remember(personId, data) {
        if (personId != null) data.tagsUsedIn(personId) else emptyList()
    }
    // it-047 #5：真实抽取后才 Confirm（size≤1 直接返回不算抽中落定；
    // null=并发幂等门拦截，避免双震）；isDrawing 挡双击窗口内第二次点击
    val startRandom: () -> Unit = {
        val c = deck
        if (c != null && !c.isDrawing) {
            scope.launch {
                val drew = c.drawRandom()
                if (drew != null && c.size > 1) haptics.confirm()
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        // it-071 P1：原「外层 verticalScroll + 固定高度不可滚网格」反模式废除——
        // 网格视口曾被钉成全数据集高度，懒加载失效（全部卡一次性组合常驻）。
        // 现网格自身即页面滚动容器，卡组/筛选行等页头以 span-2 item 进网格。
        if (outfits.isEmpty() || filtered.isEmpty()) {
            RecordsTopBar(
                name = person?.name ?: "",
                deck = deck,
                hasItems = filtered.isNotEmpty(),
                // it-045：顶栏下缘→内容 20dp（标题行底 6 + 空态/筛选顶 14）
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 6.dp),
                onRandom = startRandom,
            )
            if (tags.isNotEmpty()) {
                FilterChipsRow(
                    options = tags,
                    selected = filterTag,
                    onSelect = { filterTag = it },
                    fadeAtStart = true,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp),
                )
            }
            if (outfits.isEmpty()) {
                EmptyState(
                    title = "还没有穿搭记录",
                    hint = "在搭配页「保存这套」，或生成效果图后「录入成品图」，就会出现在这里",
                    // it-066：空态行动按钮（DESIGN.md §5.8 基线补齐）
                    actionLabel = "去搭配一套",
                    onAction = onGoOutfit,
                )
            } else {
                EmptyState(
                    title = "该标签下没有穿搭",
                    hint = "换一个标签，或清除筛选",
                    // it-066：空态行动按钮（DESIGN.md §5.8 基线补齐）
                    actionLabel = "清除筛选",
                    onAction = { filterTag = null },
                )
            }
        } else {
            // it-028：首屏瀑布入场（specs/05 #7），仅首进播放（DESIGN.md §3 预算）
            var entranceDone by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(900)
                entranceDone = true
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                // 原页头与网格的纵向节奏折算（arrangement 16 + 各 item top）：
                // 标题→筛选 20 / 筛选→卡组 20 / 卡组→计数 18；首行格 +6 属折算余量（验证记录注明）
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 30.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    RecordsTopBar(
                        name = person?.name ?: "",
                        deck = deck,
                        hasItems = filtered.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        onRandom = startRandom,
                    )
                }
                if (tags.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        FilterChipsRow(
                            options = tags,
                            selected = filterTag,
                            onSelect = { filterTag = it },
                            // it-065 修3：无行尾固定钮，起点即给「右侧还有内容」轻提示
                            fadeAtStart = true,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }

                // ---- 卡组：快速浏览 + 随机翻（it-007/it-010；it-011 增 ‹n/m› 卡序） ----
                // it-031 C6：翻页器移出拼贴区放卡下方居中（审查 P0：胶囊浮层压住帽行）；
                // it-033：胶囊整体保持外置居中不回退，但拆左右两半边独立翻页热区
                // ‹=上一张、›=下一张，各 48×48dp（≥44dp 基线），中心 n/m 纯展示
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            // 无筛选行时标题与卡组原间距 26=6+20，折算 top=10；有筛选行 20 → top=4
                            .padding(top = if (tags.isEmpty()) 10.dp else 4.dp, bottom = 8.dp),
                    ) {
                        // it-048：不得加 clipToBounds——甩卡是真实飞行（it-047 全路径），裁剪会把
                        // 卡片在容器边距处切掉（it-031 旧库时代的包裹已随自研内核删除）
                        Box(
                            Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            val controller = CardDeck(
                                items = filtered,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    // it-065 修1（覆盖 it-064 修2b② 的 start 内缩）：层叠几何
                                    // base(k)=(s·(V-1-k), -s·(V-1-k))——顶卡比深层卡右偏 28dp（V=3×14dp）。
                                    // it-064 只收 start 等于收窄卡宽、不挪右缘（内容右缘恒=页边距+style 内衬），
                                    // 顶卡右缘恒溢出屏 2dp、停驻上一张贴屏左缘；改为 end 22dp 吸收右偏：
                                    // 静止层叠恰好落进 20dp 页面栅格（深层左缘=页左边距、顶卡右缘=页右边距，
                                    // 居中且两侧对称可见安全边距），停驻上一张右缘=屏 −22dp 恒在屏外
                                    //（park 右缘 = −end 内缩，几何恒等式）。top 8dp + 容器 top 20dp ≥ 上探
                                    // 28dp，静止卡组不盖筛选行；容器不可裁剪（it-048），甩卡真实飞出不受影响。
                                    .padding(end = 22.dp, top = 8.dp)
                                    // it-072 W8：卡组仍是焦点，但首屏需同时露出“全部 N 套”与
                                    // 第一行历史记录；收紧 50dp，不改变卡组手势、叠层与安全边距。
                                    .height(318.dp),
                                // it-047：样式与弹簧全部走 DeckStyle 默认（14dp 层叠 / 6dp 内衬 /
                                // flyOutSpec = it-046 基准 spring(0.9,500)），不再引用三方库类型
                            ) { outfit ->
                                OutfitDeckCard(
                                    vm = vm,
                                    outfit = outfit,
                                    // it-077 修订九：角标常驻拼贴卡——未配置不再隐藏（Leo 拍板与预览面板同口径），
                                    // 未配置态点击直达 W11；已配置点击进生成 sheet
                                    configured = imageGenConnection != null,
                                    onOpen = {
                                        // it-047 #8③：抽取进行中卡面点击忽略
                                        if (deck?.isDrawing != true) onOpenOutfit(outfit.id)
                                    },
                                    onGenerate = { generateFor = outfit },
                                    onOpenSettings = onOpenSettings,
                                )
                            }
                            // it-071 P2：组合期写状态改 SideEffect（原直写在组合期，多一次重组）
                            SideEffect { deck = controller }
                        }
                        Row(
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.Surface(
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surface,
                                shadowElevation = 3.dp,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // it-033：左半边 = 上一张（48×48dp 热区）
                                    Box(
                                        Modifier
                                            .size(48.dp)
                                            .clickable { deck?.previous() }
                                            .semantics { contentDescription = "上一张" },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            "‹",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = editorialColors().ink,
                                        )
                                    }
                                    DeckCounter(
                                        deck = deck,
                                        total = filtered.size,
                                        modifier = Modifier.padding(horizontal = 2.dp),
                                    )
                                    // it-033：右半边 = 下一张（48×48dp 热区）
                                    Box(
                                        Modifier
                                            .size(48.dp)
                                            .clickable { deck?.next() }
                                            .semantics { contentDescription = "下一张" },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            "›",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = editorialColors().ink,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        if (filterTag == null) "全部 ${filtered.size} 套" else "筛选 · ${filtered.size} 套",
                        style = MaterialTheme.typography.titleSmall,
                        color = editorialColors().inkFaint,
                        modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
                    )
                }
                itemsIndexed(
                    filtered,
                    key = { _, it -> it.id },
                    contentType = { _, _ -> "outfit" },
                ) { index, outfit ->
                    StaggeredEntrance(index = index, animate = !entranceDone) {
                        Column(Modifier.padding(top = 4.dp)) {
                            OutfitThumb(vm, outfit, modifier = Modifier.fillMaxWidth()) {
                                onOpenOutfit(outfit.id)
                            }
                            if (outfit.tags.isNotEmpty()) {
                                Row(Modifier.padding(top = 4.dp)) { TagRow(outfit.tags.take(3)) }
                            }
                        }
                    }
                }
            }
        }
    }
    // it-077：穿搭卡片生成效果图 sheet（生成即录入该穿搭成品图）
    if (generateFor != null && imageGenConnection != null) {
        val target = generateFor!!
        OutfitGenerateSheet(
            vm = vm,
            outfit = target,
            items = remember(target, data) { target.itemIds.mapNotNull { data.itemById(it) } },
            person = person,
            personNote = vm.personNote.collectAsState().value,
            connection = imageGenConnection,
            onDismiss = { generateFor = null },
        )
    }
}

/**
 * 标题行 + 随机一套（空态分支与网格页头共用；it-071 页头以 span-2 item 进网格）。
 */
@Composable
private fun RecordsTopBar(
    name: String,
    deck: CardDeckController<Outfit>?,
    hasItems: Boolean,
    onRandom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Text(
            "穿搭记录 · $name",
            style = MaterialTheme.typography.headlineMedium,
            color = editorialColors().ink,
            modifier = Modifier.weight(1f),
        )
        RandomButton(deck = deck, hasItems = hasItems, onClick = onRandom)
    }
}

/**
 * ‹ n/m › 计数（it-047 #10 白名单：currentIndex 读取下沉到本独立组合——
 * 重组只波及胶囊自身，不回流 RecordsScreen 体级/网格）。
 */
@Composable
private fun DeckCounter(
    deck: CardDeckController<Outfit>?,
    total: Int,
    modifier: Modifier = Modifier,
) {
    val idx = (deck?.currentIndex ?: 0).coerceIn(0, (total - 1).coerceAtLeast(0))
    Text(
        "${idx + 1}/$total",
        style = MaterialTheme.typography.labelLarge,
        color = editorialColors().ink,
        modifier = modifier,
    )
}

/**
 * 「随机一套」按钮（it-047 #9：抽取状态读取收口在本组件内——
 * drawRandom 全程不触发 RecordsScreen 体级重组，只重绘按钮自身）。
 * it-069 修4：形制并入 W1 顶栏同款 TextButton + 图标（原实底 Button 双轨废止）。
 */
@Composable
private fun RandomButton(
    deck: CardDeckController<Outfit>?,
    hasItems: Boolean,
    onClick: () -> Unit,
) {
    val drawing = deck?.isDrawing == true
    TextButton(
        onClick = onClick,
        enabled = deck != null && !drawing && hasItems,
    ) {
        Icon(Icons.Rounded.Casino, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        // it-036 C12：文案与 W1 统一为「随机一套」（it-011 R3-P1 的两套文案废止），图标沿用
        Text("随机一套", style = MaterialTheme.typography.titleSmall)
    }
}

/**
 * 卡组穿搭卡（it-010 成品图优先；it-011 O7 人体叙事拼贴 + 缺失空槽）。
 */
@Composable
private fun OutfitDeckCard(
    vm: AppViewModel,
    outfit: Outfit,
    configured: Boolean,
    onOpen: () -> Unit,
    onGenerate: () -> Unit,
    onOpenSettings: () -> Unit = {},
) {
    val data by vm.data.collectAsState()
    val items = remember(data, outfit) { outfit.itemIds.mapNotNull { data.itemById(it) } }
    val effect = outfit.effectImages.firstOrNull()

    androidx.compose.material3.Surface(
        shape = MaterialTheme.shapes.large,
        color = androidx.compose.material3.MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxSize(),
        onClick = onOpen,
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                if (effect != null) {
                    // 用户导入的成品穿搭图：衬纸 Fit 完整展示（it-042 C2——原全幅 Crop 在宽盒里
                    // 把竖图人物头部裁掉，与同页网格缩略（0.86 近原比）两种呈现打架；it-011 C5 同语言）
                    // it-064 修2a：撤销 it-058 C2 的 hero 共享元素——卡组高频切换时 SharedTransition
                    // 的 bounds 跟踪与甩卡飞行互相干扰（实测切换不丝滑的根因），恢复整页转场
                    com.leo.wardrobe.ui.components.PhotoCard(
                        file = vm.imageFileOf(effect.file),
                        contentDescription = "穿搭成品图",
                        corner = 0.dp,
                        mat = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    // 人体叙事拼贴：淡色人形轮廓底 + 缺失品类虚线空槽（it-011 O7）
                    com.leo.wardrobe.ui.components.BodyCollage(
                        items = items,
                        imageFileOf = vm::imageFileOf,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // it-077 修订九（Leo）：角标只上拼贴卡（无成品图=该穿搭还没有效果图）；
                // 已有成品图的卡即结果展示，不挂入口（重生成走 W7 菜单）。
                // 角标常驻不再按配置状态隐藏——已配置=主色胶囊点击进生成 sheet；
                // 未配置=次级胶囊「去配置」点击直达 W11（与预览面板 AI 按钮同口径）
                if (effect == null) {
                    val configuredPill = configured
                    androidx.compose.material3.Surface(
                        onClick = if (configuredPill) onGenerate else onOpenSettings,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                        color = if (configuredPill) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.secondaryContainer,
                        shadowElevation = 3.dp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        ) {
                            Icon(
                                Icons.Rounded.AutoFixHigh,
                                contentDescription = "生成效果图",
                                tint = if (configuredPill) androidx.compose.ui.graphics.Color.White
                                else MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(15.dp),
                            )
                            Text(
                                if (configuredPill) "生成效果图" else "生成效果图 · 去配置",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (configuredPill) androidx.compose.ui.graphics.Color.White
                                else MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                }
            }
            if (outfit.tags.isNotEmpty()) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    TagRow(outfit.tags.take(4))
                }
            }
        }
    }
}
