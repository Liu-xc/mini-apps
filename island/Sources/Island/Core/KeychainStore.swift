import Foundation
import Security

/// 钥匙串账户：每个内容源一个（GLM=API Key；MiMo=浏览器 Cookie）
enum KeychainAccount {
    static let glm = "glm-key"
    static let mimoCookie = "mimo-cookie"
    static let legacyAPIKey = "api-key"
}

/// 凭证只进钥匙串，不落任何文件/日志
enum KeychainStore {
    private static let service = "com.spartapps.island"

    static func save(_ secret: String, account: String) throws {
        SecItemDelete(baseQuery(account: account) as CFDictionary)
        var attributes = baseQuery(account: account)
        attributes[kSecValueData as String] = Data(secret.utf8)
        attributes[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
        let status = SecItemAdd(attributes as CFDictionary, nil)
        guard status == errSecSuccess else {
            throw ProviderError(message: "SecItemAdd 失败：\(status)")
        }
    }

    static func load(account: String) -> String {
        var item: CFTypeRef?
        var query = baseQuery(account: account)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        guard status == errSecSuccess,
              let data = item as? Data,
              let secret = String(data: data, encoding: .utf8) else { return "" }
        return secret
    }

    static func delete(account: String) {
        SecItemDelete(baseQuery(account: account) as CFDictionary)
    }

    /// it-001 时期的旧账户（api-key）迁移到 glm-key
    static func migrateLegacyAPIKey() {
        let legacy = load(account: KeychainAccount.legacyAPIKey)
        guard !legacy.isEmpty, load(account: KeychainAccount.glm).isEmpty else { return }
        try? save(legacy, account: KeychainAccount.glm)
        delete(account: KeychainAccount.legacyAPIKey)
    }

    private static func baseQuery(account: String) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
    }
}
