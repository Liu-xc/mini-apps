package com.leo.darkroom.develop

/**
 * 沉浸相册的播放簿记（it-011 US-18）：**本次启动内**已显影过的图片不再自动重播动画
 * （翻回时直接显示成品），重播 = 手动把它移出已播集合。纯逻辑，可单测。
 */
class GalleryPlayback {
    private val played = LinkedHashSet<Long>()

    fun shouldAnimate(id: Long): Boolean = id !in played

    fun markPlayed(id: Long) {
        played += id
    }

    /** 手动重播：清掉已播标记，下一次成为当前页即重跑动画 */
    fun replay(id: Long) {
        played -= id
    }

    fun snapshot(): Set<Long> = played.toSet()
}
