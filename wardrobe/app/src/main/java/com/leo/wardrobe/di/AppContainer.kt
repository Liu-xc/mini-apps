package com.leo.wardrobe.di

import android.content.Context
import com.leo.libs.store.FileMediaStore
import com.leo.libs.store.SnapshotStore
import com.leo.wardrobe.data.image.ImageFileStore
import com.leo.wardrobe.data.mock.DemoMode
import com.leo.wardrobe.data.mock.MockWardrobeData
import com.leo.wardrobe.data.mock.MockWardrobeRepository
import com.leo.wardrobe.data.prefs.KeystoreApiKeyStore
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
class AppContainer(private val context: Context) {

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
        if (demo) MockWardrobeRepository(loadMockData()) else WardrobeRepositoryImpl(snapshotStore, imageStore)

    /** it-024：数据包导出/导入（演示模式下入口置灰，服务层再兜底拒绝） */
    val packages = com.leo.wardrobe.data.packages.WardrobePackages(repository, snapshotStore, imageStore, demo)

    val prefs: PrefsStore = PrefsStore(context)
    /** 「好久没穿」提醒设置（it-018 阶段C） */
    val recapPrefs: com.leo.wardrobe.data.prefs.RecapPrefsStore =
        com.leo.wardrobe.data.prefs.RecapPrefsStore(context)
    /** 主体抠图引擎（it-016 US-15）：模型打包 assets、bytes 注入（ADR-003）；构造零副作用，
     *  onnxruntime 类与会话在首次去背景时才加载（ADR-002），不进启动路径 */
    val cutoutEngine: com.leo.libs.cutout.CutoutEngine = com.leo.libs.cutout.OnnxCutoutEngine(
        modelBytes = { context.assets.open("u2netp.onnx").use { it.readBytes() } },
    )
    /** it-041 US-41a：BYOK Key 存储——演示模式永不挂真 key（libs/agent 红线②，注入内存实现） */
    val apiKeyStore: com.leo.libs.agent.ApiKeyStore =
        if (demo) com.leo.libs.agent.InMemoryApiKeyStore() else KeystoreApiKeyStore(context)

    /** it-041：模型传输实例工厂——构造零副作用（OkHttp 客户端惰性请求），不进启动路径；
     *  演示模式挂离线 FakeChatModel（红线②：零外呼，工具仍真查演示数据） */
    fun chatModel(preset: com.leo.libs.agent.ProviderPreset): com.leo.libs.agent.ChatModel =
        if (demo) com.leo.wardrobe.data.mock.demoChatModel()
        else com.leo.libs.agent.OkHttpChatModel(preset, apiKeyStore)

    /** it-041 阶段 B：AI 对话会话（tmp→rename 原子写，杀进程可续）与用量记账 */
    val agentSession: com.leo.libs.agent.session.FileSessionStore =
        com.leo.libs.agent.session.FileSessionStore(File(context.filesDir, "agent-sessions"))

    val agentUsage: com.leo.libs.agent.usage.FileUsageLedger =
        com.leo.libs.agent.usage.FileUsageLedger(File(context.filesDir, "agent-usage.json"))

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

    /** 与 APK 内置图片同源的完整演示 JSON；图片由 init 中的解包逻辑统一准备。 */
    private fun loadMockData(): WardrobeData =
        MockWardrobeData.fromJson(
            context.assets.open("mock/wardrobe.json").bufferedReader().use { it.readText() },
        )
}
