import Foundation
import Security

/// 凭证存储：Application Support/island/credentials.json（chmod 0600）。
/// 不用钥匙串——ad-hoc 签名的 App 每次重构建签名都变，钥匙串 ACL 随之失效，
/// 每版都弹窗要密码；本地 0600 文件零弹窗且重启/重构建稳定。
/// 凭证绝不入仓库/日志。
/// 钥匙串账户（迁移期引用）
enum KeychainAccount {
    static let glm = "glm-key"
    static let mimoCookie = "mimo-cookie"
    static let legacyAPIKey = "api-key"
}

enum CredentialStore {
    private static var baseURL: URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? FileManager.default.temporaryDirectory
        return base.appendingPathComponent("island", isDirectory: true)
    }

    private static var fileURL: URL {
        baseURL.appendingPathComponent("credentials.json")
    }

    static func load(account: String) -> String {
        guard let dict = readDictionary() else { return "" }
        return dict[account] ?? ""
    }

    static func save(_ secret: String, account: String) {
        var dict = readDictionary() ?? [:]
        dict[account] = secret
        writeDictionary(dict)
    }

    static func delete(account: String) {
        var dict = readDictionary() ?? [:]
        dict.removeValue(forKey: account)
        writeDictionary(dict)
    }

    /// it-001/002 时期写入钥匙串的凭证迁移到文件，并删除钥匙串旧条目（消灭弹窗）
    static func migrateFromKeychain() {
        var migrated = false
        for account in [KeychainAccount.glm, KeychainAccount.mimoCookie, KeychainAccount.legacyAPIKey] {
            let secret = keychainLoad(account: account)
            guard !secret.isEmpty else { continue }
            if load(account: account).isEmpty, account != KeychainAccount.legacyAPIKey {
                save(secret, account: account)
            }
            keychainDelete(account: account)
            migrated = true
        }
        if migrated {
            NSLog("[island] 凭证已从钥匙串迁移到本地文件")
        }
    }

    // MARK: - 文件读写

    private static func readDictionary() -> [String: String]? {
        guard let data = try? Data(contentsOf: fileURL),
              let dict = try? JSONDecoder().decode([String: String].self, from: data) else { return nil }
        return dict
    }

    private static func writeDictionary(_ dict: [String: String]) {
        do {
            try FileManager.default.createDirectory(at: baseURL, withIntermediateDirectories: true)
            let data = try JSONEncoder().encode(dict)
            try data.write(to: fileURL, options: .atomic)
            try FileManager.default.setAttributes([.posixPermissions: 0o600], ofItemAtPath: fileURL.path)
        } catch {
            NSLog("[island] 凭证文件写入失败: \(error.localizedDescription)")
        }
    }

    // MARK: - 钥匙串（仅迁移用）

    private static func keychainBaseQuery(account: String) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: "com.spartapps.island",
            kSecAttrAccount as String: account,
        ]
    }

    private static func keychainLoad(account: String) -> String {
        var item: CFTypeRef?
        var query = keychainBaseQuery(account: account)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        guard status == errSecSuccess,
              let data = item as? Data,
              let secret = String(data: data, encoding: .utf8) else { return "" }
        return secret
    }

    private static func keychainDelete(account: String) {
        SecItemDelete(keychainBaseQuery(account: account) as CFDictionary)
    }
}
