package com.leo.libs.agent.internal

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response

/**
 * 挂起等响应，协程取消即刻 [Call.cancel]（it-081/A-3+O-4，收敛自生图两适配器的逐字重复实现）：
 * 阻塞式 execute 在连接/DNS/响应头阶段不响应取消——用户取消后请求仍会在后台跑完并计费。
 */
internal suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            cont.resume(response)
        }

        override fun onFailure(call: Call, e: IOException) {
            cont.resumeWithException(e)
        }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}
