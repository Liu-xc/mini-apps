package com.leo.darkroom

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.leo.darkroom.card.CardSpec
import com.leo.darkroom.card.FrameStyle
import com.leo.darkroom.card.GrainNoise
import com.leo.darkroom.card.PhotoCardPainter
import com.leo.darkroom.card.PhotoLook
import com.leo.darkroom.card.ShareFormat
import com.leo.darkroom.card.TitleFontStyle
import com.leo.darkroom.card.TitleSizeOption
import com.leo.darkroom.data.PhotoRepository
import com.leo.darkroom.data.PrefsStore
import com.leo.darkroom.develop.DevelopClock
import com.leo.darkroom.develop.DevelopMode
import com.leo.darkroom.develop.DevelopSpeed
import com.leo.darkroom.export.ExportPlan
import com.leo.darkroom.export.ShareHelper
import com.leo.darkroom.export.VideoExporter
import com.leo.darkroom.platform.Haptics
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/**
 * 应用状态机（it-001）：选图 → 显影台 → 成片页 + 设置。
 * 显影进度由 [DevelopClock] 确定性驱动，帧循环挂在 vsync（withFrameNanos）上。
 */
class DarkroomViewModel(application: Application) : AndroidViewModel(application) {

    val prefs = PrefsStore(application)
    private val photos = PhotoRepository(application)
    private val albums = com.leo.darkroom.data.AlbumRepository(application)
    private val exporter = VideoExporter()

    val grain: Bitmap by lazy { GrainNoise.bitmap() }

    enum class Screen { PICK, DEVELOP, RESULT, SETTINGS, PAGER }
    enum class SavedKind { NONE, IMAGE, VIDEO }

    data class UiState(
        val screen: Screen = Screen.PICK,
        val photo: Bitmap? = null,
        val photoLabel: String = "",
        val spec: CardSpec = CardSpec(dateText = CardSpec.today()),
        val progress: Float = 0f,
        val playing: Boolean = false,
        val ejecting: Boolean = false,
        val shakeHint: Boolean = false,
        val speed: DevelopSpeed = DevelopSpeed.STANDARD,
        val shakeEnabled: Boolean = true,
        /** 当前显影模式（it-007 US-14；W1 选择，会话内不变） */
        val mode: DevelopMode = DevelopMode.POLAROID,
        val loadingPhoto: Boolean = false,
        val exporting: Boolean = false,
        val exportProgress: Float = 0f,
        val exportFormat: ShareFormat = ShareFormat.FEED,
        val photoLook: PhotoLook = PhotoLook.ORIGINAL,
        val message: String? = null,
        val savedImageUri: Uri? = null,
        val savedVideoUri: Uri? = null,
        val lastSavedKind: SavedKind = SavedKind.NONE,
        val error: String? = null,
        // —— 沉浸相册（it-011 US-18）——
        /** null = 尚未请求过权限 */
        val albumGranted: Boolean? = null,
        val album: List<com.leo.darkroom.data.AlbumPhoto> = emptyList(),
        val albumLoading: Boolean = false,
        /** 本次启动内已显影过的相册图 id（翻回不重播，重播=手动移出） */
        val playedIds: Set<Long> = emptySet(),
        /** it-011 收尾：沉浸相册的会话级退出标记（退出回选图首页，可再进；不持久化） */
        val galleryDismissed: Boolean = false,
        /** it-012：画册模式（PAGER）的起始页（网格点击进来定） */
        val albumInitialPage: Int = 0,
        /** it-012：成片返回栈——从画册进的回画册，否则回相册网格 */
        val resultFromPager: Boolean = false,
        /** it-011 收尾：成片页双态——false=成片查看（默认，仅关键操作），true=编辑（全部细项） */
        val resultEditing: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    // it-003 O4：每次显影按所选档重建（原实现固定 STANDARD，慢洗/快显不生效）
    private var clock = DevelopClock(DevelopSpeed.STANDARD.durationMs)
    private var loopJob: Job? = null
    private var fixedJob: Job? = null
    private var cameraUri: Uri? = null

    /** 用户是否已在本次进程内选过显影模式（挡启动期 DataStore 回填） */
    private var modeChosen = false

    /** 用户是否已改过创作选项（脚注/相纸/字体——it-010，同 modeChosen 语义） */
    private var optionsChosen = false

    init {
        viewModelScope.launch {
            prefs.speed.collect { speed ->
                _state.update { it.copy(speed = speed) }
            }
        }
        viewModelScope.launch {
            prefs.shakeEnabled.collect { v -> _state.update { it.copy(shakeEnabled = v) } }
        }
        viewModelScope.launch {
            // 模式只在启动时读一次（it-007）：W1 的 setMode 是即时真源。
            // DataStore 首次读取可能晚于用户点击，晚到的旧快照会把刚选的模式冲回去，
            // 所以用户一旦选过就不再接受启动期的回填。
            prefs.mode.first().let { m ->
                _state.update { s ->
                    if (!modeChosen && s.screen == Screen.PICK) s.copy(mode = m) else s
                }
            }
        }
        viewModelScope.launch {
            // 创作选项默认值（it-010）只在启动时读一次，与模式同语义：
            // W3 的 setter 是即时真源，晚到的旧快照不许冲掉用户刚选的值
            prefs.footerDefault.first().let { v ->
                _state.update { s ->
                    if (!optionsChosen && s.screen == Screen.PICK) s.copy(spec = s.spec.copy(footer = v)) else s
                }
            }
            prefs.frameDefault.first().let { v ->
                _state.update { s ->
                    if (!optionsChosen && s.screen == Screen.PICK) s.copy(spec = s.spec.copy(frame = v)) else s
                }
            }
            prefs.titleFontDefault.first().let { v ->
                _state.update { s ->
                    if (!optionsChosen && s.screen == Screen.PICK) s.copy(spec = s.spec.copy(titleFont = v)) else s
                }
            }
            prefs.titleSizeDefault.first().let { v ->
                _state.update { s ->
                    if (!optionsChosen && s.screen == Screen.PICK) s.copy(spec = s.spec.copy(titleSize = v)) else s
                }
            }
        }
    }

    // —— 沉浸相册（it-011 US-18）——

    private val galleryPlayback = com.leo.darkroom.develop.GalleryPlayback()

    /** 权限结果回填：授予即加载首页；拒绝留在回退态（原 Photo Picker 流完整可用） */
    fun onAlbumPermissionResult(granted: Boolean) {
        _state.update { it.copy(albumGranted = granted) }
        if (granted) loadAlbum(initial = true)
    }

    fun loadAlbum(initial: Boolean = false) {
        if (_state.value.albumLoading) return
        viewModelScope.launch {
            _state.update { it.copy(albumLoading = true) }
            val offset = if (initial) 0 else _state.value.album.size
            val page = albums.recent(offset)
            _state.update {
                val merged = if (initial) page else (it.album + page).distinctBy { p -> p.id }
                it.copy(album = merged, albumLoading = false)
            }
        }
    }

    fun loadMoreAlbum() {
        if (_state.value.albumGranted == true && _state.value.album.isNotEmpty()) loadAlbum(false)
    }

    suspend fun albumThumbnail(
        photo: com.leo.darkroom.data.AlbumPhoto,
        size: Int = 1440,
    ): android.graphics.Bitmap? =
        if (size <= 512) albums.gridThumb(photo) else albums.pageImage(photo, size)

    /** it-012：网格点缩略图进画册模式，从该页起翻 */
    fun openAlbumPager(index: Int) {
        _state.update { it.copy(albumInitialPage = index, screen = Screen.PAGER) }
    }

    fun markGalleryPlayed(id: Long) {
        galleryPlayback.markPlayed(id)
        _state.update { it.copy(playedIds = galleryPlayback.snapshot()) }
    }

    /** 手动重播当前卡（it-011）：移出已播集合，卡面即重新出纸显影 */
    fun replayGalleryCard(id: Long) {
        galleryPlayback.replay(id)
        _state.update { it.copy(playedIds = galleryPlayback.snapshot()) }
    }

    /** 沉浸相册点开成片：动画已在 W1 播完，直接进 W3（it-011 收尾：默认成片查看态） */
    fun openGalleryResult(
        photo: com.leo.darkroom.data.AlbumPhoto,
        bitmap: android.graphics.Bitmap,
        toEdit: Boolean = false,
    ) {
        fixedJob?.cancel()
        loopJob?.cancel()
        _state.update {
            it.copy(
                screen = Screen.RESULT,
                photo = bitmap,
                photoLabel = "相册",
                spec = CardSpec(
                    dateText = photo.dateText.ifBlank { CardSpec.today() },
                    footer = it.spec.footer,
                    frame = it.spec.frame,
                    titleFont = it.spec.titleFont,
                    titleSize = it.spec.titleSize,
                ),
                photoLook = PhotoLook.ORIGINAL,
                progress = 1f,
                playing = false,
                ejecting = false,
                shakeHint = false,
                exporting = false,
                savedImageUri = null,
                savedVideoUri = null,
                lastSavedKind = SavedKind.NONE,
                error = null,
                message = null,
                resultEditing = toEdit,
                resultFromPager = true,
            )
        }
    }

    // —— 成片页双态（it-011 收尾：显影完成默认成片查看，编辑是显式动作）——

    fun openResultEdit() = _state.update { it.copy(resultEditing = true) }

    fun closeResultEdit() = _state.update { it.copy(resultEditing = false) }

    // —— 沉浸相册进出（it-011 收尾：会话级退出/再进）——

    fun exitGallery() = _state.update { it.copy(galleryDismissed = true) }

    fun enterGallery() {
        _state.update { it.copy(galleryDismissed = false) }
        if (_state.value.album.isEmpty()) loadAlbum(initial = true)
    }

    // —— 选图 ——
    fun onPhotoPicked(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            _state.update { it.copy(loadingPhoto = true) }
            val bmp = photos.decode(uri)
            _state.update { it.copy(loadingPhoto = false) }
            if (bmp != null) startSession(bmp, "相册")
            else _state.update { it.copy(error = "这张图读不出来，换一张试试") }
        }
    }

    fun onSamplePicked(index: Int) {
        viewModelScope.launch {
            startSession(photos.sample(index), com.leo.darkroom.card.SampleArt.titles[index])
        }
    }

    fun prepareCamera(): Uri {
        val uri = ShareHelper.newCameraUri(getApplication())
        cameraUri = uri
        return uri
    }

    fun onCameraResult(ok: Boolean) {
        val uri = cameraUri ?: return
        if (!ok) return
        viewModelScope.launch {
            _state.update { it.copy(loadingPhoto = true) }
            val bmp = photos.decode(uri)
            _state.update { it.copy(loadingPhoto = false) }
            if (bmp != null) startSession(bmp, "刚拍的")
            else _state.update { it.copy(error = "照片读取失败") }
        }
    }

    private fun startSession(bmp: Bitmap, label: String) {
        fixedJob?.cancel()
        loopJob?.cancel()
        // it-003 O4：会话开始时按当前档位取时长（AC1：下一次显影生效）
        clock = DevelopClock(_state.value.speed.durationMs)
        _state.update {
            it.copy(
                screen = Screen.DEVELOP,
                photo = bmp,
                photoLabel = label,
                spec = CardSpec(
                    dateText = CardSpec.today(),
                    // 创作选项跨会话携带（it-010）：上一次的脚注/相纸/字体/字号即本次起点
                    footer = it.spec.footer,
                    frame = it.spec.frame,
                    titleFont = it.spec.titleFont,
                    titleSize = it.spec.titleSize,
                ),
                photoLook = PhotoLook.ORIGINAL,
                progress = 0f,
                playing = false,
                ejecting = true,
                shakeHint = true,
                exporting = false,
                resultEditing = false,
                resultFromPager = false,
                exportProgress = 0f,
                error = null,
            )
        }
    }

    // —— 显影循环 ——

    /** 出纸动画结束（UI 调用）：开始显影 */
    fun onEjectDone() {
        _state.update { it.copy(ejecting = false) }
        if (!_state.value.playing && clock.progress < 1f) play()
        viewModelScope.launch {
            delay(4_000)
            _state.update { it.copy(shakeHint = false) }
        }
    }

    fun play() {
        if (clock.isFixed) return
        _state.update { it.copy(playing = true) }
        startLoop()
    }

    fun pause() {
        _state.update { it.copy(playing = false) }
    }

    /** 药水条拖动 */
    fun seek(fraction: Float) {
        clock.seek(fraction)
        _state.update { it.copy(progress = clock.progress) }
        if (clock.isFixed) onFixedReached()
    }

    /** 甩一甩：单次推进一段进度 */
    fun boost(fraction: Float = 0.10f) {
        if (_state.value.screen != Screen.DEVELOP) return
        if (clock.isFixed) return
        clock.boost(fraction)
        _state.update { it.copy(progress = clock.progress, shakeHint = false) }
        if (clock.isFixed) onFixedReached()
    }

    /** 减弱动态：直接跳到定影 */
    fun skipDevelop() {
        if (clock.isFixed) return
        clock.seek(1f)
        _state.update { it.copy(progress = 1f) }
        onFixedReached()
    }

    private fun startLoop() {
        loopJob?.cancel()
        loopJob = viewModelScope.launch {
            // viewModelScope 无 MonotonicFrameClock（withFrameNanos 不可用），
            // 16ms 定步 + 单调时钟已满足显影时间线精度
            var last = System.nanoTime()
            while (isActive) {
                delay(16)
                val now = System.nanoTime()
                val dtMs = ((now - last) / 1_000_000L).coerceIn(0L, 100L)
                last = now
                if (_state.value.playing) {
                    clock.tick(dtMs)
                    _state.update { it.copy(progress = clock.progress) }
                    if (clock.isFixed) {
                        onFixedReached()
                        break
                    }
                }
            }
        }
    }

    private fun onFixedReached() {
        if (fixedJob?.isActive == true) return
        _state.update { it.copy(playing = false, progress = 1f) }
        Haptics.confirm(getApplication())
        fixedJob = viewModelScope.launch {
            delay(750)
            _state.update { it.copy(screen = Screen.RESULT, resultEditing = false) }
            loopJob?.cancel()
        }
    }

    // —— 成片编辑 ——

    fun setTitle(title: String) {
        _state.update { it.copy(spec = it.spec.copy(title = title.take(24))) }
    }

    fun setDate(date: String) {
        _state.update { it.copy(spec = it.spec.copy(dateText = date.take(11))) }
    }

    // —— 创作选项（it-010 US-16/17）：会话即时生效，并作为下一次会话默认持久化 ——

    fun setFooter(text: String) {
        optionsChosen = true
        _state.update { it.copy(spec = it.spec.copy(footer = text.take(24))) }
        viewModelScope.launch { prefs.setFooterDefault(text.take(24)) }
    }

    fun setFrame(frame: FrameStyle) {
        optionsChosen = true
        _state.update {
            if (it.exporting) it else it.copy(
                spec = it.spec.copy(frame = frame),
                savedImageUri = null,
                savedVideoUri = null,
                lastSavedKind = SavedKind.NONE,
            )
        }
        viewModelScope.launch { prefs.setFrameDefault(frame) }
    }

    fun setTitleFont(font: TitleFontStyle) {
        optionsChosen = true
        _state.update { it.copy(spec = it.spec.copy(titleFont = font)) }
        viewModelScope.launch { prefs.setTitleFontDefault(font) }
    }

    fun setTitleSize(size: TitleSizeOption) {
        optionsChosen = true
        _state.update { it.copy(spec = it.spec.copy(titleSize = size)) }
        viewModelScope.launch { prefs.setTitleSizeDefault(size) }
    }

    fun setExportFormat(format: ShareFormat) {
        _state.update {
            if (it.exportFormat == format) it
            else it.copy(
                exportFormat = format,
                savedImageUri = null,
                savedVideoUri = null,
                lastSavedKind = SavedKind.NONE,
            )
        }
    }

    fun setPhotoLook(look: PhotoLook) {
        _state.update {
            if (it.exporting || it.photoLook == look) it
            else it.copy(
                photoLook = look,
                savedImageUri = null,
                savedVideoUri = null,
                lastSavedKind = SavedKind.NONE,
            )
        }
    }

    // —— 设置 ——

    fun setSpeed(speed: DevelopSpeed) = viewModelScope.launch { prefs.setSpeed(speed) }

    /** 显影模式（W1 选择，it-007 US-14）：先落会话态再写偏好，避免选完立即开洗时读到旧值 */
    fun setMode(mode: DevelopMode) {
        modeChosen = true
        _state.update { it.copy(mode = mode) }
        viewModelScope.launch { prefs.setMode(mode) }
    }

    fun setShakeEnabled(enabled: Boolean) = viewModelScope.launch { prefs.setShakeEnabled(enabled) }

    // —— 导出 ——

    fun exportImage(share: Boolean = false) {
        val s = _state.value
        val photo = s.photo ?: return
        if (s.exporting) return
        viewModelScope.launch {
            _state.update { it.copy(exporting = true, exportProgress = 0f) }
            runCatching {
                val bmp = withContext(Dispatchers.Default) {
                    PhotoCardPainter.renderShareFrame(
                        photo, s.spec, s.mode, grain, s.exportFormat, look = s.photoLook,
                    )
                }
                withContext(Dispatchers.IO) {
                    val uri = ShareHelper.saveImage(getApplication(), bmp, "显影_${s.spec.dateText.replace(' ', '-')}")
                    if (share) ShareHelper.share(getApplication(), uri, "image/jpeg")
                    uri
                }
            }.onSuccess { uri ->
                Haptics.confirm(getApplication())
                _state.update {
                    it.copy(
                        exporting = false,
                        exportProgress = 1f,
                        savedImageUri = uri,
                        lastSavedKind = SavedKind.IMAGE,
                        message = if (share) null else "已存入相册 · 显影",
                    )
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(exporting = false, error = e.message ?: "保存失败")
                }
                Haptics.warn(getApplication())
            }
        }
    }

    fun exportVideo() {
        val s = _state.value
        val photo = s.photo ?: return
        if (s.exporting) return
        viewModelScope.launch {
            _state.update { it.copy(exporting = true, exportProgress = 0f) }
            runCatching {
                val outFile = File(getApplication<Application>().cacheDir, "export_${System.currentTimeMillis()}.mp4")
                val plan = ExportPlan(developMs = s.speed.durationMs, leadMs = s.mode.leadMs)
                withContext(Dispatchers.IO) {
                    exporter.export(
                        VideoExporter.Params(
                            photo = photo,
                            spec = s.spec,
                            mode = s.mode,
                            plan = plan,
                            format = s.exportFormat,
                            look = s.photoLook,
                            outFile = outFile,
                        ),
                    ) { p -> _state.update { it.copy(exportProgress = p) } }
                }
                withContext(Dispatchers.IO) {
                    val uri = ShareHelper.saveVideo(
                        getApplication(), outFile, "显影_${s.spec.dateText.replace(' ', '-')}.mp4",
                    )
                    outFile.delete()
                    uri
                }
            }.onSuccess { uri ->
                Haptics.confirm(getApplication())
                _state.update {
                    it.copy(
                        exporting = false,
                        exportProgress = 1f,
                        savedVideoUri = uri,
                        lastSavedKind = SavedKind.VIDEO,
                        message = "视频已存入相册 · 显影",
                    )
                }
            }.onFailure { e ->
                _state.update { it.copy(exporting = false, error = e.message ?: "视频导出失败") }
                Haptics.warn(getApplication())
            }
        }
    }

    fun shareSavedImage() {
        val uri = _state.value.savedImageUri ?: return
        ShareHelper.share(getApplication(), uri, "image/jpeg")
    }

    fun shareSavedVideo() {
        val uri = _state.value.savedVideoUri ?: return
        ShareHelper.share(getApplication(), uri, "video/mp4")
    }

    // —— 导航 ——

    fun backToPick() {
        // it-012 交互动线：画册的返回回网格；成片的返回——从画册进的回画册，否则回网格
        fixedJob?.cancel()
        loopJob?.cancel()
        clock.reset()
        _state.update { s ->
            when (s.screen) {
                Screen.PAGER -> s.copy(screen = Screen.PICK)
                Screen.RESULT -> if (s.resultFromPager) s.copy(screen = Screen.PAGER) else s.copy(screen = Screen.PICK)
                else -> s.copy(screen = Screen.PICK)
            }
        }
    }

    fun openSettings() = _state.update { it.copy(screen = Screen.SETTINGS) }

    fun closeSettings() = _state.update { it.copy(screen = Screen.PICK) }

    fun clearMessage() = _state.update { it.copy(message = null) }

    fun clearError() = _state.update { it.copy(error = null) }

    fun consumeUri(which: Int) = _state.update {
        if (which == 0) it.copy(savedImageUri = null) else it.copy(savedVideoUri = null)
    }

    override fun onCleared() {
        loopJob?.cancel()
        fixedJob?.cancel()
        super.onCleared()
    }
}
