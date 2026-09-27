import Foundation

/// 邸报 LLM 润色（M3 可选项，默认关闭）。
/// 密钥存 `llm.key`（0600，随 appSupport），不进 config.json；任何失败静默回退规则版战报。
enum GazettePolisher {
    static var keyFileURL: URL { DirSupport.appSupport.appendingPathComponent("llm.key") }

    static func readKey() -> String {
        guard let data = try? Data(contentsOf: keyFileURL),
              let key = String(data: data, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines),
              !key.isEmpty else { return "" }
        return key
    }

    /// 返回润色后的战报；nil = 不润色（未启用/无密钥/调用失败），调用方保持规则版。
    static func polish(markdown: String, cfg: PosthouseConfig, key: String? = nil, timeout: TimeInterval = 30) -> String? {
        guard cfg.gazetteLLMPolish else { return nil }
        let resolvedKey = key ?? readKey()
        guard !resolvedKey.isEmpty else {
            PLog.warn("LLM 润色已启用但 \(keyFileURL.path) 无密钥，回退规则版")
            return nil
        }

        let url = cfg.gazetteLLMBaseUrl.hasSuffix("/")
            ? cfg.gazetteLLMBaseUrl + "chat/completions"
            : cfg.gazetteLLMBaseUrl + "/chat/completions"

        let body: [String: Any] = [
            "model": cfg.gazetteLLMModel,
            "temperature": 0.6,
            "messages": [
                ["role": "system", "content": "你是中文技术编辑。把给定的每日 git 战报润色得更有味道，"
                    + "但必须原样保留一级标题、表格与成就条目结构，不新增事实，不删数据。只输出润色后的 Markdown。"],
                ["role": "user", "content": markdown]
            ]
        ]
        guard let bodyData = try? JSONSerialization.data(withJSONObject: body) else { return nil }

        let p = Process()
        p.executableURL = URL(fileURLWithPath: "/usr/bin/curl")
        p.arguments = [
            "-sS", "--max-time", String(Int(timeout)),
            "-X", "POST", url,
            "-H", "Content-Type: application/json",
            "-H", "Authorization: Bearer \(resolvedKey)",
            "--data-binary", "@-"
        ]
        let inPipe = Pipe()
        p.standardInput = inPipe
        let outPipe = Pipe()
        p.standardOutput = outPipe
        p.standardError = Pipe()
        do {
            try p.run()
            inPipe.fileHandleForWriting.write(bodyData)
            try? inPipe.fileHandleForWriting.close()
        } catch {
            PLog.warn("LLM 润色启动失败: \(error)")
            return nil
        }

        let data = outPipe.fileHandleForReading.readDataToEndOfFile()
        let deadline = Date().addingTimeInterval(timeout + 5)
        while p.isRunning && Date() < deadline { Thread.sleep(forTimeInterval: 0.05) }
        p.terminate()

        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let choices = json["choices"] as? [[String: Any]],
              let first = choices.first,
              let message = first["message"] as? [String: Any],
              let content = message["content"] as? String,
              !content.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        else {
            PLog.warn("LLM 润色响应不可用，回退规则版：\(String(data: data, encoding: .utf8)?.prefix(200) ?? "")")
            return nil
        }
        return content
    }
}
