package com.leo.wardrobe.data.gen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.leo.libs.agent.AgentError
import com.leo.libs.agent.ModelSpec
import com.leo.libs.agent.ProviderSpec
import com.leo.libs.agent.Usage
import com.leo.libs.agent.image.GeneratedImage
import com.leo.libs.agent.image.ImageGenEvent
import com.leo.libs.agent.image.ImageGenRequest
import com.leo.libs.agent.image.ImageModel
import com.leo.libs.agent.image.ImageRef
import com.leo.libs.agent.image.OkHttpImageModel
import com.leo.wardrobe.data.mock.MockImageModel
import com.leo.wardrobe.di.AppContainer
import com.leo.wardrobe.domain.model.Item
import com.leo.wardrobe.domain.model.OutfitImage
import com.leo.wardrobe.domain.usecase.BuildTryOnPrompt
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * it-077 · 穿搭生图控制器：W7 直达 sheet 与顾问对话流工具共用的一条链路——
 * 解析生图连接（Prefs + Keystore）→ 装配参考图（人物参考照 + 单品图，WebP→JPEG base64）
 * → ImageModel 事件流 → 候选字节在内存 → 选定后转 WebP 落盘挂 Outfit（source=ai）→ 用量记账。
 *
 * 演示模式一律走 [MockImageModel]（不出网不计费）；开销类策略按拍板①不做拦截。
 */
class OutfitImageGenerator(private val container: AppContainer) {

    /** 生图连接解析结果：spec + 生效模型 id */
    data class Connection(val spec: ProviderSpec, val model: String) {
        val modelSpec: ModelSpec? get() = spec.model(model)
    }

    sealed interface RunOutcome {
        /** 候选字节（App 内直接给 Coil 预览，不经临时 URL） */
        data class Candidates(val images: List<GeneratedImage>, val model: String) : RunOutcome
        data class Failed(val error: AgentError) : RunOutcome
    }

    sealed interface SaveOutcome {
        data class Saved(val outfitId: String, val file: String, val model: String) : SaveOutcome
        data class Failed(val error: AgentError) : SaveOutcome
    }

    /** 未配置（厂商/模型缺或无 Key）返回 null——UI 据此引导去 W11 */
    suspend fun connection(): Connection? {
        val presetId = container.prefs.aiImagePresetId.first()
        val modelSel = container.prefs.aiImageModel.first()
        val spec: ProviderSpec? = if (presetId == CUSTOM_IMAGE_ID) {
            val base = container.prefs.aiImageCustomBaseUrl.first()
            val model = container.prefs.aiImageCustomModel.first()
            if (base.isBlank() || model.isBlank()) null
            else ProviderSpec.customImage(base, model)
        } else {
            com.leo.libs.agent.ModelCatalog.byId(presetId)
        }
        val model = spec?.imageModels?.let { models -> modelSel.ifBlank { models.firstOrNull()?.id } }
        if (spec == null || model.isNullOrBlank()) return null
        // 同一厂商一把 Key 双轨共用；无 Key 视为未配置
        val hasKey = container.apiKeyStore.get(spec.id)?.isNotBlank() == true
        return if (hasKey) Connection(spec, model) else null
    }

    private fun imageModel(spec: ProviderSpec): ImageModel =
        if (container.isDemo) MockImageModel(spec) else OkHttpImageModel(spec, container.apiKeyStore)

    /** 参考图张数预检（Rectifier 思想：超档位在 UI 层就该拦住，这里兜底） */
    fun inputLimit(connection: Connection): Int =
        connection.modelSpec?.inputImages?.last ?: DEFAULT_MAX_INPUT

    /**
     * 跑一次生成：返回候选字节，不落盘。用 [onProgress] 透出进度文案。
     * @param items 参与的单品（参考图按 person → items 顺序）
     * @param personRefFile 人物参考照文件名（null = 不带人物参考）
     */
    suspend fun run(
        connection: Connection,
        prompt: String,
        items: List<Item>,
        personRefFile: String?,
        resolution: String?,
        extra: Map<String, String>,
        onProgress: (String) -> Unit,
    ): RunOutcome {
        val refs = buildRefs(personRefFile, items.map { it.imageFile }, connection)
        if (refs.isEmpty() && items.isNotEmpty()) {
            return RunOutcome.Failed(AgentError.Schema("参考图读取失败：本地文件缺失"))
        }
        val request = ImageGenRequest(
            model = connection.model,
            prompt = prompt,
            images = refs,
            resolution = resolution,
            extra = extra.mapValues { (_, v) -> JsonPrimitive(v) },
        )
        var failure: AgentError? = null
        var candidates: List<GeneratedImage>? = null
        var recorded = false
        return try {
            imageModel(connection.spec).generate(request).collect { ev ->
                when (ev) {
                    is ImageGenEvent.Started -> {}
                    is ImageGenEvent.Progress -> onProgress(ev.message)
                    is ImageGenEvent.Completed -> {
                        candidates = ev.images
                        if (!recorded && ev.imageCount > 0) {
                            // 纯信息展示（拍板①：不做拦截）
                            container.agentUsage.record(connection.spec.id, connection.model, Usage(images = ev.imageCount))
                            recorded = true
                        }
                    }
                    is ImageGenEvent.Failed -> failure = ev.error
                }
            }
            when {
                failure != null -> RunOutcome.Failed(failure!!)
                candidates.isNullOrEmpty() -> RunOutcome.Failed(AgentError.Provider(-1, "生成未返回图片"))
                else -> RunOutcome.Candidates(candidates!!, connection.model)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        }
    }

    /** 选定候选落盘并挂到穿搭（outfitId 空 = 按 itemIds 新建穿搭） */
    suspend fun save(
        personId: String,
        outfitId: String?,
        itemIds: List<String>,
        chosen: GeneratedImage,
        model: String,
        prompt: String,
    ): SaveOutcome = withContext(Dispatchers.IO) {
        val file = container.imageStore.putPackageImage(chosen.bytes)
            ?: return@withContext SaveOutcome.Failed(AgentError.Provider(-1, "生成图保存失败"))
        val outfit = outfitId?.let { id -> container.repository.data.value.outfits.find { it.id == id } }
            ?: runCatching { container.repository.createOutfit(personId, itemIds) }.getOrNull()
            ?: return@withContext SaveOutcome.Failed(AgentError.Provider(-1, "穿搭不存在或创建失败"))
        container.repository.addEffectImage(
            outfit.id,
            OutfitImage(
                file = file,
                source = SOURCE_AI,
                model = model,
                prompt = prompt.take(400),
            ),
        )
        SaveOutcome.Saved(outfit.id, file, model)
    }

    /** 高级面板取值记忆：{modelId: {paramKey: value}} */
    suspend fun lastParams(): Map<String, Map<String, String>> {
        val raw = container.prefs.aiImageLastParams.first()
        return runCatching {
            Json.parseToJsonElement(raw).jsonObject.mapValues { (_, perModel) ->
                (perModel as? JsonObject).orEmpty().entries.associate { (k, v) ->
                    k to (v.jsonPrimitive.contentOrNull ?: v.toString())
                }
            }
        }.getOrDefault(emptyMap())
    }

    suspend fun saveLastParams(modelId: String, values: Map<String, String>) {
        val all = lastParams().toMutableMap()
        all[modelId] = values
        val json = JsonObject(all.mapValues { (_, per) -> JsonObject(per.mapValues { (_, v) -> JsonPrimitive(v) }) }).toString()
        container.prefs.setAiImageLastParams(json)
    }

    /**
     * 参考图装配：人物参考照在前、衣物在后（多图融合以最后一张定宽高比——衣物收尾）。
     * 衣物 WebP 统一转 JPEG（各家对 WebP 支持不一），≤1440px 由存储层天然满足。
     */
    private suspend fun buildRefs(personRefFile: String?, itemFiles: List<String>, connection: Connection): List<ImageRef> =
        withContext(Dispatchers.IO) {
            buildList {
                personRefFile?.let { f -> fileToDataUri(f)?.let { add(ImageRef.DataUri(it)) } }
                val limit = inputLimit(connection)
                var garmentCount = if (personRefFile != null) 1 else 0
                for (f in itemFiles) {
                    if (garmentCount >= limit) break
                    fileToDataUri(f)?.let {
                        add(ImageRef.DataUri(it))
                        garmentCount++
                    }
                }
            }
        }

    private fun fileToDataUri(fileName: String): String? = runCatching {
        val path = container.imageStore.file(fileName)?.absolutePath ?: return null
        val bitmap = BitmapFactory.decodeFile(path) ?: return null
        val buffer = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, buffer)
        bitmap.recycle()
        "data:image/jpeg;base64," + Base64.encodeToString(buffer.toByteArray(), Base64.NO_WRAP)
    }.getOrNull()

    companion object {
        const val CUSTOM_IMAGE_ID = "custom-image"
        const val SOURCE_AI = "ai"
        const val SOURCE_MANUAL = "manual"
        private const val DEFAULT_MAX_INPUT = 10
        private const val JPEG_QUALITY = 90

        /** 生成 sheet 与顾问工具共用的提示词口径 */
        fun promptOf(items: List<Item>, scene: String, personNote: String): String =
            BuildTryOnPrompt.build(items, scene, personNote)
    }
}
