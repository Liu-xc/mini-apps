package com.leo.wardrobe.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * it-071 P3 / ADR-028：基线 profile 采集场景。
 *
 * 启动（搭配页，含槽位 pager 与入场动画）→ 衣橱网格往返 fling → 穿搭记录页往返 fling，
 * 覆盖首启热路径与两大列表滚动路径（it-071 优化的收益锚点）。
 * 生成物 merge 进 :app release 资产，安装后由 profileinstaller 提交 ART AOT 编译。
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = "com.leo.wardrobe") {
        startActivityAndWait()
        // 首屏入场动画（it-028 瀑布 900ms）+ 数据加载落定
        Thread.sleep(1_600)

        // 衣橱 Tab（底部导航 label；「衣橱」仅此一处精确文本，已核 MainActivity Tab enum）
        device.findObject(By.text("衣橱"))?.click()
        device.wait(Until.hasObject(By.text("筛选")), 5_000)
        Thread.sleep(1_200)

        val w = device.displayWidth
        val h = device.displayHeight
        // 网格 fling 往返（steps 小 = 高初速，贴近真实甩动）
        repeat(3) {
            device.swipe(w / 2, (h * 0.75).toInt(), w / 2, (h * 0.25).toInt(), 8)
            Thread.sleep(500)
            device.swipe(w / 2, (h * 0.25).toInt(), w / 2, (h * 0.75).toInt(), 8)
            Thread.sleep(500)
        }

        // 穿搭记录 Tab（页头 title 带人名后缀，精确 text 只命中导航 label）
        device.findObject(By.text("穿搭记录"))?.click()
        device.wait(Until.hasObject(By.text("随机一套")), 5_000)
        Thread.sleep(1_200)
        repeat(2) {
            device.swipe(w / 2, (h * 0.75).toInt(), w / 2, (h * 0.3).toInt(), 8)
            Thread.sleep(500)
            device.swipe(w / 2, (h * 0.3).toInt(), w / 2, (h * 0.75).toInt(), 8)
            Thread.sleep(500)
        }
    }
}
