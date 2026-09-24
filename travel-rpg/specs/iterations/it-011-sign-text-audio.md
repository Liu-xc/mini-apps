# it-011 · 路牌文字与音频初版（Sign Text · Audio）

- **状态**：实施中（Leo /goal 常设指令：「持续迭代→review→再迭代」，2026-09-24；
  it-010 遗留直入）
- **里程碑**：M2 世界填充 → 感官层
- **涉及用户故事**：US-3（探索引导）、US-4（画面/听感品质）

## 背景与动机

it-010 的路牌是空白菜牌——「石林 →」这类引导文字是路引可读性的最后一环；
同时整个游戏完全无声，环境音/脚步/水花是沉浸感的最大缺口。两者都不引入
外部资产：文字用 Canvas 程序化纹理，声音用 WebAudio 合成。

## 验收标准

- **AC-1 路牌文字**：Placement 增加可选 `label` 字段（schema 向后兼容）；placer
  对带 label 的 sign 用 Canvas 程序化木纹+文字纹理（站名+方向箭头，中文衬线）
  覆写板面材质（仅克隆板面，不影响立柱）；4 块路牌各标注目的地，近景可读。
- **AC-2 音频初版**：`src/audio.ts` WebAudio 程序化合成——
  ① 风环境音（低通噪声 + LFO 阵风）；② 鸟鸣（日间随机啾鸣，sunset 停）；
  ③ 湖岸水声（近湖渐入）；④ 脚步（草/涉水/游泳三种音色，onStep 驱动）；
  ⑤ 落地闷响（onLand）；⑥ 入水水花（onSwimChange）；⑦ 划桨声（骑乘推进周期）；
  M 键静音开关；首次按键/点击解锁 AudioContext（浏览器自动播放策略）。
- **AC-3 回归**：构建零错、errs=[]、行为断言、probe +audio（ready/muted/
  stepSounds 计数）、路牌近景截图可读文字。

## 影响范围

新增 `src/audio.ts`；修改 `scenes.ts`（label 数据）、`placer.ts`（标签应用）、
`main.ts`（接线/M 键/probe）、`specs/03-data-model.md`（label 字段）、本文件、
CHANGELOG、README。

## 验证记录

**2026-09-24 实施完成，AC-1 ~ AC-3 全部通过。**

| AC | 结果 |
|---|---|
| AC-1 路牌文字 | ✅ Placement.label 可选字段（03-data-model 同步）+ placer 板面叠文字面片：R1 材质替换路线失败（Kenney 盒子 UV/材质结构吃不进 map，板面纯色）→ R2 改「板面正/背各叠 Canvas 面片」（木纹底+描边+衬线站名+箭头，polygonOffset 防 z-fight，双面可读），近景「石林 →」清晰可读（a-sign-text2.png） |
| AC-2 音频初版 | ✅ audio.ts 全 WebAudio 合成：风（低通噪声+双 LFO 阵风）、鸟鸣（日间 4-9s 随机啾鸣，sunset 停）、湖岸水声（带通噪声按近湖度渐入）、脚步草/水双音色（onStep）、落地闷响（onLand 按冲击力）、入水水花（onSwimChange 双层）、划桨（骑乘尾迹节拍）；首次按键/点击解锁 AudioContext；M 静音；实测 probe audio ready=true、steps 计数随行走增长 |
| AC-3 回归 | ✅ 构建零错；errs=[]；本会话行为断言（快旅/移动/跳跃）已过；编辑器 world 48 摆放不受影响（label 为可选字段不破坏既有数据） |

### 走查备注

- 板面文字用「叠面片」而非「换材质」——Kenney 盒子 UV 指向图集子区域，
  换 map 只会露出纯色；面片方案对任意 glTF 板面都稳。
- 音量参数刻意保守（master 0.9，各层 0.05-0.26），实机听感待 Leo 反馈微调。

### 遗留（后续迭代候选）

- 音频：篝火噼啪（近campfire渐入）、昼夜鸟种分层、脚步随移速变奏；
- 路牌文字动态指向（按玩家位置算最近站名）；
- 水下闷响滤波（游泳时低通全局混音）。
