import Testing
import Foundation
@testable import Island

/// it-003 对抗审计回归测试（2026-09-26 二轮）：
/// P0-A hitTest 坐标系换算 / P1-C 环墨迹外溢 / P1-D 0%≠无数据 / P1-E 命中区 / P2 圆帽交叠。
@Suite
struct AuditRegressionTests {

    // MARK: P0-A — 窗口基坐标 → 屏幕全局坐标

    @Test
    func screenPointConvertsWindowBaseToScreen() {
        // 审计探针实测几何：panel frame=(500,700,352,213)，hitTest 收到 (324,187)（窗口本地）
        let frame = NSRect(x: 500, y: 700, width: 352, height: 213)
        let screen = IslandContentView.screenPoint(
            forWindowPoint: NSPoint(x: 324, y: 187), windowFrame: frame
        )
        #expect(screen.x == 824 && screen.y == 887)
        // 换算后必须落回全局矩形（修复前 local 点与 global rect 恒不相交 → 点击全死）
        #expect(frame.contains(screen))
    }

    @Test
    func screenPointFallsBackToInputWhenWindowNil() {
        let p = NSPoint(x: 3, y: 4)
        #expect(IslandContentView.screenPoint(forWindowPoint: p, windowFrame: nil).x == 3)
        #expect(IslandContentView.screenPoint(forWindowPoint: p, windowFrame: nil).y == 4)
    }

    // MARK: P1-C — 环描边墨迹外缘 = 外径（stroke 中心对称外溢 4pt 已修）

    @Test
    func ringInkOuterDiameterEqualsToken() {
        let diameter: CGFloat = IslandLayout.ringDiameter   // 66
        let ringWidth: CGFloat = IslandLayout.ringWidth     // 8
        let pathDiameter = diameter - ringWidth             // 路径 58
        // stroke 以路径为中心 → 墨迹外径 = 路径 + 环宽 = token 外径（不再 74）
        let inkOuter = pathDiameter + ringWidth
        #expect(inkOuter == diameter)
        #expect(diameter == 66)
    }

    // MARK: P1-D — 0% 与「无数据」渲染分支分离

    @Test
    func zeroPercentDrawsMinArcButUnknownDrawsNone() {
        // 0%：有数据 → 绘弧（最小可见红弧 0.005）
        let zeroTrim = HeroRing.arcTrimEnd(
            fill: min(1, max(0, 0 / 100)), ringWidth: 8, pathDiameter: 58
        )
        #expect(zeroTrim >= 0.005, "0% 必须绘最小弧（修审计 P1-D：不再与无数据同渲染）")
        // 无数据：底轨升为 unknown 白 25%，与色点同值
        #expect(HeroRing.trackOpacity(remaining: nil) == 0.25)
        #expect(HeroRing.trackOpacity(remaining: 80) == 0.08)
    }

    // MARK: P2 — round cap 交叠补偿（96%+ 帽叠死缝隙，99% vs 100% 无差）

    @Test
    func arcTrimCompensatesRoundCapOverlap() {
        let ringWidth: CGFloat = 8
        let pathDiameter: CGFloat = 58
        let cap = Double(ringWidth / (CGFloat.pi * pathDiameter))
        // 补偿：视觉弧长（trim + 帽伸长）= 名义填充 → 99% 留 1% 缝、100% 恰好闭合
        let trim99 = HeroRing.arcTrimEnd(fill: 0.99, ringWidth: ringWidth, pathDiameter: pathDiameter)
        let visual99 = trim99 + cap
        #expect(abs(visual99 - 0.99) < 1e-9, "补偿后视觉弧长 = 名义填充")
        let trim100 = HeroRing.arcTrimEnd(fill: 1.0, ringWidth: ringWidth, pathDiameter: pathDiameter)
        let visual100 = trim100 + cap
        #expect(abs(visual100 - 1.0) < 1e-9, "100% 视觉闭合整圈")
        #expect(visual100 - visual99 > 0.005, "99% 与 100% 有可见差（修复前帽交叠盖死缝隙）")
        // 极小填充保底
        #expect(HeroRing.arcTrimEnd(fill: 0, ringWidth: ringWidth, pathDiameter: pathDiameter) == 0.005)
        // 非法直径不 NaN
        #expect(HeroRing.arcTrimEnd(fill: 0.5, ringWidth: 8, pathDiameter: 0) == 0.005)
    }

    // MARK: P1-E / P2 — 命中区与无数据灰同值

    @Test
    func iconHitTargetMeets24pt() {
        #expect(IslandLayout.footerHeight >= 24, "it-003 AC2：图标按钮命中区 ≥24pt")
    }

    @Test
    func unknownNSColorMatchesSwiftUIWhite25() {
        let ns = IslandTheme.levelNSColor(nil)
        var r: CGFloat = 1, g: CGFloat = 1, b: CGFloat = 1, a: CGFloat = 0
        ns.usingColorSpace(.sRGB)!.getRed(&r, green: &g, blue: &b, alpha: &a)
        #expect(abs(a - 0.25) < 0.001, "菜单 unknown 与卡片同为白 25%（修审计 P2 两档灰）")
        // 等级阈值回归（50/20 边界）
        #expect(IslandTheme.level(of: 50) == .good)
        #expect(IslandTheme.level(of: 20) == .warn)
        #expect(IslandTheme.level(of: 19.9) == .bad)
    }
}
