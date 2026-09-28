import AppKit
import Foundation
import WebKit

/// 续期失败分类：`sessionDead = true` 表示账号会话已死（停自动重试，等用户登录），
/// false = 网络/超时类暂态（保持 active，下个刷新周期再试）——AC2/AC3 分界
struct SessionRenewError: LocalizedError {
    let message: String
    var sessionDead = false
    var errorDescription: String? { message }
}

/// it-004：MiMo 登录态静默续期（M1 spike 机制的生产实现）。
/// serviceToken 过期时用**持久化 WKWebsiteDataStore 里的账号会话**离屏走一遍
/// SSO（loginUrl → sts 回调）——账号会话存活则零交互种下新 Cookie，提取后回写凭证文件。
@MainActor
final class MimoSessionRenewer: NSObject, WKNavigationDelegate {
    private static let timeoutNanoseconds: UInt64 = 30_000_000_000
    /// 落到账号登录页后的宽限：正常 SSO 是 302 直通，JS 跳转留余量；停留超时 = 会话失效
    private static let loginPageGrace: TimeInterval = 5

    private var window: NSWindow?
    private var webView: WKWebView?
    private var attempt: Attempt?
    private var graceTimer: Timer?
    private var inFlight: Task<String, Error>?

    private final class Attempt {
        var continuation: CheckedContinuation<String, Error>?
        var finished = false
    }

    /// 单飞入口：并发调用共享同一次续期（refreshAll 与手动刷新可能撞车）
    func renew(startURL: URL) async throws -> String {
        if let running = inFlight {
            return try await running.value
        }
        let task = Task { try await self.perform(startURL: startURL) }
        inFlight = task
        defer { inFlight = nil }
        return try await task.value
    }

    /// 清除 App 内全部网站数据（登录会话随凭证清除，AC4）
    static func clearWebsiteData() {
        let store = WKWebsiteDataStore.default()
        store.removeData(ofTypes: WKWebsiteDataStore.allWebsiteDataTypes(), modifiedSince: .distantPast) {
            NSLog("[island] 已清除 App 内网站数据（登录会话）")
        }
    }

    /// M1 spike 调试钩子（`GLM_ISLAND_SPIKE_EXPIRE_MIMO=1`，start 前执行）：
    /// ① credentials.json 的 serviceToken 置无效（触发 401——fetch 读的是凭证不是 WebView 存储）；
    /// ② 剥离 WebView 会话里的 serviceToken（会话其余 Cookie 保留，逼续期走账号会话重 mint）；
    /// ③ 输出会话 Cookie 清单（名字/域/有效期，不打值；exp=0 = 会话 Cookie 不落盘）——AC1/AC2 验证
    static func spikeExpireServiceToken() async {
        let cookie = CredentialStore.load(account: KeychainAccount.mimoCookie)
        if cookie.contains("api-platform_serviceToken=") {
            let corrupted = cookie.replacingOccurrences(
                of: "api-platform_serviceToken=[^;]*",
                with: "api-platform_serviceToken=SPIKE-EXPIRED",
                options: .regularExpression
            )
            CredentialStore.save(corrupted, account: KeychainAccount.mimoCookie)
            NSLog("[island][spike] 凭证 serviceToken 已置无效（触发 401）")
        }
        let store = WKWebsiteDataStore.default().httpCookieStore
        let cookies = await withCheckedContinuation { (continuation: CheckedContinuation<[HTTPCookie], Never>) in
            store.getAllCookies { continuation.resume(returning: $0) }
        }
        let brief = cookies.map {
            "\($0.name)@\($0.domain) exp=\($0.expiresDate.map { Int($0.timeIntervalSince1970) } ?? 0)"
        }.joined(separator: ", ")
        NSLog("[island][spike] WebView 会话 Cookie \(cookies.count) 个: \(brief)")
        for cookie in cookies where cookie.name == "api-platform_serviceToken" {
            await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
                store.delete(cookie) { continuation.resume() }
            }
            NSLog("[island][spike] 已剥离 WebView serviceToken")
        }
    }

    // MARK: - 续期主流程

    private func perform(startURL: URL) async throws -> String {
        let webView = ensureWebView()
        let attempt = Attempt()
        self.attempt = attempt
        graceTimer?.invalidate()
        graceTimer = nil

        return try await withCheckedThrowingContinuation { continuation in
            attempt.continuation = continuation
            Task { @MainActor [weak self] in
                try? await Task.sleep(nanoseconds: Self.timeoutNanoseconds)
                guard let self, self.attempt === attempt, !attempt.finished else { return }
                self.finish(.failure(SessionRenewError(message: "自动续期超时，请稍后重试或在设置重新登录")))
            }
            webView.load(URLRequest(url: startURL))
        }
    }

    private func finish(_ result: Result<String, Error>) {
        graceTimer?.invalidate()
        graceTimer = nil
        guard let attempt, !attempt.finished, let continuation = attempt.continuation else { return }
        attempt.finished = true
        attempt.continuation = nil
        self.attempt = nil
        continuation.resume(with: result)
    }

    private func ensureWebView() -> WKWebView {
        if let webView { return webView }
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .default()   // 持久化：与登录窗共享同一账号会话
        let web = WKWebView(frame: NSRect(x: 0, y: 0, width: 520, height: 680), configuration: config)
        web.navigationDelegate = self
        let win = NSWindow(
            contentRect: NSRect(x: 0, y: 0, width: 520, height: 680),
            styleMask: [.borderless],
            backing: .buffered,
            defer: false
        )
        win.isReleasedWhenClosed = false
        win.contentView = web
        win.backgroundColor = .clear
        win.isOpaque = false
        win.ignoresMouseEvents = true
        win.alphaValue = 0   // 完全不可见；不 orderFront——静默运行
        window = win
        webView = web
        return web
    }

    private func extractHeader(_ webView: WKWebView) async -> String {
        let store = webView.configuration.websiteDataStore.httpCookieStore
        let cookies = await withCheckedContinuation { (continuation: CheckedContinuation<[HTTPCookie], Never>) in
            store.getAllCookies { continuation.resume(returning: $0) }
        }
        return MimoSSO.cookieHeader(from: cookies)
    }

    // MARK: - 导航判定（delegate 回调主线程 → assumeIsolated 回主 Actor）

    private func handleDidFinish(url: URL?) {
        guard let attempt, !attempt.finished else { return }
        if MimoSSO.isPlatformURL(url) {
            Task { @MainActor [weak self] in
                guard let self, let current = self.attempt, !current.finished,
                      let webView = self.webView else { return }
                let header = await self.extractHeader(webView)
                guard MimoSSO.hasServiceToken(header) else {
                    self.finish(.failure(SessionRenewError(message: "自动续期未取得登录态，稍后重试")))
                    return
                }
                self.finish(.success(header))
            }
        } else if MimoSSO.isAccountLoginURL(url) {
            // 会话存活应 302 直通平台；停在登录页超过宽限期 = 账号会话失效（AC3）
            graceTimer?.invalidate()
            graceTimer = Timer.scheduledTimer(withTimeInterval: Self.loginPageGrace, repeats: false) { [weak self] _ in
                Task { @MainActor [weak self] in
                    guard let self, let current = self.attempt, !current.finished else { return }
                    guard MimoSSO.isAccountLoginURL(self.webView?.url) else { return }   // 宽限内跳走则放行
                    self.finish(.failure(SessionRenewError(
                        message: "MiMo 登录已失效，请在设置重新登录",
                        sessionDead: true
                    )))
                }
            }
        }
        // 其余宿主（about:blank 中间页等）不处理，等下一次导航
    }

    private func handleNavigationFail(_ error: Error) {
        guard attempt != nil else { return }
        let nsError = error as NSError
        if nsError.domain == NSURLErrorDomain && nsError.code == NSURLErrorCancelled {
            return   // 重定向取消是常态
        }
        finish(.failure(SessionRenewError(message: "自动续期网络失败：\(error.localizedDescription)")))
    }

    // MARK: - WKNavigationDelegate（nonisolated → 主线程 hop）

    nonisolated func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        MainActor.assumeIsolated { self.handleDidFinish(url: webView.url) }
    }

    nonisolated func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        MainActor.assumeIsolated { self.handleNavigationFail(error) }
    }

    nonisolated func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        MainActor.assumeIsolated { self.handleNavigationFail(error) }
    }
}
