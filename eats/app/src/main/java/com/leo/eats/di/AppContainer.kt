package com.leo.eats.di

import android.content.Context
import com.leo.eats.data.image.ImageFileStore
import com.leo.eats.data.json.JsonFileStore
import com.leo.eats.data.prefs.SpinPrefsStore
import com.leo.eats.data.repo.EatsRepositoryImpl
import com.leo.eats.domain.repository.EatsRepository
import com.leo.eats.domain.usecase.BuildCandidates
import com.leo.eats.domain.usecase.SpinWheel
import com.leo.eats.map.MapController
import com.leo.eats.platform.LinkOpener
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

    val jsonStore: JsonFileStore = JsonFileStore(context.filesDir ?: File("."))
    val imageStore: ImageFileStore = ImageFileStore(context)
    val repository: EatsRepository = EatsRepositoryImpl(jsonStore, imageStore)
    val spinPrefs: SpinPrefsStore = SpinPrefsStore(context)
    val buildCandidates: BuildCandidates = BuildCandidates()
    val spinWheel: SpinWheel = SpinWheel()
    val linkOpener: LinkOpener = LinkOpener(context)
    val mapController: MapController = MapController()
}
