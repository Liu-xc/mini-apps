import Foundation

/// JSON 宽松取值公共工具（it-003：两解析器去重，新增解析器直接复用）
enum JSONLoose {
    /// NSNumber/String → Double；缺失或不可转换为 nil
    static func double(_ value: Any?) -> Double? {
        switch value {
        case let number as NSNumber: number.doubleValue
        case let string as String: Double(string)
        default: nil
        }
    }

    /// 首个命中候选键的字符串值
    static func firstString(_ dict: [String: Any], keys: [String]) -> String? {
        for key in keys where dict[key] is String {
            return dict[key] as? String
        }
        return nil
    }

    /// 美化 JSON（>8KB 截断），供设置页诊断与 spike 校准
    static func pretty(_ data: Data) -> String? {
        guard let obj = try? JSONSerialization.jsonObject(with: data),
              let prettyData = try? JSONSerialization.data(withJSONObject: obj, options: [.prettyPrinted, .sortedKeys]),
              let text = String(data: prettyData, encoding: .utf8) else { return nil }
        return text.count > 8000 ? text.prefix(8000) + "\n…(截断)" : text
    }
}
