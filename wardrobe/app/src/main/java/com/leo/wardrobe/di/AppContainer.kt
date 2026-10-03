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

    /** it-081/D-2：真实数据 wardrobe.json 主/bak 双损坏（已隔离为 .corrupt-*，当前以空数据启动）——
     *  UI 应提示用户抢救 .corrupt 文件；演示模式不适用 */
    val dataLoadFailed: Boolean get() = !demo && repository.let { it is WardrobeRepositoryImpl } &&
        snapshotStore.loadFailed

    /** it-024：数据包导出/导入（演示模式下入口置灰，服务层再兜底拒绝） */
    val packages = com.leo.wardrobe.data.packages.WardrobePackages(repository, snapshotStore, imageStore, demo)

    /** 非敏感的模型偏好同样按真实/Mock 运行数据域隔离。 */
    val prefs: PrefsStore = PrefsStore(context, if (demo) "mock_" else "")
    /** 「好久没穿」提醒设置（it-018 阶段C） */
    val recapPrefs: com.leo.wardrobe.data.prefs.RecapPrefsStore =
        com.leo.wardrobe.data.prefs.RecapPrefsStore(context)
    /** 主体抠图引擎（it-016 US-15）：模型打包 assets、bytes 注入（ADR-003）；构造零副作用，
     *  onnxruntime 类与会话在首次去背景时才加载（ADR-002），不进启动路径 */
    val cutoutEngine: com.leo.libs.cutout.CutoutEngine = com.leo.libs.cutout.OnnxCutoutEngine(
        modelBytes = { context.assets.open("u2netp.onnx").use { it.readBytes() } },
    )
    /** it-050：Mock 测试连接与真实衣橱连接使用完全独立的 Keystore 命名空间。 */
    val apiKeyStore: com.leo.libs.agent.ApiKeyStore =
        KeystoreApiKeyStore(context, if (demo) "mock_agent" else "agent")

    /** Mock 真实模型的私有响应缓存：不进数据包，退出演示不触碰真实数据。 */
    val mockChatCache: com.leo.wardrobe.data.mock.MockChatCache? =
        if (demo) com.leo.wardrobe.data.mock.MockChatCache(File(context.cacheDir, "mock-agent-cache")) else null

    /** it-050：模型传输实例工厂。Mock 有 Key 时真实直连并经私有缓存；无 Key 由 UI/VM 只读拦截。 */
    fun chatModel(preset: com.leo.libs.agent.ProviderPreset): com.leo.libs.agent.ChatModel =
        com.leo.libs.agent.OkHttpChatModel(preset, apiKeyStore).let { model ->
            mockChatCache?.let { cache ->
                com.leo.wardrobe.data.mock.CachedMockChatModel(model, cache) {
                    val key = apiKeyStore.get(preset.id).orEmpty()
                    java.security.MessageDigest.getInstance("SHA-256")
                        .digest(key.toByteArray(Charsets.UTF_8))
                        .joinToString("") { "%02x".format(it) }
                }
            } ?: model
        }

    /** it-041 阶段 B：AI 对话会话（tmp→rename 原子写，杀进程可续）与用量记账 */
    val agentSession: com.leo.libs.agent.session.FileSessionStore =
        com.leo.libs.agent.session.FileSessionStore(
            File(context.filesDir, if (demo) "mock-agent-sessions" else "agent-sessions"),
        )

    /** 多会话目录（it-050），只存摘要与排序信息；消息仍在 agentSession。 */
    val chatSessions = com.leo.wardrobe.data.chat.ChatSessionIndex(
        File(context.filesDir, if (demo) "mock-agent-sessions/index.json" else "agent-sessions/index.json"), agentSession,
    )

    val agentUsage: com.leo.libs.agent.usage.FileUsageLedger =
        com.leo.libs.agent.usage.FileUsageLedger(
            File(context.filesDir, if (demo) "mock-agent-usage.json" else "agent-usage.json"),
        )

    /** it-077：穿搭生图控制器（W7 生成 sheet 与顾问对话流工具共用；演示模式内部走 MockImageModel） */
    val outfitImageGenerator = com.leo.wardrobe.data.gen.OutfitImageGenerator(this)

    val buildPrompt: BuildOutfitPrompt = BuildOutfitPrompt()
    val pickRandom: PickRandomOutfit = PickRandomOutfit()
    val imageComposer: OutfitImageComposer = OutfitImageComposer(imageStore)
    val share: ShareClipboard = ShareClipboard(context)

    init {
        if (demo) unpackMockImages(context)
    }

    /**
     * 内置演示照片解包到 mock 图片目录。
     * it-049 首次引入 revision：升级后刷新同名内置照片（如转透明底的商品图），
     * 但不触碰由演示操作临时写入的 UUID 文件。
     */
    private fun unpackMockImages(context: Context) {
        val dir = File(context.cacheDir, "mock-images").apply { mkdirs() }
        val revisionFile = File(dir, ".asset-revision")
        val refreshBundledAssets = runCatching { revisionFile.readText() != MOCK_ASSET_REVISION }
            .getOrDefault(true)
        context.assets.list("mock").orEmpty().forEach { name ->
            val target = File(dir, name)
            if (refreshBundledAssets || !target.exists()) {
                context.assets.open("mock/$name").use { input ->
                    target.outputStream().use { input.copyTo(it) }
                }
            }
        }
        if (refreshBundledAssets) revisionFile.writeText(MOCK_ASSET_REVISION)
    }

    /** 与 APK 内置图片同源的完整演示 JSON；图片由 init 中的解包逻辑统一准备。 */
    private fun loadMockData(): WardrobeData =
        MockWardrobeData.fromJson(
            context.assets.open("mock/wardrobe.json").bufferedReader().use { it.readText() },
        )

    private companion object {
        const val MOCK_ASSET_REVISION = "it-078"  // it-078：演示生图固定回放效果图样张
    }
}
