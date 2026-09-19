# 衣橱 App（安卓原生）最终实施计划 v6
（线框交互图 · 多角色 · Material 3 Expressive · 分层架构+ERD · Spec-driven 仓库 · 标注体系）

## 目标
个人用安卓应用：多角色衣橱 → 录入衣物 → 分品类槽位滑动组合穿搭 → 复制「合成图+文案」给生图 Agent → 效果图录回与单品双向关联；单品与穿搭均可**打标签 + 自然语言评论**。

## 一、Spec-driven 仓库模式
```
mini-apps/                        # 多应用单仓
├─ README.md  AGENTS.md  .gitignore    # 仓库总览 + 约束铁律
└─ wardrobe/
   ├─ README.md                   # 简介、构建运行
   ├─ specs/                      # ★ 迭代上下文源
   │  ├─ 00-overview.md           # 业务背景、核心价值流
   │  ├─ 01-user-stories.md       # US/UC 编号 + Given/When/Then 验收标准
   │  ├─ 02-wireframes.md         # 线框 W1–W8 + 交互路径（定稿）
   │  ├─ 03-data-model.md         # ERD、字段、品类枚举、标签预设、存储格式
   │  ├─ 04-architecture.md       # 分层、模块职责、设计模式、依赖规则
   │  ├─ 05-design-system.md      # 视觉 token、动效清单、参考链接
   │  ├─ 06-decisions.md          # ADR（JSON vs Room、手动 DI、M3 Expressive…）
   │  ├─ CHANGELOG.md
   │  └─ iterations/it-001-mvp.md # 本次迭代：范围、验证结果、遗留
   └─ (Gradle/Compose 工程)
```
**AGENTS.md 铁律**：①任何功能先在 `specs/iterations/` 写 it-XXX 提案（背景/故事/验收标准）再写代码，bugfix 豁免但回填 ②代码合入必须同步常青 spec，不一致视为未完成 ③验证结果回填 it-XXX + CHANGELOG ④提交规范 `feat|fix|docs|spec|chore(scope): 描述 (#it-XXX)` ⑤接手前先读 00→01→相关 spec 再看代码。本次开发即 it-001，动代码前先固化全部 specs。

## 二、UI 技术栈（2026 主流）
- **Material 3 Expressive**（material3 1.4+）：MaterialExpressiveTheme + MaterialMotionScheme 空间弹簧
- 轮播：HorizontalPager + graphicsLayer/lerp（缩放/视差/圆角形变），业界标准无需三方库
- 点睛：Lottie（空状态）、Coil（图片淡入）、compose-shimmer（骨架屏）
- 参考：官方 Compose Animation 文档/cheat sheet、sinasamaki.com Pager 系列、ProAndroidDev《Swipeable Image Carousel》(2025-03)、github.com/skydoves/compose-animations、m3.material.io
- 坑：M3 1.4.0+ BottomSheet 动效硬编码（issue 452071842），锁版本适配

## 三、视觉与动画（时装编辑风 × 高表现力）
米白 #FAF7F2/墨黑/砖红；Noto Serif SC 衬线标题+字距小标签；大图占屏；圆角 16-20dp；深色映射。
动画：①轮播缩放视差 ②🎲 老虎机（连滚+0.1s 间隔停+轻弹）③共享元素过渡（卡片→详情/穿搭格→穿搭详情）④复制成功 morph ✓+彩屑 ⑤SwipeToDismiss 删除 ⑥切角色 crossfade+标题滑入 ⑦staggered 入场 ⑧Lottie 空状态

## 四、技术架构（分层 + 设计模式）
```
com.leo.wardrobe/
├─ App/MainActivity.kt        # 单 Activity，NavHost + 底部三 Tab
├─ di/AppContainer.kt         # 组合根：手动构造器注入
├─ domain/
│  ├─ model/                  # Person/Item/Outfit/Note/WardrobeCategory/EffectImage/Tag
│  ├─ repository/             # WardrobeRepository、ImageStore 接口
│  └─ usecase/                # ComposeOutfitImage、BuildOutfitPrompt、PickRandomOutfit、ImportItemPhoto
├─ data/
│  ├─ json/JsonFileStore.kt   # 原子写(tmp+rename)
│  ├─ repo/WardrobeRepositoryImpl.kt
│  └─ image/ImageFileStore.kt # WebP 压缩
├─ export/                    # OutfitImageComposer、PromptBuilder(策略)、ShareClipboard(门面)
└─ ui/
   ├─ theme/                  # DesignToken + MotionScheme
   ├─ components/             # 照片卡/品类标签/TagChip/评论卡片/空状态
   └─ outfit/ records/ wardrobe/ person/ detail/ export/  # Screen + ViewModel
```
模式：Repository（接口在 domain）、SSOT（快照+StateFlow 写即持久化，三 Tab 共读、角色过滤流上 map）、MVVM+UDF、组合根手动 DI、值对象、策略（文案模板）、门面（复制/分享）。测试：domain 用例+假仓库 JVM 单测。

## 五、实体关系图（ERD，含标注体系）
```
┌─────────────┐ 1    N ┌───────────────────┐ 1    N ┌──────────────┐
│   Person    │───────▶│       Item        │───────▶│    Note      │
│  角色衣橱主  │  拥有   │  单品              │  评论   │ 自然语言评论   │
├─────────────┤        ├───────────────────┤        ├──────────────┤
│ id/name/    │        │ id, personId      │        │ id, text     │
│ emoji       │        │ category(枚举8类)  │        │ createdAt    │
└──────┬──────┘        │ name/color/desc   │        └──────────────┘
       │ 1             │ imageFile→ImageFile│
       │ 1             │ tags: [String]    │ ← 标签(值对象,预设+自定义)
       ▼               └─────────┬─────────┘
┌─────────────┐ 1    N ┌────────┴──────────┐ 1    N ┌──────────────┐
│   Outfit    │───────▶│  OutfitImage(值)   │        │   Note       │
│  穿搭        │ 成品图  │  file→ImageFile   │        │ (同上,共享实体)│
├─────────────┤        └───────────────────┘        └──────────────┘
│ id,personId │
│ itemIds[] ──┼─▶ Item M:N（Item 反查「相关穿搭」）
│ tags:[Str]  │ ← 穿搭标签（风格/场合/季节…）
│ createdAt   │
└─────────────┘
ImageFile=私有目录 WebP（单品照/成品图共用）；Note 同一实体，parentType+parentId 关联 Item/Outfit
标签预设组（可在 specs 中扩展）：风格=通勤/休闲/运动/约会/度假/正式；季节=春夏秋冬；场合=上班/出游/居家/聚会
```

## 六、线框交互图（定稿 → specs/02-wireframes.md）

### W1 搭配页（Tab1）
```
┌───────────────────────────────┐
│ 👨 Leo ▾           🎲 随机一套 │ ← 点▾=W2 切角色
├───────────────────────────────┤
│ 上装                      3/12 │
│  ‹   ┌─────────────────┐   ›  │
│      │   白衬衫 照片     │      │ ← 中全尺寸/两侧缩淡
│      └─────────────────┘      │
│  白色牛津纺衬衫  WHITE · 04    │
│  #通勤 #简约                    │ ← 单品标签跟随显示
│·······························│
│ 下装 1/8 · 鞋 · 包 …(同样滑动)  │
├───────────────────────────────┤
│  [ 📋 复制图片+文本 ]   [ ↗ ]   │
└───────────────────────────────┘
```

### W2 角色切换 / W3 衣橱 / W4 添加编辑
```
W2                    W3                     W4
┌──────────────┐  ┌────────────────┐  ┌────────────────┐
│ 切换衣橱      │  │衣橱·Leo    共24件│  │✕添加衣物  [保存] │
│ 👨Leo ● 当前  │  │筛选: #全部#通勤… │← │[📷 从相册选照片] │←必填
│ 👩老婆        │  │  按标签筛选     │  │名称[白牛津衬衫__] │
│ 👶宝宝        │  │▍上装(8)         │  │品类(上装)下装…  │←chips
│[＋新建][管理] │  │ ┌──┐白牛津衬衫   │  │颜色[白色______] │
└──────────────┘  │ │缩│#通勤#简约    │  │标签:(通勤)(休闲)＋│←预设+自定义
                  │ └──┘点→编辑      │  │备注[领口易皱…__] │
                  │ 长按→删除        │  └────────────────┘
                  │           [＋]   │
                  └────────────────┘
```

### W5 衣物详情 / W6 导出面板
```
W5                     W6
┌───────────────┐   ┌────────────────┐
│‹返回    [✎编辑]│   │ 导出生图素材     │
│[ 单品大图 ]    │   │ ┌────┬────┐   │
│白牛津衬衫·白色  │   │ │上装 │下装│   │←合成图预览
│#通勤 #简约 #棉质│←  │ ├────┼────┤   │
│穿过这些穿搭：    │   │ │鞋   │包  │   │
│[成品][成品]→滑  │   │ └────┴────┘   │
│点缩略图→W7     │   │ 文案(自动带标签, │
│──────────────│   │ 可编辑):        │
│💬 评论 (2)     │←  │ "通勤简约风格:  │
│ 9/12 洗后微缩水 │   │  上装白牛津衬衫…"│
│ 8/03 搭西装好看 │   │ [📋复制图+文本] │
│[说点什么…]    │←  │ [只复制文本][↗]  │
└───────────────┘   │ [＋录入成品图]   │
                    │ [☆收藏这套]     │
                    └────────────────┘
```

### W7 穿搭详情 / W8 穿搭记录
```
W7                      W8
┌───────────────┐   ┌────────────────┐
│‹穿搭·09-19     │   │[搭配] 穿搭记录 [衣橱]│
│[🖼成品效果图]   │   │筛选: #全部#通勤#约会│←标签筛选
│(多张横滑) ○●○  │   │┌────┐┌────┐   │
│#通勤 #早秋     │←  ││成品 ││成品 │   │
│这套包含：      │   ││#通勤││#约会│   │←标签角标
│▸白衬衫→W5      │   │└────┘└────┘   │
│▸牛仔裤→W5      │   │┌────┐┌────┐   │
│[＋录入][复制素材]│   ││成品 ││合成 │   │←无成品用合成图
│──────────────│   │└────┘└────┘   │
│💬 评论 (1)     │   │ 点一套→W7      │
│ 9/19 被同事夸了 │   └────────────────┘
│[说点什么…]     │
└───────────────┘
```

### 交互路径
```
[W1]◀─W2切角色(全App隔离) │滑动组套/🎲随机
 ↓
[W6]预览·改文案(自动带标签风格) ─复制→生图Agent粘贴
 │                            └分享(兜底)
 ☆收藏──▶建Outfit ◀──＋录入成品图──┘
 ↓
[W8]─(标签筛选)▶[W7]─点单品▶[W5]
[W3]─(标签筛选)─点衣物▶[W5]◀─相关穿搭反查─[W7]
[W3]FAB▶[W4]；W5/W7 均可打标+评论
```

## 七、实施步骤
1. 仓库与 specs 骨架：git init、README/AGENTS.md、全套 specs 文档固化（含 it-001-mvp）
2. 脚手架：Gradle+wrapper+依赖，`./gradlew assembleDebug` 通过
3. 设计系统：DesignToken、字体打包、MotionScheme、组件（含 TagChip/评论卡片）
4. 数据层：实体(含 Note/tags)、Repository、JSON 原子存储、图片管理、备份；假仓库单测
5. 角色体系：默认角色、W2 切换/管理、三 Tab 过滤
6. 衣橱管理：W3（标签筛选）+W4（表单含标签/备注）、Photo Picker、压缩
7. 搭配页 W1：轮播+老虎机+组合记忆+卡片标签展示
8. 导出：合成图、文案（自动拼标签风格）、复制/分享、W6
9. 穿搭体系：收藏、W8（标签筛选+角标）、W7、录入成品图、W5 双向关联
10. 标注体系：TagChip 输入组件（预设+自定义）、评论时间线+输入、标签进文案与筛选
11. 动画打磨：共享元素、彩屑、staggered、Lottie
12. 模拟器验证：AVD 无头→装 APK→adb 模拟→截图对照线框；预置演示数据（含标签/评论）
13. 收尾：验证回填 it-001、CHANGELOG、APK 交付、提交

## 八、验证标准
- `./gradlew assembleDebug` 与 JVM 单测通过
- 模拟器截图：滑动换装、角色隔离、复制/分享、成品图双向关联、打标/评论/标签筛选生效、动效与编辑风观感
- specs 齐全且与代码一致；模拟器不可用则 APK 真机验证

## 暂不做（后续可加）
背景抠图、云端同步、桌面端、品类自定义、角色间共享单品、标签体系自定义分组管理