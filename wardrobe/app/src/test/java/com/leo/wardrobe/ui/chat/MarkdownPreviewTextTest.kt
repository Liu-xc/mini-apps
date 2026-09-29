package com.leo.wardrobe.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

/** it-062：W12 会话列表预览扁平化——与 MarkdownText 同口径去标记，旧索引残留同样干净。 */
class MarkdownPreviewTextTest {

    @Test
    fun `标题与列表项压成单行且以间隔号连接`() {
        val markdown = """
            好，帮你配一套通勤装。

            ## 第一套 · 秋冬通勤
            - 上装：黑色罗纹高领衫
            - 下装：深灰阔腿西裤
        """.trimIndent()

        assertEquals(
            "好，帮你配一套通勤装。 第一套 · 秋冬通勤 · 上装：黑色罗纹高领衫 · 下装：深灰阔腿西裤",
            markdownPreviewText(markdown),
        )
    }

    @Test
    fun `行内强调与链接只留文本`() {
        assertEquals(
            "思路：深浅对比，参考 这篇",
            markdownPreviewText("**思路**：深浅对比，参考 [这篇](https://example.com)"),
        )
    }

    @Test
    fun `旧索引把换行压成空格后的残留列表标记折叠为间隔号`() {
        // ChatSessionIndex.refresh 的旧实现：latest.replace('\n', ' ').take(60)
        val legacy = "## 第一套 · 秋冬通勤 - 上装：黑色罗纹高领衫 - 下装：深灰色百褶裙"
        assertEquals(
            "第一套 · 秋冬通勤 · 上装：黑色罗纹高领衫 · 下装：深灰色百褶裙",
            markdownPreviewText(legacy),
        )
    }

    @Test
    fun `引用有序列表与水平线也被剥掉`() {
        val markdown = """
            1. 先看色
            2. 再看料
            > 引用一句

            ---
            收尾一句话
        """.trimIndent()

        assertEquals(
            "先看色 · 再看料 · 引用一句 收尾一句话",
            markdownPreviewText(markdown),
        )
    }

    @Test
    fun `纯文本不变且函数幂等`() {
        val plain = "今天穿得舒服就行"
        assertEquals(plain, markdownPreviewText(plain))
        assertEquals(plain, markdownPreviewText(markdownPreviewText(plain)))

        val rendered = markdownPreviewText("## 第一套\n- 上装：白T")
        assertEquals(rendered, markdownPreviewText(rendered))
    }

    @Test
    fun `空白与空串返回空`() {
        assertEquals("", markdownPreviewText(""))
        assertEquals("", markdownPreviewText("   \n \n"))
    }
}
