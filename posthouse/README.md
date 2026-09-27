# 驿站 posthouse

macOS 菜单栏工具：本地 git 仓库的**烽火台**（实时报警）+ **自动哨兵**（白名单补推）+ **邸报**（每日战报）。
spec-driven 立项见 [`specs/iterations/it-001-beacon-and-gazette.md`](specs/iterations/it-001-beacon-and-gazette.md)。

## 构建与运行

```bash
# 前置：Xcode Command Line Tools（swift + git），无需 Xcode 工程
tools/build-app.sh            # debug 构建 + 组装 build/Posthouse.app
tools/build-app.sh --release  # release

open build/Posthouse.app      # 首次 GUI 启动会请求「文稿」文件夹访问（TCC 弹窗，允许即可）
# 或从终端启动（继承终端授权，无弹窗）：
build/Posthouse.app/Contents/MacOS/Posthouse
```

菜单栏出现火焰图标即工作正常；点开可看各仓明细、开关自动推送、生成邸报。

## CLI（一次性，同代码路径）

```bash
BIN=build/Posthouse.app/Contents/MacOS/Posthouse
$BIN --probe                # 同步探测全部仓库，打印 status.json（exit 0）
$BIN --push /abs/repo       # 手动推送；exit 0 成功 / 3 被安全规则拦 / 1 失败
$BIN --gazette              # 立即生成今日邸报
```

## 配置

`~/Library/Application Support/Posthouse/config.json`（菜单「打开配置…」直达）：

- `scanRoots` 扫描根（根自身 + 一层内子仓库），默认 `~/Documents/mini-apps`、`~/Documents/mini-games`
- `autoPushEnabled` / `autoPushWhitelist` 自动推送**双闸门，默认全关**
- `gazetteHour/Minute` 每日战报定时（默认 22:30），`gazetteOutputDir`（默认 `~/Documents/daily-gazette/`）
- `pushArgs` 推送注入参数（默认 HTTP/1.1 + postBuffer=512MB）

数据与日志同目录：`status.json` / `push-events.json` / `achievements.json` / `posthouse.log`。

## 安全边界（硬规则）

白名单外绝不自动推、永不 `--force`、永不自动 `pull/rebase/merge`（本地落后只发分叉通知）、
远端不通不推（指数退避 30s→600s）。详见 `specs/06-decisions.md` ADR-003。

## 测试

```bash
swift test    # 37 用例：安全边界单测 + 真实 temp 仓库集成 + 成就回放
```

## 文档

- 常青 spec：`specs/00-overview` → `01-user-stories` → `03-data-model` → `04-architecture` → `05-design-system` → `06-decisions`
- 迭代记录：`specs/iterations/it-001-beacon-and-gazette.md`（含验证记录）
