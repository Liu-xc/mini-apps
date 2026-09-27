# it-001 — 驿站（posthouse）MVP：烽火台 + 邸报

> 状态：**提案，待 Leo 拍板**。确认后才动代码（AGENTS.md 流程②）。

## 背景与动机

两个真实痛点，一个主题：

1. **推送屡屡受挫**。代理出口节点挂、直连被墙、workflow scope 被拒……多次出现「本地积压几十个提交、推送悄悄失败、过几天才发现」。事后靠记忆文件人工对账，很被动。
2. **并行会话状态散乱**。多个 AI 会话满天飞，某天到底干了什么（几个提交、修了几个 bug、哪些仓库水位涨了）没有统一视图，全靠事后翻 git log。

两者本质都是「本地仓库状态没有实时、聚合、有仪式感的呈现」。本迭代立项一个 macOS 菜单栏工具，把这两件事做成一个产品的两个模块：

- **烽火台**（beacon）：实时报警——每个仓库是一座烽火台，积压/远端不通就点狼烟。
- **邸报**（gazette）：每日结算——每晚自动生成一页「今日战报」，带成就系统。

命名主题自洽：古代驿站管烽火传递，也发邸报。

## 平台与形态

**macOS 菜单栏常驻 app**（SwiftUI `MenuBarExtra`，macOS 14+，SwiftPM 构建）。

理由：被监控数据（本地 git 仓库、git 凭证、代理环境）天然长在 Mac 上；Android/网页端只能看 GitHub API 的降级视图，作为远期候选（M4）。开发栈用 SwiftUI，与 clips 的 Mac 端规划一致。

## 里程碑

### M1 烽火台·最小闭环——「看得到」

- 仓库清单：监控根目录列表（默认 `~/Documents/mini-apps`、`~/Documents/mini-games`），自动扫描一层内的 git 仓库，支持手动添加单个仓库。
- 每仓库状态：当前分支、ahead/behind、未提交变更数、最近提交时间、远端连通性（`git ls-remote` 轻量探测，带缓存，不做高频轮询打爆网络）。
- 菜单栏三态图标：全绿（干净）/ 有积压（狼烟）/ 远端不通（熄火）；下拉菜单分组列出各仓库明细。
- 手动操作：单仓 push，自动携带 `http.version=HTTP/1.1` + `postBuffer` 参数（读自全局配置）；推送结果走 macOS 通知中心。
- 配置：JSON 文件（`~/Library/Application Support/Posthouse/config.json`），菜单项「打开配置」；M1 不做 GUI 设置页。

**验收标准**

- [ ] 人为制造本地 ahead（提交不推），菜单栏状态 60s 内更新为「有积压」，明细显示 ahead=N。
- [ ] 远端可达时，菜单内 push 一次成功；日志记录实际使用的参数。
- [ ] 断网（或代理僵死）时图标转「熄火」，恢复后自动转回，无需重启 app。
- [ ] 对 mini-apps、mini-games 两个真实仓库实测状态与 `git status`/`git rev-list` 一致。

### M2 自动哨兵——「守得住」

- 后台轮询（间隔可配，默认 60s，失败指数退避）。
- **自动重试推送**：仓库级白名单开关，**默认全关**；仅对白名单仓库在「远端可达 && 本地 ahead && 快进可推」时自动 push。
- 安全边界（硬规则，单测覆盖）：
  - 永不 `--force`、永不自动 `pull`/`rebase`/`merge`；
  - non-FF 被拒只报警（通知 + 菜单标红），绝不自动动作；
  - 推送仅使用用户配置的参数（HTTP/1.1 等），不改写仓库自身 git 配置。
- 故障识别：连续 N 次网络类失败 → 通知给出「疑似代理故障」提示，附一键执行 network-rescue 诊断脚本的入口（M2 先做提示+打开终端命令，联动自愈放 M4）。

**验收标准**

- [ ] 断网 5 分钟后恢复，恢复 ≤2 分钟内白名单仓库积压自动推上并收到通知。
- [ ] 非白名单仓库在任何情况下不被自动 push（决策逻辑单测）。
- [ ] 构造 non-FF 场景（远端人为推进），验证只报警不动手（单测 + 实测各一）。

### M3 邸报——「记得住」

- 每晚定时（默认 22:30，可配）扫描各仓库当日 git log，生成一页 Markdown 战报。
- 战报内容：各仓库提交数/分类（feat/fix/docs/spec）概览、每个提交取首行一句话、积压水位变化（清零打勾）、连续活跃天数。
- **成就规则引擎**（纯规则，不依赖 LLM）首发 6 枚：
  1. 首日点亮 —— 第一次生成战报；
  2. 连修三坑 —— 单日 ≥3 个 fix 提交；
  3. 深夜修罗 —— 存在 23:00 后提交；
  4. 大部队 —— 单日全仓 ≥10 提交；
  5. 清仓大吉 —— 全部监控仓库积压清零；
  6. 千军一发 —— 单次推送 ≥20 个提交成功。
- 输出：Markdown 写入本地目录（默认 `~/Documents/daily-gazette/`，**不进任何公开 git 仓库**，避免把私有提交信息带上公开 repo）+ 通知中心。
- 可选 LLM 润色开关（默认关）：开了才调用 GLM 把战报写得有味道。

**验收标准**

- [ ] 连续两天定时自动产出战报文件，内容与当日 git log 人工核对一致。
- [ ] 成就规则全部单测；用历史提交数据回放，≥3 枚成就可触发。
- [ ] 战报文件不含配置以外的绝对路径/凭证等敏感信息。

### M4 远期候选（本迭代不承诺）

CI 状态（`gh run list`）、推送成功烟花动效、network-rescue 全自动联动、Android 端远端看板（GitHub API）、邸报周报/月报聚合与成就图鉴页。

## 技术选型草案

| 项 | 选择 | 理由 |
|---|---|---|
| UI | SwiftUI MenuBarExtra | 菜单栏场景官方形态，代码量最小 |
| git 操作 | `Process` 调 git CLI | 不引 libgit2；用户机器的 git 环境（凭证/代理）就是被监控的真实环境 |
| 定时 | `DispatchSourceTimer` + 睡眠唤醒重排 | 菜单栏 app 常驻，需处理 Mac 合盖睡眠 |
| 配置 | JSON 文件 | 无 GUI 设置页，最快出活 |
| 测试 | XCTest（状态机/成就规则/推送决策纯逻辑单测） | git 交互层用假 git 脚本注入 |

## 影响范围

- 新增 `posthouse/` 目录（app 源码 + `specs/` + `tools/`），不改动任何现有应用。
- 立项后补 `posthouse/specs/00-overview.md` 等常青 spec 与根 README/CHANGELOG 条目。
- 本工具自身监控的仓库清单里就包括 mini-apps（自举）。

## 待拍板决策点

1. **项目名/目录名**：推荐 `posthouse`（驿站，涵盖烽火台+邸报两模块）；备选 `beacon`（只突出报警）、`gazette`（只突出战报），或你另起。
2. **里程碑切法**：M1 看得到 → M2 守得住 → M3 记得住 的顺序是否 OK？还是邸报优先级更高（先 M3 后 M2）？
3. **首批自动推送白名单**：建议首批只放 `mini-apps` + `mini-games`，且开关默认关、你手动开。
4. **邸报输出目录**：`~/Documents/daily-gazette/` 是否合适？

---

## 决策点执行结果（/goal 实施全部 按推荐值执行）

1. 项目名 **posthouse**（目录 `posthouse/`）✓
2. 里程碑顺序 M1 → M2 → M3 ✓
3. 白名单首批 mini-apps + mini-games，**总开关与白名单默认全关**，由 Leo 手动开启 ✓
4. 邸报目录 `~/Documents/daily-gazette/` ✓

## 验证记录（2026-09-27）

**测试**：`swift test` **39/39 绿**（swift-testing；CLT 无 XCTest，见 ADR-002）。
覆盖：PushDecision 安全边界 10 + Backoff 指数退避/封顶/代理僵死 4 + Git 失败归类 5 +
temp 仓库真实集成 6（含 non-FF 拒绝+绝不强推）+ 成就规则回放 9 + 连活跃天数/渲染/配置 5。

**M1 烽火台**
- [x] 探测与 git 对账一致：mini-apps `dirty=2/ahead=0`、mini-games `ahead=51/behind=116`，
      status.json 与 `git status --porcelain`/`rev-list --count` 逐值相等。
- [x] 60s 轮询持续更新（posthouse.log 连续轮次）；远端连通性 reach=True 实测。
- [x] 狼烟态菜单栏图标实拍：flame + 计数「1」（mini-games 积压），聚合态 backlog。
- [x] 手动推送成功且日志记录实际参数：`git -c http.version=HTTP/1.1 -c http.postBuffer=524288000 push origin main`，
      成功/失败通知落盘。菜单按钮与 CLI `--push` 同一 `pushNow` 代码路径；
      **菜单 UI 盲点击未验**（LSUIElement 裸二进制无法 AX/截图定位），以 CLI 同路径 e2e 替代。

**M2 自动哨兵（沙盒 /tmp/ph-sbx e2e，不碰真远端）**
- [x] 断网→恢复自动补推：远端置死（testrepo=unreachable↑2 不推）→ 恢复 → **4 秒内**
      自动推送 2 提交，通知+push-events.json 落盘（≤1 轮询周期，远优于验收线 ≤2 分钟）。
- [x] 非白名单仓全程不被碰：otherrepo ahead=1 始终保持（决策单测 + e2e 双证）。
- [x] non-FF 实测：远端被他人推进后自动推送被拒 → 分类 nonFastForward →
      通知「我不会自动动作」→ 远端 tip/本地状态原封不动；退避衰减不锤远端。
- [x] 分叉（behind>0）决策层拦截 + 分叉通知（每仓去重）；push 被拒执行层通知——两层防线。
- [x] 推送白名单双闸门默认关（ConfigStoreTests 语义单测）。

**M3 邸报**
- [x] 定时自动触发：配置 16:06 → **16:06:05** 产出 `~/Documents/daily-gazette/gazette-2026-09-27.md` + 通知。
- [x] 内容与 git 真值逐项对账：提交 6（fix 2/feat 1/docs·spec 3 分类全对）、
      收官积压 1 仓（mini-games）、连续活跃 **9 天**（09-19→09-27 与 `git log --format=%cs` 并集一致）。
- [x] 成就发放：首日点亮 + 深夜修罗（真实 01:30 提交触发），档案/同日去重文件落盘；
      规则引擎 9 项回放单测（含「已解锁不重复发放」「回放日 ≥3 枚」验收项）。
- [ ] 连续两天定时产出：机制同源（checkGazetteDue + gazette-dates 同日去重），**跨日连续性留运行期观察**
      （22:30 默认配置已恢复，今晚自动为第一次正式运行）。

**实测踩坑四连（均已修复+入档 ADR/04-architecture）**
1. `rev-parse --is-inside-work-tree` 对子目录也 true → dist/gradle/wardrobe 误判成仓；改 `.git` 存在性判定。
2. engine 串行队列内并行探测再 `group.wait()` → 自死锁，首拍永不返回；探测改独立并发队列。
3. CLI 模式 timer 未 resume 即释放 → libdispatch SIGTRAP（exit=133）；timer 改 start 懒创建+deinit cancel。
4. GUI 启动访问 `~/Documents` 被 TCC 挂起；扫描加 15s 超时兜底，授权前不挂死。

**超出原案的补充**（记 it-001 内）：CLI 三旗标 `--probe/--push/--gazette`（US-05，脚本化+验收通道）、
分叉专属通知、连续活跃天数（补实现于验收中发现规格缺口）。

**收尾状态**：验收后已恢复默认配置（真实扫描根/轮询 60s/白名单空/邸报 22:30）、
清除验收态成就与邸报文件（今晚 22:30 为干净的首日运行）、沙盒 /tmp/ph-sbx 已删。

## 完成性审计补漏（同日二轮，/goal 完成审计触发）

对照提案逐条查漏，补上两处首轮遗漏的 M2/M3 扫尾项：

1. **M3 可选 LLM 润色开关**（提案原文「默认关，开了才调 GLM」）：
   `gazetteLLMPolish`（默认 false）+ `gazetteLLMBaseUrl/Model`；密钥走 `llm.key`（0600，不入 config，
   同 island 凭证模式）；实现为 curl 调 OpenAI 兼容 chat/completions，**任何失败静默回退规则版**，
   门禁单测（默认关不调网/无密钥回退）计入 41 测试。
2. **M2 network-rescue 一键入口**（原文「附一键执行诊断脚本的入口」）：
   菜单「烽火台 → 网络诊断（network-rescue）…」生成 .command 交终端跑
   `~/.agents/skills/network-rescue/scripts/network_doctor.sh`；疑似代理故障通知文案挂上该入口。
