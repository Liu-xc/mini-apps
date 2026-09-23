# Vendor 清单（第三方组件）

| 组件 | 版本/来源 | 许可证 | 文件 |
|---|---|---|---|
| xiangqi.js | [lengyanyu258/xiangqi.js](https://github.com/lengyanyu258/xiangqi.js) `master`（2026-09-23 取） | BSD-2-Clause（文件头保留） | `vendor/xiangqi.js` |
| xiangqiboardjs | [lengyanyu258/xiangqiboardjs](https://github.com/lengyanyu258/xiangqiboardjs) `master` 构建产物（`docs/js/xiangqiboard.min.js`、`docs/css/xiangqiboard.min.css`，2026-09-23 取） | MIT（文件头保留） | `vendor/xiangqiboardjs/` |
| xiangqiboardjs 图片资源 | 同上仓库 `docs/img/`：棋盘底图 + wikimedia 14 枚棋子 SVG（JS 按文档根相对路径引用） | 同上（Wikimedia 棋子 SVG 为公有领域/自由许可，随组件分发） | `img/xiangqiboards/`、`img/xiangqipieces/` |
| jQuery 3.6.0 | code.jquery.com | MIT | `vendor/jquery.min.js` |

均为 vendored 快照，不走 npm；升级时重新下载并更新本表日期。
