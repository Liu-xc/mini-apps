package com.leo.libs.cutout

import kotlinx.coroutines.flow.StateFlow

/**
 * 主体抠图引擎：从任意背景照片中分割显著主体，返回仅更新 alpha 通道的同尺寸 RGBA。
 *
 * 设计约束（wardrobe it-016 / libs/cutout specs/06-decisions.md）：
 * - bytes 进出、不见 android.graphics 与 Context（ADR-002），Bitmap 转换归消费方；
 * - 模型 bytes 由消费方注入（ADR-003，打包 assets、全离线）；
 * - 推理会话惰性创建、空闲自动释放（see [OnnxCutoutEngine]）。
 */
interface CutoutEngine {

    val readiness: StateFlow<CutoutReadiness>

    /** 加载模型并创建推理会话。幂等；[CutoutReadiness.Failed] 后可再次调用重试。 */
    suspend fun prepare()

    /**
     * 抠图：返回与输入同尺寸的 RGBA，仅 alpha 通道被替换为显著主体掩码（RGB 原样保留）。
     * 未就绪时自动补齐 [prepare]（含 [CutoutReadiness.Failed] 重试），失败抛 [CutoutException.InferFailed]。
     */
    suspend fun cutout(rgba: ByteArray, width: Int, height: Int): ByteArray

    /** 立即释放推理会话（空闲自动释放之外的手动入口，如低内存回调）。幂等。 */
    suspend fun release()
}

sealed interface CutoutReadiness {
    /** 未初始化或会话已释放。 */
    data object Idle : CutoutReadiness

    /** 模型加载 / 会话创建中。 */
    data object Preparing : CutoutReadiness

    /** 可推理。 */
    data object Ready : CutoutReadiness

    /** 初始化失败；再次 prepare()/cutout() 即重试。 */
    data class Failed(val cause: Throwable) : CutoutReadiness
}

/** 引擎对外错误。 */
sealed class CutoutException(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
    /** 输入非法：rgba 长度与 width×height×4 不符等。 */
    class BadInput(message: String) : CutoutException(message)

    /** 模型加载或推理失败（cause 为底层异常）。 */
    class InferFailed(cause: Throwable) : CutoutException("cutout infer failed", cause)
}
