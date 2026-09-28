import SwiftUI
import WebKit

/// it-004：设置页「登录小米账号」sheet——持久化 WKWebView 走完整 SSO，
/// 着陆平台并取得 serviceToken 后回调整段 Cookie（调用方落盘 + 标记 active）
struct MimoLoginSheet: View {
    let startURL: URL
    let onCookie: (String) -> Void
    let onCancel: () -> Void

    @State private var statusHint = "正在打开登录页…"

    var body: some View {
        VStack(spacing: 0) {
            MimoLoginWebView(startURL: startURL, onCookie: onCookie) { statusHint = $0 }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            Divider()
            HStack {
                Text(statusHint)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                Spacer()
                Button("取消", action: onCancel)
            }
            .padding(10)
        }
        .frame(width: 520, height: 680)
    }
}

/// WKWebView 封装：与静默续期器共享 `WKWebsiteDataStore.default()`（账号会话同一份）
struct MimoLoginWebView: NSViewRepresentable {
    let startURL: URL
    let onCookie: (String) -> Void
    let onStatus: (String) -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(onCookie: onCookie, onStatus: onStatus)
    }

    func makeNSView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .default()
        let webView = WKWebView(frame: .zero, configuration: config)
        webView.navigationDelegate = context.coordinator
        webView.load(URLRequest(url: startURL))
        return webView
    }

    func updateNSView(_ nsView: WKWebView, context: Context) {}

    final class Coordinator: NSObject, WKNavigationDelegate {
        let onCookie: (String) -> Void
        let onStatus: (String) -> Void

        init(onCookie: @escaping (String) -> Void, onStatus: @escaping (String) -> Void) {
            self.onCookie = onCookie
            self.onStatus = onStatus
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            let url = webView.url
            if MimoSSO.isPlatformURL(url) {
                onStatus("已登录平台，正在保存会话…")
                webView.configuration.websiteDataStore.httpCookieStore.getAllCookies { cookies in
                    let header = MimoSSO.cookieHeader(from: cookies)
                    // it-004 诊断：只打名字/域/有效期（不打值）——expires=0 表示会话 Cookie（不落盘）
                    let brief = cookies.map {
                        "\($0.name)@\($0.domain) exp=\($0.expiresDate.map { Int($0.timeIntervalSince1970) } ?? 0)"
                    }.joined(separator: ", ")
                    NSLog("[island][login] 登录取得 Cookie \(cookies.count) 个: \(brief)")
                    DispatchQueue.main.async {
                        guard MimoSSO.hasServiceToken(header) else {
                            self.onStatus("未取得登录态，请刷新重试")
                            return
                        }
                        self.onCookie(header)
                    }
                }
            } else if MimoSSO.isAccountLoginURL(url) {
                onStatus("请在页面内完成小米账号登录，完成后自动保存")
            }
        }

        func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
            DispatchQueue.main.async { self.onStatus("页面加载失败：\(error.localizedDescription)") }
        }

        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
            DispatchQueue.main.async { self.onStatus("页面加载失败：\(error.localizedDescription)") }
        }
    }
}
