# 报告 · HTML→PDF 管线与质量门

## 生成

`scripts/report_template.py`：数据驱动。顶部三块可编辑数据：

```python
THEMES  = [dict(app="eats", no="E1", title="…", concl="一句话结论",
                problems=[("P0","…"),("P1","…")],       # 严重度标签自动着色
                shot="eats-01.png", wire="wf-01.png",    # assets/ 下的文件名
                points=[(1,"徽标①对应的要点"),(2,"…")])]  # 编号=线框徽标
COMMONS = [("C1","共性问题","涉及页","P0","修法")]
TESTS   = [("实测项","结论")]
```

结构自动生成：封面 → 总评/方法/共性问题表 → 分节页 → 每主题一页（页眉严重度计数/结论/问题两栏/
左原图 76mm·中线框 76mm·右要点栏）→ 落地拆分 → 附录（实测表+证据图）。

## 渲染（pdf 技能管线）

```bash
python3 <pdf-skill>/scripts/poster_validate.py check-html report.html   # 必须无 ERROR
NODE_PATH=$(npm root -g) node <pdf-skill>/scripts/html2pdf-next.js \
  report.html --output 报告.pdf --width 210mm --height 297mm
python3 <pdf-skill>/scripts/pdf_qa.py --no-tables 报告.pdf             # FAIL 必修，WARN 逐条判断
```

## 布局铁律（实测换来的）

- **禁用 `position:absolute`**：`html2pdf-next.js` 把它强转 static，封面/分节页塌叠。
  全程 flex 流式：`.page{display:flex;flex-direction:column}`，钉底页脚 `margin-top:auto`。
- `.page` 固定 `height:297mm; overflow:hidden` **不能**阻止内容溢出时打印分页——页脚会被顶到
  新的近空页（症状：某页只剩「附录 21」）。内容必须真实收进 297mm。
- 主题页预算：padding 12+10mm + 页眉 ~13 + 结论 ~12 + 问题区 ~18 + 图 76mm 宽→168.9mm 高 + 标签 7
  ≈ 241mm，留白充足；**改图宽必须同步改高度（宽÷0.45）**。
- 附录页最易溢出：证据图 ≤88mm 高、表格行距 ~1.9mm、`h2` 间距 ≤4.5mm。
- 多页混合背景：`html,body{background:最浅内容底色}`；每 `.page` 加 `overflow:hidden`。
- 字体栈 `"Hiragino Sans GB","PingFang SC","Heiti SC",sans-serif`（须带通用回落，
  check-html 的 FONT_NO_FALLBACK 是 ERROR 级）。

## 质量门（缺一不可）

1. `check-html` 无 ERROR → 渲染。
2. `pdf_qa`：P0 级 WARN（溢出页/空白页/缺元数据）必修；封面非对称留白属设计意图可留。
   元数据：pymupdf `set_metadata`（title/author/subject/creator）。
3. **视觉验收门**：`pymupdf` 全页渲 100dpi PNG → 分两批派发 `pdf:visual-judge` 子代理
   （每批给页面清单+版式说明+验收标准，要求逐页 `{page,pass,issues}`）→ 问题定点修复
   → 修复页发回同一 judge 复核 → 全过才算完成。这一步抓出过：符号缺字、徽标压字、
   线框内容错域、分节页缺页码、附录溢出——全是脚本检查不出的。

## 依赖速记

- playwright/pdf-lib：npm 全局装（`NODE_PATH=$(npm root -g)`），Chromium headless 缓存在
  `~/Library/Caches/ms-playwright`；装包加 `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1` 可跳过浏览器下载。
- pillow/pymupdf：临时 venv（`pip install -i 清华镜像 pillow pymupdf`），PEP 668 不许 --user。
