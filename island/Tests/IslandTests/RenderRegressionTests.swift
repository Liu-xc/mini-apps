import Testing
import Foundation
import AppKit
import SwiftUI
import CoreGraphics
@testable import Island

/// it-003 三轮对抗核实补强（渲染级回归）。
/// 核实者指出：`AuditRegressionTests` 只锁纯函数，HeroRing body 的 `if remaining != nil`
/// 守卫、环心「--%」亮度、空态按钮实际命中高度删掉修复也全绿——故用 ImageRenderer
/// 离屏渲染**真实视图**后做像素断言，钉住 body 分支与渲染几何。
@Suite
@MainActor
struct RenderRegressionTests {

    private func render(_ view: some View, scale: CGFloat) -> (buf: [UInt8], w: Int, h: Int)? {
        let renderer = ImageRenderer(content: view)
        renderer.scale = scale
        guard let nsImage = renderer.nsImage,
              let cg = nsImage.cgImage(forProposedRect: nil, context: nil, hints: nil) else { return nil }
        var buf = [UInt8](repeating: 0, count: cg.width * cg.height * 4)
        guard let ctx = CGContext(
            data: &buf, width: cg.width, height: cg.height,
            bitsPerComponent: 8, bytesPerRow: cg.width * 4,
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ) else { return nil }
        ctx.draw(cg, in: CGRect(x: 0, y: 0, width: cg.width, height: cg.height))
        return (buf, cg.width, cg.height)
    }

    private func rgb(_ img: (buf: [UInt8], w: Int, h: Int), _ x: Int, _ y: Int) -> (Int, Int, Int) {
        let i = (y * img.w + x) * 4
        return (Int(img.buf[i]), Int(img.buf[i + 1]), Int(img.buf[i + 2]))
    }

    /// 无数据：整圈白 25% 底轨 + 无健康色弧 + 环心「--%」白 55%（渲染钉 body 守卫）
    @Test
    func unknownRingIsBareWhite25TrackWithoutArc() {
        guard let img = render(HeroRing(remaining: nil, caption: ""), scale: 2) else {
            Issue.record("HeroRing(nil) 离屏渲染失败"); return
        }
        // 路径半径 = (66−8)/2 = 29pt → @2x 58px：8 个采样点全部应落在白 25% 底轨上
        for i in 0..<8 {
            let a = Double(i) * .pi / 4
            let x = Int((Double(img.w / 2) + 58 * cos(a)).rounded())
            let y = Int((Double(img.h / 2) + 58 * sin(a)).rounded())
            let (r, g, b) = rgb(img, x, y)
            #expect((54...74).contains(r) && (54...74).contains(g) && (54...74).contains(b),
                    "无数据环采样点 \(i) 应为白 25% 底轨，实测 rgb(\(r),\(g),\(b))")
        }
        // 全图无健康色弧（红/绿）像素——body 守卫被删则此处必红/绿
        var colored = 0
        var i = 0
        while i < img.buf.count {
            let r = Int(img.buf[i]), g = Int(img.buf[i + 1]), b = Int(img.buf[i + 2])
            if (r > 200 && g < 130 && b < 130) || (g > 150 && r < 130 && b < 160) { colored += 1 }
            i += 4
        }
        #expect(colored == 0, "无数据环不得绘健康色弧，实测彩色像素 \(colored)")
        // 环心占位「--%」= 白 55%（premultiplied 峰值 ≈140；100% 为 255、40% 为 102）
        var peak = 0
        for y in (img.h / 2 - 20)...(img.h / 2 + 20) {
            for x in (img.w / 2 - 30)...(img.w / 2 + 30) {
                peak = max(peak, rgb(img, x, y).0)
            }
        }
        #expect((110...180).contains(peak), "环心占位「--%」应为白 55%，实测峰值 \(peak)")
    }

    /// 0%：绘最小红弧（round cap 保底）+ 底轨仍为白 8%——与「无数据」渲染分离
    @Test
    func zeroPercentRingDrawsRedMinArc() {
        guard let img = render(HeroRing(remaining: 0, caption: ""), scale: 2) else {
            Issue.record("HeroRing(0) 离屏渲染失败"); return
        }
        var red = 0
        var minX = Int.max, maxX = Int.min, minY = Int.max, maxY = Int.min
        var i = 0
        while i < img.buf.count {
            let r = Int(img.buf[i]), g = Int(img.buf[i + 1]), b = Int(img.buf[i + 2])
            if r > 200 && g < 130 && b < 130 {
                red += 1
                let p = i / 4, x = p % img.w, y = p / img.w
                minX = min(minX, x); maxX = max(maxX, x)
                minY = min(minY, y); maxY = max(maxY, y)
            }
            i += 4
        }
        #expect(red >= 100, "0% 应绘可见红弧（round cap 保底），实测纯红像素 \(red)")
        // 红弧在 12 点方向（考虑 CGContext 可能的上下翻转，顶或底皆可），水平居中
        #expect(abs((minX + maxX) / 2 - img.w / 2) <= 10, "红弧应水平居中于 12/6 点方向")
        #expect(minY <= img.h / 2 - 40 || maxY >= img.h / 2 + 40, "红弧应在环顶或环底")
        // 右侧（3 点方向）必为有数据底轨白 8%：premultiplied ≈20（无数据时为 64）
        let (r, g, b) = rgb(img, img.w / 2 + 58, img.h / 2)
        #expect((14...30).contains(r) && (14...30).contains(g) && (14...30).contains(b),
                "0% 底轨应为白 8%，实测 rgb(\(r),\(g),\(b))")
    }

    /// 环墨迹外径 = token 66pt（渲染级，替代恒等式测试）：修前 path=66 → 墨迹 74 必失败
    @Test
    func ringInkOuterDiameterMatchesTokenInRender() {
        guard let img = render(HeroRing(remaining: 80, caption: ""), scale: 1) else {
            Issue.record("HeroRing(80) 离屏渲染失败"); return
        }
        var minX = Int.max, maxX = Int.min, minY = Int.max, maxY = Int.min
        var i = 0
        while i < img.buf.count {
            let r = Int(img.buf[i]), g = Int(img.buf[i + 1]), b = Int(img.buf[i + 2])
            if g > 150 && r < 130 && b < 160 {
                let p = i / 4, x = p % img.w, y = p / img.w
                minX = min(minX, x); maxX = max(maxX, x)
                minY = min(minY, y); maxY = max(maxY, y)
            }
            i += 4
        }
        guard maxX > minX else { Issue.record("未找到绿弧像素"); return }
        let bw = maxX - minX + 1, bh = maxY - minY + 1
        #expect((64...68).contains(bw) && (64...68).contains(bh),
                "环墨迹外径应 =66pt（±AA；修前 74），实测 \(bw)x\(bh)pt")
        #expect(abs((minX + maxX) / 2 - img.w / 2) <= 2 && abs((minY + maxY) / 2 - img.h / 2) <= 2,
                "环墨迹应与布局框同轴")
    }

    /// 空态「去设置」按钮实际渲染命中高度 ≥24pt（P1-E：核实者指出常量测试锁不住按钮本身）
    @Test
    func unconfiguredHintButtonHitTargetAtLeast24pt() {
        let panel = ProviderPanel(
            descriptor: ProviderRegistry.all[0],
            state: SourceState(snapshot: nil, lastError: nil, isFetching: false),
            configured: false,
            now: Date(),
            height: IslandLayout.panelHeight(hasRing: false, detailLines: 0),
            width: IslandLayout.panelWidth(cardWidth: IslandLayout.cardWidth(providerCount: 2),
                                           providerCount: 2),
            openSettings: {}
        )
        guard let img = render(panel, scale: 2) else {
            Issue.record("空态面板离屏渲染失败"); return
        }
        // 按钮底 = 白8% 叠 白4% 面板 → 合成亮度 ≈30（面板本体 ≈10、左侧虚线环 ≈46、文字 ≥140）；
        // 只扫右半区（x ≥ 中线）避开左侧虚线环；按行统计同亮度像素——按钮是 ≥30px 实心横带，
        // 文字 AA 边缘的零星同亮像素不成带，避免把文字行算进按钮高度
        var minRow = Int.max, maxRow = Int.min
        for y in 0..<img.h {
            var run = 0
            for x in (img.w / 2)..<(img.w - 4) {
                let (r, g, b) = rgb(img, x, y)
                if (24...40).contains(r) && (24...40).contains(g) && (24...40).contains(b) { run += 1 }
            }
            if run >= 30 { minRow = min(minRow, y); maxRow = max(maxRow, y) }
        }
        guard minRow <= maxRow else { Issue.record("未找到按钮底色像素"); return }
        let heightPx = maxRow - minRow + 1
        #expect(heightPx >= 48, "「去设置」按钮命中高度应 ≥24pt@2x=48px，实测 \(heightPx)px")
    }
}
