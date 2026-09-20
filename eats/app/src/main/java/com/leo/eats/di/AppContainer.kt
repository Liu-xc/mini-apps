package com.leo.eats.di

import android.content.Context
import com.leo.eats.data.image.ImageFileStore
import com.leo.eats.data.mock.DemoMode
import com.leo.eats.data.mock.MockEatsRepository
import com.leo.eats.data.prefs.RecapPrefsStore
import com.leo.eats.data.prefs.SpinPrefsStore
import com.leo.eats.data.repo.EatsRepositoryImpl
import com.leo.eats.domain.model.EatsData
import com.leo.eats.domain.repository.EatsRepository
import com.leo.eats.domain.repository.ImageStore
import com.leo.eats.domain.usecase.BuildCandidates
import com.leo.eats.map.MapController
import com.leo.eats.platform.LinkOpener
import com.leo.eats.platform.RecapSaver
import com.leo.libs.store.FileMediaStore
import com.leo.libs.store.SnapshotStore
import org.osmdroid.config.Configuration
import java.io.File

/** 组合根：手动构造器装配（ADR-004 与 wardrobe 同模式），specs/04-architecture.md */
class AppContainer(context: Context) {

    init {
        // osmdroid 全局配置（ADR-002）：UserAgent + 瓦片缓存放应用私有目录（免存储权限）
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            osmdroidBasePath = File(context.filesDir, "osmdroid")
            osmdroidTileCache = File(File(context.filesDir, "osmdroid"), "tiles")
        }
    }

    /** 演示模式（it-006）：开关在组合根构造时读取，切换经 DemoMode 重启进程生效 */
    private val demo = DemoMode.isEnabled(context)

    /** 演示模式下提醒与外发通道全部停用（it-007 阶段B） */
    val isDemo: Boolean get() = demo

    /** eats.json 持久化（store SDK，ADR-010）；演示模式下不读取不写入 */
    val snapshotStore: SnapshotStore<EatsData> = SnapshotStore(
        dir = context.filesDir,
        fileName = "eats.json",
        serializer = EatsData.serializer(),
        default = { EatsData() },
        versionOf = { it.schemaVersion },
    )

    /** 演示模式下图片指向 cacheDir/mock-images，真实 images/ 不被触碰 */
    val imageStore: ImageStore = ImageFileStore(
        context,
        FileMediaStore(
            if (demo) context.cacheDir else context.filesDir,
            if (demo) "mock-images" else "images",
        ),
    )

    val repository: EatsRepository =
        if (demo) MockEatsRepository() else EatsRepositoryImpl(snapshotStore, imageStore)

    val spinPrefs: SpinPrefsStore = SpinPrefsStore(context)
    val recapPrefs: RecapPrefsStore = RecapPrefsStore(context)
    val recapSaver: RecapSaver = RecapSaver(context)
    val buildCandidates: BuildCandidates = BuildCandidates()
    val linkOpener: LinkOpener = LinkOpener(context)
    val mapController: MapController = MapController()
}
