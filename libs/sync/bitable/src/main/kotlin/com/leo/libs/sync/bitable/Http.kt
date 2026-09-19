package com.leo.libs.sync.bitable

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** HTTP 抽象：可注入 Fake 做单测；OkHttp 为默认实现 */
data class HttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
    val formParts: List<FormPart>? = null,
)

data class FormPart(
    val name: String,
    val value: String? = null,
    val fileName: String? = null,
    val bytes: ByteArray? = null,
    val contentType: String? = null,
)

data class HttpResponse(
    val status: Int,
    val headers: Map<String, List<String>>,
    val body: ByteArray,
) {
    val bodyText: String get() = body.decodeToString()

    fun header(name: String): String? = headers[name]?.firstOrNull() ?: headers[name.lowercase()]?.firstOrNull()
}

interface HttpTransport {
    suspend fun execute(request: HttpRequest): HttpResponse
}

class OkHttpTransport(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build(),
) : HttpTransport {

    override suspend fun execute(request: HttpRequest): HttpResponse = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(request.url)
        request.headers.forEach { (k, v) -> builder.header(k, v) }
        val body: RequestBody? = when {
            request.formParts != null -> buildMultipart(request.formParts)
            request.body != null -> request.body.toRequestBody("application/json; charset=utf-8".toMediaType())
            else -> null
        }
        builder.method(request.method, body)
        client.newCall(builder.build()).execute().use { resp ->
            HttpResponse(
                status = resp.code,
                headers = resp.headers.toMultimap(),
                body = resp.body?.bytes() ?: ByteArray(0),
            )
        }
    }

    private fun buildMultipart(parts: List<FormPart>): MultipartBody {
        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
        for (p in parts) {
            when {
                p.bytes != null -> {
                    val media = (p.contentType ?: "application/octet-stream").toMediaType()
                    multipart.addFormDataPart(
                        p.name,
                        p.fileName ?: p.name,
                        p.bytes.toRequestBody(media),
                    )
                }
                else -> multipart.addFormDataPart(p.name, p.value ?: "")
            }
        }
        return multipart.build()
    }
}
