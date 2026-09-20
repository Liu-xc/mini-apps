# it-014 · 导出：长图保存到相册

- **状态**：已完成
- **提案日期**：2026-09-20
- **来源**：Leo 口头需求（「图片应该还支持下载，而不仅是复制」）
- **范围**：wardrobe 导出面板（ShareClipboard / ExportSheet）

## 变更

1. **「存相册」按钮**：导出面板动作栏加第三动作（复制长图｜存相册｜分享），
   将当前合成长图写入系统相册 `Pictures/Wardrobe` 目录。
2. **存储实现**（ShareClipboard.saveToGallery）：API 29+ 走 MediaStore
   （RELATIVE_PATH + IS_PENDING 两段式，无需任何存储权限）；API 28 及以下
   返回失败并由 toast 引导走「分享」保存（避免为旧系统引入运行时权限流）。
3. 成功 toast「已保存到相册 Pictures/Wardrobe」，失败引导分享兜底。

## 验证记录

- **构建**：`assembleDebug` ✅（2026-09-20）
- **模拟器实测**：
  - 动作栏三按钮「复制长图｜存相册｜分享」完整显示（初版「分享」被截断，调整配重 1.25/0.95/0.8+contentPadding 修复）
  - 点「存相册」→ 文件三次点击三次写入 `/sdcard/Pictures/Wardrobe/wardrobe_outfit_*.jpg`
    （702KB，1024×8666 完整 JPEG，拉出本地核验亮度内容非空）
  - toast 被 sheet 遮挡不可见（既有问题）→ 成功反馈改落在按钮上：「已存相册 ✓」2s 后恢复
  - API < 29 分支：返回 false → toast 引导走「分享」（未在模拟器实测，逻辑路径 review）
