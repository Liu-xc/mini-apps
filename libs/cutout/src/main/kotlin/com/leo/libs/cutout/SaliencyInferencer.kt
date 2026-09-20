package com.leo.libs.cutout

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer

/**
 * 显著性推理薄壳：输入 [U2NetPreprocessor] 产物（1×3×320×320 NCHW），
 * 输出 u2netp 首个输出 d0（320×320 显著性掩码，float）。
 * 引擎对接口编排，便于测试注入假实现（specs/00-architecture.md §6）。
 * 面向 [OnnxCutoutEngine] 的注入缝而公开；消费方通常不直接使用。
 */
interface SaliencyInferencer : AutoCloseable {
    /** @return 320×320 掩码，float 数组长度 102400 */
    fun infer(input: FloatArray): FloatArray
}

/** ai.onnxruntime 实现：动态取输入名（u2netp onnx 导出命名不统一），取首个输出（d0）。 */
internal class OnnxSaliencyInferencer(modelBytes: ByteArray) : SaliencyInferencer {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession = env.createSession(modelBytes)
    private val inputName: String = session.inputNames.iterator().next()

    override fun infer(input: FloatArray): FloatArray {
        require(input.size == 3 * U2NetPreprocessor.INPUT_SIDE * U2NetPreprocessor.INPUT_SIDE) {
            "input size ${input.size}"
        }
        val shape = longArrayOf(
            1,
            3,
            U2NetPreprocessor.INPUT_SIDE.toLong(),
            U2NetPreprocessor.INPUT_SIDE.toLong(),
        )
        OnnxTensor.createTensor(env, FloatBuffer.wrap(input), shape).use { tensor ->
            session.run(mapOf(inputName to tensor)).use { results ->
                val first = results.iterator().next()
                val output = first.value as OnnxTensor
                val buffer = output.floatBuffer
                val out = FloatArray(buffer.remaining())
                buffer.get(out)
                return out
            }
        }
    }

    override fun close() {
        session.close()
    }
}
