# mini-apps

个人小应用合集仓库。一个应用一个目录，各自独立可构建，互不依赖。

| 应用 | 目录 | 说明 | 状态 |
|---|---|---|---|
| 衣橱 | [`wardrobe/`](wardrobe/) | 安卓原生应用：多角色衣橱管理、滑动组合穿搭、导出生图素材给 AI 生图 Agent、成品效果图回录与单品双向关联 | 开发中 (it-001) |
| 吃啥 | [`eats/`](eats/) | 安卓原生应用：堂食/外卖/自做三类吃饭记录，地图标记 + 最近一次追踪 + 转盘快速决策 | 提案中 (it-001) |
| 剪贴盒 | [`clips/`](clips/) | Mac + Android 双端（Compose Multiplatform）：本地剪贴板历史，自动记录、热键呼出搜索、一键复制回，纯离线零权限 | 提案中 (it-001) |

## 仓库约定

- **本仓库是 spec-driven 的**：先写规格文档再写代码，详见 [AGENTS.md](AGENTS.md)。
- 每个应用的规格文档、迭代记录放在 `应用名/specs/` 下，是该应用一切迭代的上下文源。
- 新增应用：在根目录建 `应用名/` 子目录，复制 `wardrobe/specs/` 的文档骨架作为起点。
- 根目录不放任何应用代码。

## 环境要求

- 安卓应用（衣橱、吃啥）：JDK 17、Android SDK（platform 35 / build-tools 34+）。构建方式见各应用目录的 README：[wardrobe/README.md](wardrobe/README.md)、[eats/README.md](eats/README.md)。
- 剪贴盒（Mac + Android 双端）：JDK 17；Android 端还需 Android SDK。构建方式见 [clips/README.md](clips/README.md)。
