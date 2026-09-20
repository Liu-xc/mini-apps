package com.leo.libs.sync.bitable


/** 可脚本化的假传输：记录全部请求，按 handler 返回 */
class FakeTransport(private val handler: (HttpRequest) -> HttpResponse) : HttpTransport {
    val requests = mutableListOf<HttpRequest>()
    override suspend fun execute(request: HttpRequest): HttpResponse {
        requests += request
        return handler(request)
    }
}

fun ok(data: String = "{}", vararg headers: Pair<String, String>): HttpResponse =
    HttpResponse(
        status = 200,
        headers = headers.associate { (k, v) -> k to listOf(v) } +
            ("Content-Type" to listOf("application/json")),
        body = """{"code":0,"msg":"ok","data":$data}""".toByteArray(),
    )

/** token 等顶层返回的端点（无 data 包装） */
fun raw(body: String, status: Int = 200): HttpResponse =
    HttpResponse(status = status, headers = emptyMap(), body = body.toByteArray())

fun tokenOk(token: String = "t-1", expire: Long = 7200): HttpResponse =
    raw("""{"code":0,"tenant_access_token":"$token","expire":$expire}""")

fun err(status: Int, code: Int, msg: String = "err", vararg headers: Pair<String, String>): HttpResponse =
    HttpResponse(
        status = status,
        headers = headers.associate { (k, v) -> k to listOf(v) },
        body = """{"code":$code,"msg":"$msg"}""".toByteArray(),
    )
