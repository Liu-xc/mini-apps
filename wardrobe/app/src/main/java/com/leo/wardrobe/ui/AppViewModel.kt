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
import com.leo.wardrobe.domain.model.itemsOf
import com.leo.wardrobe.domain.model.outfitById
import com.leo.wardrobe.domain.model.personById
import com.leo.wardrobe.domain.model.newId
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

    fun imageFileOf(name: String): File? = container.imageStore.file(name).takeIf { it.exists() }

    /** 一次性消息（snackbar） */
    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    fun toast(msg: String?) { _toast.value = msg }

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
        viewModelScope.launch { repo.ensureDefaultPerson() }
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

    // ---- Person ----
    fun addPerson(name: String, emoji: String) = viewModelScope.launch {
        val p = repo.addPerson(name, emoji)
        switchPerson(p.id)
        toast("已创建 ${p.emoji} ${p.name} 的衣橱")
    }

    fun updatePerson(id: String, name: String, emoji: String) = viewModelScope.launch {
        repo.updatePerson(id, name, emoji)
    }

    fun deletePerson(id: String) = viewModelScope.launch {
        repo.deletePerson(id)
        val fallback = repo.data.value.persons.firstOrNull()
        if (fallback != null) prefs.setCurrentPerson(fallback.id)
        toast("角色及其衣物已删除")
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

    fun deleteItem(id: String) = viewModelScope.launch {
        repo.deleteItem(id)
        toast("已删除")
    }

    // ---- Outfit ----
    /** ☆收藏：按当前组合创建穿搭（US-08） */
    fun createOutfit(itemIds: List<String>, tags: List<String>, onDone: (Outfit?) -> Unit = {}) =
        viewModelScope.launch {
            val person = currentPerson.value ?: return@launch
            val o = repo.createOutfit(person.id, itemIds, tags)
            toast("已收藏这套穿搭")
            onDone(o)
        }

    fun deleteOutfit(id: String) = viewModelScope.launch {
        repo.deleteOutfit(id)
        toast("已删除穿搭")
    }

    fun updateOutfitTags(id: String, tags: List<String>) = viewModelScope.launch {
        repo.data.value.outfitById(id)?.let { repo.updateOutfit(it.copy(tags = tags.distinct())) }
    }

    /** 导入成品图并挂到穿搭（US-09）；outfit 为 null 时先按 items 创建 */
    fun importEffectImage(outfit: Outfit?, itemIds: List<String>, uri: Uri) {
        viewModelScope.launch {
            val target = outfit ?: run {
                val person = currentPerson.value ?: return@launch
                repo.createOutfit(person.id, itemIds)
            }
            val file = container.imageStore.importFromUri(uri.toString())
            if (file == null) { toast("图片导入失败"); return@launch }
            repo.addEffectImage(target.id, file)
            toast("成品图已录入")
        }
    }

    fun removeEffectImage(outfitId: String, file: String) = viewModelScope.launch {
        repo.removeEffectImage(outfitId, file)
    }

    // ---- Note ----
    fun addNote(parentType: NoteParent, parentId: String, text: String) = viewModelScope.launch {
        if (text.isBlank()) return@launch
        repo.addNote(parentType, parentId, text)
    }

    fun deleteNote(id: String) = viewModelScope.launch { repo.deleteNote(id) }

    // ---- 便捷查询 ----
    fun itemsOfPerson(): List<Item> =
        currentPerson.value?.let { data.value.itemsOf(it.id) } ?: emptyList()
}
