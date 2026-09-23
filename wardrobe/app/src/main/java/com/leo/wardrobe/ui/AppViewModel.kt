package com.leo.wardrobe.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.leo.wardrobe.WardrobeApp
import com.leo.wardrobe.domain.model.Item
import com.leo.wardrobe.domain.model.NoteParent
import com.leo.wardrobe.domain.model.Outfit
import com.leo.wardrobe.domain.model.Person
import com.leo.wardrobe.domain.model.WardrobeCategory
import com.leo.wardrobe.domain.model.WardrobeData
import com.leo.wardrobe.domain.model.WishItem
import com.leo.wardrobe.domain.model.WishOutfit
import com.leo.wardrobe.domain.model.itemById
import com.leo.wardrobe.domain.model.itemsOf
import com.leo.wardrobe.domain.model.outfitById
import com.leo.wardrobe.domain.model.outfitWithItems
import com.leo.wardrobe.domain.model.personById
import com.leo.wardrobe.domain.model.newId
import com.leo.wardrobe.domain.model.wishItemById
import com.leo.wardrobe.domain.model.wishItemsOf
import com.leo.wardrobe.domain.model.wishOutfitWithMembers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/**
 * 全局 ViewModel（SSOT 出口，specs/04-architecture.md）：
 * 当前角色 + 全量数据 + 组合记忆；写操作全部转发 Repository。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as WardrobeApp).container
    val repo = container.repository
    private val prefs = container.prefs

    val imageComposer get() = container.imageComposer
    val promptBuilder get() = container.buildPrompt
    val share get() = container.share

    fun imageFileOf(name: String): File? = container.imageStore.file(name)?.takeIf { it.exists() }

    /** 一次性消息（snackbar） */
    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    fun toast(msg: String?) { _toast.value = msg }

    /** it-024：带动作按钮的一次性消息（导出完成 → [分享]） */
    data class ActionToast(val message: String, val actionLabel: String, val onAction: (() -> Unit)?)

    private val _actionToast = MutableStateFlow<ActionToast?>(null)
    val actionToast: StateFlow<ActionToast?> = _actionToast.asStateFlow()

    fun toastAction(message: String, actionLabel: String, onAction: (() -> Unit)? = null) {
        _actionToast.value = ActionToast(message, actionLabel, onAction)
    }

    fun consumeActionToast() { _actionToast.value = null }

    /**
     * it-020：写路径统一兜底——失败 Log + toast（quiet 时仅 Log），成功提示可选。
     * 内存快照回滚由 libs/store 的 commit 序列天然承担（commit 抛异常则快照不赋值）。
     */
    private fun launchSafely(
        okToast: String? = null,
        failToast: String = "操作失败",
        quiet: Boolean = false,
        block: suspend () -> Unit,
    ) = viewModelScope.launch {
        try {
            block()
            okToast?.let(::toast)
        } catch (t: Throwable) {
            android.util.Log.e("Wardrobe", "viewModel write failed", t)
            if (!quiet) toast("$failToast：${t.message ?: t.javaClass.simpleName}")
        }
    }

    val data: StateFlow<WardrobeData> = repo.data

    val currentPerson: StateFlow<Person?> =
        combine(repo.data, prefs.currentPersonId) { d, savedId ->
            savedId?.let { d.personById(it) } ?: d.persons.firstOrNull()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 各槽位组合记忆：Map<品类.name, itemId>（US-06） */
    val slotSelections: StateFlow<Map<String, String>> =
        currentPerson.filterNotNull()
            .flatMapLatest { prefs.slotSelections(it.id) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    init {
        launchSafely { repo.ensureDefaultPerson() }
    }

    fun switchPerson(id: String) {
        viewModelScope.launch { prefs.setCurrentPerson(id) }
    }

    fun setSlot(category: WardrobeCategory, itemId: String?) {
        val person = currentPerson.value ?: return
        viewModelScope.launch { prefs.saveSlotSelection(person.id, category, itemId) }
    }

    /** 组合记忆首值（恢复槽位用）：等待 DataStore 首个值，避免与 pager 初始化竞态 */
    suspend fun firstSlotSelection(personId: String?, category: WardrobeCategory): String? {
        if (personId == null) return null
        return prefs.slotSelections(personId).firstOrNull()?.get(category.name)
    }

    /** 生图文案的人物描述（全局记住，it-002） */
    val personNote: StateFlow<String> = prefs.personNote
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun setPersonNote(note: String) {
        viewModelScope.launch { prefs.setPersonNote(note) }
    }

    /** 导出的自定义要求（it-013，记住上次） */
    val customPrompt: StateFlow<String> = prefs.customPrompt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun setCustomPrompt(prompt: String) {
        viewModelScope.launch { prefs.setCustomPrompt(prompt) }
    }
    /** 导出面板五维选择记忆（it-011 O8；it-012 增 ready 修恢复竞态） */
    val exportSelections: StateFlow<Map<String, String>> = prefs.exportSelections
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val exportSelectionsReady: StateFlow<Boolean> = prefs.exportSelectionsReady
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setExportSelections(selections: Map<String, String>) {
        viewModelScope.launch { prefs.saveExportSelections(selections) }
    }

    /** W1 格位滑动 coach 首演标记（it-011 O6） */
    val coachSlotsShown: StateFlow<Boolean> = prefs.coachSlotsShown
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun markCoachSlotsShown() {
        viewModelScope.launch { prefs.markCoachSlotsShown() }
    }

    // ---- Person ----
    fun addPerson(name: String, emoji: String) = launchSafely {
        val p = repo.addPerson(name, emoji)
        prefs.setCurrentPerson(p.id)
        toast("已创建 ${p.emoji} ${p.name} 的衣橱")
    }

    fun updatePerson(id: String, name: String, emoji: String) = launchSafely { repo.updatePerson(id, name, emoji) }

    /** it-017：形象参考照（photoFile 为已导入文件名；换照/移除的旧文件清理在 Repository） */
    fun setPersonRefPhoto(id: String, photoFile: String) =
        launchSafely(okToast = "形象参考照已设置", failToast = "设置失败") { repo.setPersonRefPhoto(id, photoFile) }

    fun removePersonRefPhoto(id: String) =
        launchSafely(okToast = "形象参考照已移除") { repo.removePersonRefPhoto(id) }

    fun deletePerson(id: String) = launchSafely(okToast = "角色及其衣物已删除") {
        repo.deletePerson(id)
        repo.data.value.persons.firstOrNull()?.let { prefs.setCurrentPerson(it.id) }
    }

    // ---- Item ----

    /**
     * 选中照片后立即导入落盘（hotfix it-002+1）：
     * Photo Picker 的 URI 授权可能随转屏/进程重建/部分 ROM 策略失效，
     * 若拖到点「保存」才导入会静默失败。改为选择瞬间导入，保存时只用本地文件。
     */
    fun importPhoto(uri: android.net.Uri, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            val file = container.imageStore.importFromUri(uri.toString())
            if (file == null) toast("照片导入失败：无法读取该图片（可换个相册里的普通照片试试）")
            onDone(file)
        }
    }

    /** 去背景（it-016 US-15）：生成抠图版新文件（原图不动）；失败返回 null 并 toast，原图不受影响 */
    fun cutoutPhoto(srcFile: String, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            val out = container.imageEditStore.cutoutTo(srcFile, container.cutoutEngine)
            if (out == null) toast("去背景失败：这张照片先保持原样")
            onDone(out)
        }
    }

    /** 删除本会话产生的临时图片文件（换照片/退出/保存后的清理；失败仅记日志） */
    fun deletePhotoFile(name: String) {
        launchSafely(quiet = true) { container.imageStore.delete(name) }
    }

    /** photoFile 为已落盘的图片文件名（选择时即导入）；编辑时为 null 表示沿用旧照片 */
    fun saveItem(existing: Item?, photoFile: String?, name: String, category: WardrobeCategory,
                 color: String, desc: String, tags: List<String>, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val person = currentPerson.value ?: repo.ensureDefaultPerson()
                val file = photoFile ?: existing?.imageFile
                if (file == null) { toast("请先选择照片"); onDone(false); return@launch }
                val item = Item(
                    id = existing?.id ?: newId(),
                    personId = existing?.personId ?: person.id,
                    category = category,
                    name = name.trim().ifEmpty { category.label },
                    color = color.trim(),
                    desc = desc.trim(),
                    imageFile = file,
                    tags = tags.distinct().take(10),
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
                repo.upsertItem(item)
                // 新照片替换旧照片时删除旧文件
                if (existing != null && photoFile != null && existing.imageFile != file) {
                    container.imageStore.delete(existing.imageFile)
                }
                toast(if (existing == null) "已添加「${item.name}」" else "已更新「${item.name}」")
                onDone(true)
            } catch (t: Throwable) {
                android.util.Log.e("Wardrobe", "saveItem failed", t)
                toast("保存失败：${t.message ?: t.javaClass.simpleName}")
                onDone(false)
            }
        }
    }

    fun deleteItem(id: String) = launchSafely(okToast = "已删除") { repo.deleteItem(id) }

    // ---- Outfit ----

    /** 组合去重查询（it-004）：当前选中组合是否已保存为穿搭 */
    fun savedOutfitFor(itemIds: List<String>): Outfit? {
        val person = currentPerson.value ?: return null
        return data.value.outfitWithItems(person.id, itemIds)
    }

    /** 保存当前组合为穿搭（去重）：已存在则复用（有新标签时更新），不重复创建 */
    fun saveOutfitDedup(itemIds: List<String>, tags: List<String> = emptyList(), existing: Outfit? = null) {
        launchSafely {
            val person = currentPerson.value ?: return@launchSafely
            val found = existing ?: data.value.outfitWithItems(person.id, itemIds)
            if (found != null) {
                if (tags.isNotEmpty() && tags.toSet() != found.tags.toSet()) {
                    repo.updateOutfit(found.copy(tags = tags.distinct()))
                }
                toast("这套已在穿搭记录中")
            } else {
                repo.createOutfit(person.id, itemIds, tags)
                toast("已保存这套穿搭")
            }
        }
    }

    /** ☆收藏：按当前组合创建穿搭（US-08，保留旧入口兼容） */
    fun createOutfit(itemIds: List<String>, tags: List<String>, onDone: (Outfit?) -> Unit = {}) =
        launchSafely {
            val person = currentPerson.value ?: return@launchSafely
            val o = repo.createOutfit(person.id, itemIds, tags)
            toast("已收藏这套穿搭")
            onDone(o)
        }

    fun deleteOutfit(id: String) = launchSafely(okToast = "已删除穿搭") { repo.deleteOutfit(id) }

    fun updateOutfitTags(id: String, tags: List<String>) = launchSafely {
        repo.data.value.outfitById(id)?.let { repo.updateOutfit(it.copy(tags = tags.distinct())) }
    }

    /** 导入成品图并挂到穿搭（US-09）；outfit 为 null 时先按 items 创建 */
    fun importEffectImage(outfit: Outfit?, itemIds: List<String>, uri: Uri) {
        launchSafely {
            val target = outfit ?: run {
                val person = currentPerson.value ?: return@launchSafely
                repo.createOutfit(person.id, itemIds)
            }
            val file = container.imageStore.importFromUri(uri.toString())
            if (file == null) { toast("图片导入失败"); return@launchSafely }
            repo.addEffectImage(target.id, file)
            toast("成品图已录入")
        }
    }

    fun removeEffectImage(outfitId: String, file: String) = launchSafely { repo.removeEffectImage(outfitId, file) }

    // ---- Note ----
    fun addNote(parentType: NoteParent, parentId: String, text: String) = launchSafely {
        if (text.isBlank()) return@launchSafely
        repo.addNote(parentType, parentId, text)
    }

    fun deleteNote(id: String) = launchSafely { repo.deleteNote(id) }

    // ---- 穿搭打卡（it-018 阶段A） ----

    /** 打卡：今天穿了这套（同日多套允许，再点即再记一次） */
    fun checkinOutfit(outfitId: String) =
        launchSafely(okToast = "已打卡，今天也穿得好看", failToast = "打卡失败") {
            repo.addWearLog(outfitId, System.currentTimeMillis())
        }

    /** 撤销今日对该穿搭的全部打卡 */
    fun undoTodayWear(outfitId: String) = launchSafely(okToast = "已撤销今日打卡") {
        val zone = java.time.ZoneId.systemDefault()
        val today = java.time.LocalDate.now()
        repo.deleteWearLogsOf(
            outfitId,
            today.atStartOfDay(zone).toInstant().toEpochMilli(),
            today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
        )
    }

    // ---- 统计回顾（it-018；it-021 回顾域成员已拆至 ui/recap/RecapViewModel） ----

    // ---- 心愿域（it-019） ----

    /** W1「混入心愿」开关：默认关，仅会话内记忆（同 it-017 参考照开关先例） */
    private val _mixWishes = MutableStateFlow(false)
    val mixWishes: StateFlow<Boolean> = _mixWishes.asStateFlow()

    fun setMixWishes(enabled: Boolean) { _mixWishes.value = enabled }

    fun saveWishItem(
        existing: WishItem?,
        name: String,
        category: WardrobeCategory,
        color: String,
        desc: String,
        price: Double?,
        url: String,
        tags: List<String>,
        photoFile: String?,
        onDone: (Boolean) -> Unit,
    ) {
        launchSafely(failToast = "保存失败") {
            val person = currentPerson.value ?: repo.ensureDefaultPerson()
            val file = photoFile ?: existing?.imageFile
            if (name.isBlank()) { toast("名称必填"); onDone(false); return@launchSafely }
            val w = WishItem(
                id = existing?.id ?: newId(),
                personId = existing?.personId ?: person.id,
                category = category,
                name = name.trim().ifEmpty { category.label },
                color = color.trim(),
                desc = desc.trim(),
                price = price,
                url = url.trim(),
                imageFile = file,
                tags = tags.distinct().take(10),
                purchasedAt = existing?.purchasedAt,
                purchasedItemId = existing?.purchasedItemId,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            )
            repo.upsertWishItem(w)
            if (existing != null && photoFile != null && existing.imageFile != null && existing.imageFile != file) {
                container.imageStore.delete(existing.imageFile)
            }
            toast(if (existing == null) "已收进想买" else "已更新「${w.name}」")
            onDone(true)
        }
    }

    fun deleteWishItem(id: String) = launchSafely(okToast = "已删除这条心愿") { repo.deleteWishItem(id) }

    /**
     * 已买到 → 转正：创建正式 Item（photoFile 可空时沿用商品图）+ 回填购入记录；
     * 含该愿望件的心愿穿搭在 Repository 内自动更新（返回更新的套数供提示）。
     */
    fun purchaseWishItem(wishItemId: String, photoFile: String?, name: String, category: WardrobeCategory,
                         color: String, desc: String, tags: List<String>, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val wish = data.value.wishItemById(wishItemId)
                val file = photoFile ?: wish?.imageFile
                if (file == null) { toast("先拍一张实物照（或沿用商品图）"); onDone(false); return@launch }
                val affected = data.value.wishOutfits.count { wishItemId in it.wishItemIds }
                repo.purchaseWishItem(
                    wishItemId,
                    Item(
                        id = newId(),
                        personId = wish?.personId ?: currentPerson.value?.id.orEmpty(),
                        category = category,
                        name = name.trim().ifEmpty { category.label },
                        color = color.trim(),
                        desc = desc.trim(),
                        imageFile = file,
                        tags = tags.distinct().take(10),
                    ),
                )
                toast(
                    if (affected > 0) "已收进衣橱 ✓ $affected 套心愿穿搭已更新"
                    else "已收进衣橱 ✓",
                )
                onDone(true)
            } catch (t: Throwable) {
                android.util.Log.e("Wardrobe", "purchaseWishItem failed", t)
                toast("转正失败：${t.message ?: t.javaClass.simpleName}")
                onDone(false)
            }
        }
    }

    /** 心愿组合去重查询（对齐 US-08 语义：itemIds/wishItemIds 各自集合判等） */
    fun savedWishOutfitFor(itemIds: List<String>, wishItemIds: List<String>): WishOutfit? {
        val person = currentPerson.value ?: return null
        return data.value.wishOutfitWithMembers(person.id, itemIds, wishItemIds)
    }

    /** 保存当前混搭组合为心愿穿搭（去重：已存在则提示，不重复创建） */
    fun saveWishOutfit(itemIds: List<String>, wishItemIds: List<String>, tags: List<String> = emptyList()) {
        launchSafely {
            val person = currentPerson.value ?: return@launchSafely
            if (wishItemIds.isEmpty()) return@launchSafely
            if (data.value.wishOutfitWithMembers(person.id, itemIds, wishItemIds) != null) {
                toast("这套已在心愿穿搭中")
            } else {
                repo.createWishOutfit(person.id, itemIds, wishItemIds, tags)
                toast("已存为心愿穿搭")
            }
        }
    }

    fun deleteWishOutfit(id: String) = launchSafely(okToast = "已删除心愿穿搭") { repo.deleteWishOutfit(id) }

    /** 心愿穿搭详情「→ 去预览」：恢复组合到各槽位并开启混入开关；失败仅记日志 */
    fun restoreWishOutfitToSlots(wishOutfit: WishOutfit) {
        val person = currentPerson.value ?: return
        launchSafely(quiet = true) {
            wishOutfit.itemIds.forEach { id ->
                data.value.itemById(id)?.let { prefs.saveSlotSelection(person.id, it.category, it.id) }
            }
            wishOutfit.wishItemIds.forEach { id ->
                data.value.wishItemById(id)?.let { prefs.saveSlotSelection(person.id, it.category, it.id) }
            }
            _mixWishes.value = true
        }
    }

    /** 导入上身预览图（生图回录，挂到心愿穿搭） */
    fun importPreviewImage(wishOutfitId: String, uri: Uri) {
        launchSafely {
            val file = container.imageStore.importFromUri(uri.toString())
            if (file == null) { toast("图片导入失败"); return@launchSafely }
            repo.addPreviewImage(wishOutfitId, file)
            toast("上身预览图已录入")
        }
    }

    fun removePreviewImage(wishOutfitId: String, file: String) = launchSafely { repo.removePreviewImage(wishOutfitId, file) }

    /** 一键升级：全部愿望件买齐后转正式穿搭 */
    fun promoteWishOutfit(id: String) =
        launchSafely(okToast = "已升级为正式穿搭", failToast = "升级失败") { repo.promoteWishOutfit(id) }

    // ---- 便捷查询 ----
    fun itemsOfPerson(): List<Item> =
        currentPerson.value?.let { data.value.itemsOf(it.id) } ?: emptyList()
}
