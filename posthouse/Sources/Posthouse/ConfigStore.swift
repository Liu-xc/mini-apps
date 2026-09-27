import Foundation

struct PosthouseConfig: Codable {
    var scanRoots: [String] = []
    var extraRepos: [String] = []
    var pollIntervalSeconds: Double = 60
    var remoteProbeTimeout: Double = 8
    /// 附加到 git 的 `-c` 参数（推送网络参数），不改写仓库自身配置
    var pushArgs: [String] = ["-c", "http.version=HTTP/1.1", "-c", "http.postBuffer=524288000"]
    var autoPushEnabled: Bool = false
    /// path -> Bool；白名单默认全关，用户手动开启
    var autoPushWhitelist: [String: Bool] = [:]
    var gazetteHour: Int = 22
    var gazetteMinute: Int = 30
    var gazetteOutputDir: String = "/Users/leo/Documents/daily-gazette"
    var notificationsEnabled: Bool = true

    // M3 可选 LLM 润色（默认关；密钥在同目录 llm.key，不入 config）
    var gazetteLLMPolish: Bool = false
    var gazetteLLMBaseUrl: String = "https://open.bigmodel.cn/api/paas/v4"
    var gazetteLLMModel: String = "glm-4-flash"

    func isWhitelisted(_ repoPath: String) -> Bool {
        autoPushEnabled && (autoPushWhitelist[repoPath] == true)
    }

    static func defaultConfig() -> PosthouseConfig {
        var c = PosthouseConfig()
        let home = FileManager.default.homeDirectoryForCurrentUser
        c.scanRoots = [
            home.appendingPathComponent("Documents/mini-apps").path,
            home.appendingPathComponent("Documents/mini-games").path
        ]
        return c
    }

    static func loadOrSeed() -> PosthouseConfig {
        let url = DirSupport.configFileURL
        if let data = try? Data(contentsOf: url),
           let cfg = try? JSONDecoder().decode(PosthouseConfig.self, from: data) {
            return cfg
        }
        let cfg = PosthouseConfig.defaultConfig()
        cfg.save()
        return cfg
    }

    func save() {
        let dir = DirSupport.appSupport
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        if let data = try? encoder.encode(self) {
            try? data.write(to: DirSupport.configFileURL)
        }
    }
}
