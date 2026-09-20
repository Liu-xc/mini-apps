package com.leo.wardrobe.di

import android.content.Context
import com.leo.libs.store.FileMediaStore
import com.leo.libs.store.SnapshotStore
import com.leo.wardrobe.data.image.ImageFileStore
import com.leo.wardrobe.data.mock.DemoMode
import com.leo.wardrobe.data.mock.MockWardrobeRepository
import com.leo.wardrobe.data.prefs.PrefsStore
import com.leo.wardrobe.data.repo.WardrobeRepositoryImpl
import com.leo.wardrobe.domain.model.WardrobeData
import com.leo.wardrobe.domain.repository.WardrobeRepository
import com.leo.wardrobe.domain.usecase.BuildOutfitPrompt
import com.leo.wardrobe.domain.usecase.PickRandomOutfit
import com.leo.wardrobe.export.OutfitImageComposer
import com.leo.wardrobe.export.ShareClipboard
import java.io.File

/** 组合根：手动构造器装配（ADR-003）， specs/04-architecture.md */
class AppContainer(context: Context) {

    /** 演示模式（it-015）：开关在组合根构造时读取，切换经 DemoMode 重启进程生效 */
    private val demo = DemoMode.isEnabled(context)

    /** 演示模式下提醒通道停用（it-018 阶段C） */
    val isDemo: Boolean get() = demo

    val snapshotStore: SnapshotStore<WardrobeData> = SnapshotStore(
        dir = context.filesDir,
        fileName = "wardrobe.json",
        serializer = WardrobeData.serializer(),
        default = { WardrobeData() },
        versionOf = { it.schemaVersion },
    )

    /** 演示模式下图片指向 cacheDir/mock-images（assets 内置素材解包），真实 images/ 不被触碰。
     *  it-021：domain 依赖走 ImageStore 接口；抠图/解码/导出目录能力走 [com.leo.wardrobe.data.image.ImageEditStore] 接口（同一实例）。 */
    val imageStore: ImageFileStore = ImageFileStore(
        context,
        FileMediaStore(
            if (demo) context.cacheDir else context.filesDir,
            if (demo) "mock-images" else "images",
        ),
    )

    /** 图片加工能力（it-021 收编自具体类依赖） */
    val imageEditStore: com.leo.wardrobe.data.image.ImageEditStore get() = imageStore

    val repository: WardrobeRepository =
        if (demo) MockWardrobeRepository() else WardrobeRepositoryImpl(snapshotStore, imageStore)

    val prefs: PrefsStore = PrefsStore(context)
    /** 「好久没穿」提醒设置（it-018 阶段C） */
    val recapPrefs: com.leo.wardrobe.data.prefs.RecapPrefsStore =
        com.leo.wardrobe.data.prefs.RecapPrefsStore(context)
    /** 主体抠图引擎（it-016 US-15）：模型打包 assets、bytes 注入（ADR-003）；构造零副作用，
     *  onnxruntime 类与会话在首次去背景时才加载（ADR-002），不进启动路径 */
    val cutoutEngine: com.leo.libs.cutout.CutoutEngine = com.leo.libs.cutout.OnnxCutoutEngine(
        modelBytes = { context.assets.open("u2netp.onnx").use { it.readBytes() } },
    )
    val buildPrompt: BuildOutfitPrompt = BuildOutfitPrompt()
    val pickRandom: PickRandomOutfit = PickRandomOutfit()
    val imageComposer: OutfitImageComposer = OutfitImageComposer(imageStore)
    val share: ShareClipboard = ShareClipboard(context)

    init {
        if (demo) unpackMockImages(context)
    }

    /** 内置演示照片解包到 mock 图片目录（缺才拷，保持幂等） */
    private fun unpackMockImages(context: Context) {
        val dir = File(context.cacheDir, "mock-images").apply { mkdirs() }
        context.assets.list("mock").orEmpty().forEach { name ->
            val target = File(dir, name)
            if (!target.exists()) {
                context.assets.open("mock/$name").use { input ->
                    target.outputStream().use { input.copyTo(it) }
                }
            }
        }
    }
}
