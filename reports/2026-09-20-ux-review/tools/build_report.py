# -*- coding: utf-8 -*-
"""UX 评审报告 HTML 生成器 · A4 多页 · 数据驱动模板"""
import os, html

BASE = os.path.normpath(os.path.join(os.path.dirname(__file__), ".."))
A = "assets"

# ---------------- 数据 ----------------
EATS = "eats"
WRD = "wardrobe"

themes = [
    # ===== 吃啥 =====
    dict(app=EATS, no="E1", title="吃啥 · 首页（卡组浏览）",
        concl="功能在线但「可滑动」毫无线索，筛选生效却无任何候选数反馈，用户第一次拿到只会当成静态卡。",
        problems=[
            ("P0", "卡组可滑动性零线索：无堆叠露边、无页码计数，看不出后面还有牌"),
            ("P0", "筛选无候选数反馈（实测：类型多选确实过滤卡组，但全程不显示剩几家）"),
            ("P1", "评分胶囊与详情页星级、均分语义不统一"),
            ("P2", "「忌口 #标签」与「类型」chips 挤同一行，语义分组弱；卡上链接与操作按钮争抢注意力"),
        ],
        shot="eats-01-home.png", wire="wf-01-eats-home.png",
        points=[
            (1, "类型多选 chips 带 ✓；忌口/排除开关收进「筛选」，省一行"),
            (2, "后卡露边 8–12dp 常驻，可滑动性可视"),
            (3, "‹ 3/12 › 卡序+候选数常驻，筛选即反馈"),
        ]),
    dict(app=EATS, no="E2", title="吃啥 · 首页（抽取落定）",
        concl="决策闭环的最后一公里断了：抽取动画结束后只有一行小字，确认按钮不可见（两轮实测复验）。",
        problems=[
            ("P0", "落定后「就吃这个 / 再抽」按钮完全不在可视区，需滚动寻找；结果条只是贴着底部导航的一行小字"),
            ("P1", "抽中是全流程高潮时刻，呈现无仪式感：结果条无强调底色，彩屑一闪即逝"),
        ],
        shot="eats-02-drawn.png", wire="wf-02-eats-drawn.png",
        points=[
            (1, "深色强调结果块 + 彩屑环绕，落定即聚焦"),
            (2, "确认按钮常驻屏内，与底部导航留 12dp"),
            (3, "落定时卡组自动收拢让位，不新增滚动"),
        ]),
    dict(app=EATS, no="E3", title="吃啥 · 地图",
        concl="「看得懂地图、看不懂数据」——颜色含义零解释，且三种类型色全挤在冷色绿蓝区（代码核对）。",
        problems=[
            ("P0", "marker 颜色无图例；实现配色 青绿/蓝/草绿 全挤冷色区，堂食与自做难分，与 spec 基线（红/琥珀/绿）不符"),
            ("P1", "无「回到我的位置」按钮；底部摘要卡信息量偏少"),
        ],
        shot="eats-03-map.png", wire="wf-03-eats-map.png",
        points=[
            (1, "吸顶图例胶囊：●堂食 ●外卖 ●自做"),
            (2, "回位按钮 ⌖ 常驻右上"),
            (3, "配色回归 spec：红 / 琥珀 / 绿，拉开色相"),
        ]),
    dict(app=EATS, no="E4", title="吃啥 · 列表",
        concl="筛选区占首屏约 25%，9 家店的核心任务「快速浏览」被挤压；「未定位」是数据状态却混在类型 chips 里。",
        problems=[
            ("P1", "标题+排序+搜索+类型行+标签行占首屏 ~25%，首屏只剩 3.5 张卡"),
            ("P1", "「未定位」混在类型 chips，语义错位（它是状态不是类型）"),
            ("P2", "排序入口只在顶部，滚动后不可达"),
        ],
        shot="eats-04-list.png", wire="wf-04-eats-list.png",
        points=[
            (1, "全部收纳为单行工具条：搜索、排序、筛选（带角标）"),
            (2, "首屏 5–6 张卡直达浏览"),
            (3, "筛选收进底部弹层，应用后按钮带角标计数"),
        ]),
    dict(app=EATS, no="E5", title="吃啥 · 食堂详情",
        concl="「记一笔」是全 App 最高频动作却不吸底——记录一多，每次记一笔都要先滚回页面中部。",
        problems=[
            ("P0", "主按钮「＋记一笔今天吃了」不吸底：4 条记录已需滚动，高频动作被埋"),
            ("P1", "评分语义混乱：顶部星级（本次？均分？）+「均分 4.3」+记录行星级，三处关系读不懂"),
            ("P2", "每条链接旁跟一个「打开链接」辅助按钮，冗余"),
        ],
        shot="eats-05-detail.png", wire="wf-05-eats-detail.png",
        points=[
            (1, "主操作吸底常驻：＋记一笔今天吃了"),
            (2, "统计行只保留一套口径（均分 / 次数 / 上次）"),
        ]),
    dict(app=EATS, no="E6", title="吃啥 · 记一笔弹层",
        concl="落账按钮必被软键盘遮挡（同因实测：列表页搜索框键盘甚至把底部导航顶飞）。",
        problems=[
            ("P0", "落账贴弹层底部，点花费/感想弹键盘后按钮被盖住，需先收键盘再落账，流程断裂"),
            ("P1", "grab handle 弱，弹层高度语义不明"),
        ],
        shot="eats-06-log.png", wire="wf-06-eats-log.png",
        points=[
            (1, "imePadding：操作随键盘上移，永不被遮挡"),
        ]),
    dict(app=EATS, no="E7", title="吃啥 · 添加食堂表单",
        concl="9 字段长表单，保存只在顶栏——填到底部要滚回顶部才能保存；必填校验与按钮可用态脱节。",
        problems=[
            ("P0", "保存按钮只在顶栏右上；灰色是禁用还是样式无提示，用户填完不知去哪保存"),
            ("P1", "必填项（名称）无 * 标识，事后报错才补救；键盘遮挡（同 C3）"),
        ],
        shot="eats-07-addform.png", wire="wf-15-eats-form.png",
        points=[
            (1, "吸底保存操作栏：未就绪写明原因，就绪变实心"),
            (2, "必填字段带 *，照片区空态即引导"),
            (3, "与衣橱 R7 共用同一改版模式（两 App 同步）"),
        ]),
    # ===== 衣橱 =====
    dict(app=WRD, no="R1", title="衣橱 · 搭配页（首页）",
        concl="真人比例布局有换装游戏雏形（趣味 60% / 杂乱 40%），但核心交互「格内滑动换衣」零线索——实测可用、肉眼不可发现。",
        problems=[
            ("P0", "格内左右滑动换衣无任何 affordance：「1/3」序号不构成可翻提示"),
            ("P1", "卡片尺寸碎片化：外套窄高卡/上装方卡/下装长条/挂件小卡，比例失衡缝隙不均"),
            ("P1", "衣物照片背景五花八门（纸袋/衣架/卧室/木板），与浅色卡片风格冲突"),
            ("P2", "「复制长图」与「保存这套」主次不分；角色切换入口 affordance 弱"),
        ],
        shot="wardrobe-01-outfit.png", wire="wf-08-wardrobe-outfit.png",
        points=[
            (1, "序号胶囊改 ‹ n/n ›，明示可翻"),
            (2, "首次进入每格做 150ms 左右微移示意（仅首轮）"),
            (3, "复制长图=实心主按钮，保存这套=描边次按钮"),
        ]),
    dict(app=WRD, no="R2", title="衣橱 · 角色切换弹层",
        concl="家庭多角色场景（可能有长辈使用）的当前态只靠一个小绿点表达，识别成本过高。",
        problems=[
            ("P0", "当前角色仅一个 ~6px 绿点：无文字、无选中底色，色弱/年长用户无法识别"),
            ("P1", "「新建角色」「管理」两入口主次不清"),
        ],
        shot="wardrobe-02-roles.png", wire="wf-12-roles.png",
        points=[
            (1, "当前行浅蓝底 + 「✓ 使用中」文字"),
            (2, "新建=实心主按钮，管理=文字入口"),
        ]),
    dict(app=WRD, no="R3", title="衣橱 · 穿搭记录卡组",
        concl="it-010 分段拼贴解决了溢出，但四件单品错落如「物品陈列」，读不出人体顺序；缺鞋时无法区分「没配鞋」还是「被裁掉」。",
        problems=[
            ("P0", "卡面拼贴无人体叙事：帽/T恤/包/裤位置随机，第一眼不像一套穿搭"),
            ("P0", "缺失品类不可见（无空槽表达）；卡组同样无可滑动线索"),
            ("P1", "「随机一套」在此页语义模糊：随机翻看一套？还是随机生成搭配？"),
        ],
        shot="wardrobe-03-records.png", wire="wf-09-wardrobe-card.png",
        points=[
            (1, "淡色人形轮廓底（头/躯干/腿/脚剪影），照片落位=穿在身上"),
            (2, "缺失品类显示虚线空槽「未配鞋」，一眼可见"),
            (3, "‹ 2/5 › 卡序常驻，卡组可滑动可视"),
        ]),
    dict(app=WRD, no="R4", title="衣橱 · 穿搭详情",
        concl="无成品图时整页没有主视觉，详情沦为纯文字清单；且「录入成品图」入口重复出现两次。",
        problems=[
            ("P0", "顶部仅弱样式虚线占位，无任何视觉主体；录入入口一个带说明一个不带，语义重复"),
            ("P0", "「这套包含」单品行不可点（spec 应跳衣物详情），无 chevron 无按压态"),
        ],
        shot="wardrobe-04-record-detail.png", wire="wf-10-wardrobe-record.png",
        points=[
            (1, "拼贴（同 W8 卡面组件）作默认主视觉，入口合并为右下角标"),
            (2, "单品行加 › 与按压态，直达衣物详情"),
        ]),
    dict(app=WRD, no="R5", title="衣橱 · 衣橱列表",
        concl="it-009 品类 Tab 与标签 chips 双行叠在标题下，首屏只能看到 2–3 件衣物——浏览才是本页核心任务。",
        problems=[
            ("P1", "双行筛选占首屏 25–30%；行内右上角品类小标与分组标题双重冗余"),
            ("P1", "缩略图底色/形状不统一（同 C5 照片容器问题）"),
        ],
        shot="wardrobe-05-wardrobe.png", wire="wf-13-wardrobe-list.png",
        points=[
            (1, "品类 Tab 保留单行，标签收进「筛选」（角标计数）"),
            (2, "列表改两列卡片网格：首屏 4–6 件，去掉行内品类小标"),
        ]),
    dict(app=WRD, no="R6", title="衣橱 · 衣物详情",
        concl="单品照片原始背景直出（牛皮纸袋/衣架/卧室/木板），是全 App 照片质感问题的集中暴露点。",
        problems=[
            ("P0", "大图无统一容器：背景、明暗、比例各异，随手拍感强"),
            ("P1", "「相关穿搭」缩略图小、点击预期弱；评论区与时间线间距松散"),
        ],
        shot="wardrobe-06-item-detail.png", wire="wf-14-item-detail.png",
        points=[
            (1, "统一浅底容器：固定比例/圆角/底色，无论原图背景如何"),
            (2, "相关穿搭放大为可点卡片，「点开看整套」预期明确"),
        ]),
    dict(app=WRD, no="R7", title="衣橱 · 添加衣物表单",
        concl="与吃啥表单完全同构的问题：保存只在顶栏、照片必填无标识——建议一次改造两 App 共用。",
        problems=[
            ("P0", "保存仅顶栏一处，8 品类+5 字段长表单滚到底需滚回顶保存"),
            ("P1", "照片必填无 * 标识与空态引导；键盘遮挡（同 C3）"),
        ],
        shot="wardrobe-07-addform.png", wire="wf-07-form-save.png",
        points=[
            (1, "吸底保存两态（同 E7 改版模式，两 App 同步）"),
            (2, "照片区必填 * + 选完即亮的保存反馈"),
        ]),
    dict(app=WRD, no="R8", title="衣橱 · 导出面板（核心工作流）",
        concl="全 App 价值出口被五维 chips 淹没：高频路径「打开→复制→走」要滚 2 屏，而多数时候维度不需要改。",
        problems=[
            ("P0", "场景/氛围/季节/光线/构图五维全量铺开，主按钮沉底，高频路径滚 2 屏"),
            ("P1", "Prompt 文案区双层卡片嵌套、对比度低——而它恰是导出的灵魂；复制成功无 snackbar 反馈"),
        ],
        shot="wardrobe-08-export.png", wire="wf-11-export.png",
        points=[
            (1, "打开即见长图预览，只留「场景」常驻"),
            (2, "其余四维折叠 + 记住上次选择"),
            (3, "Prompt 深底等宽高对比，单层容器"),
            (4, "复制/分享首屏即达，复制后 snackbar「已复制」"),
        ]),
]

commons = [
    ("C1", "滑动/卡组交互无可发现性线索", "E1 · R1 · R3", "P0", "堆叠露边 + ‹n/m›页码 + 首次coach动画"),
    ("C2", "保存只在顶栏、必填无标识", "E7 · R7", "P0", "吸底保存两态 + 必填 *"),
    ("C3", "键盘避让缺失（imePadding）", "E6 · E7 · R7 · 列表搜索", "P0", "全表单/弹层接 imePadding"),
    ("C4", "核心动作不吸底（记/存/复制）", "E5 · E7 · R7 · R8", "P0", "吸底主操作条"),
    ("C5", "照片原始背景直出无容器", "R1 · R5 · R6", "P1", "统一浅底容器（后续可接抠图）"),
    ("C6", "筛选区占首屏 25%+", "E4 · R5", "P1", "单行收纳 + 弹层筛选"),
]

tests = [
    ("类型筛选生效", "点掉堂食/自做后卡组即时只剩外卖候选（猪脚饭）——逻辑正确，仅缺候选数反馈"),
    ("键盘顶飞底部导航", "列表页点搜索框弹键盘，底部导航被推出屏幕、点击失效（C3 实证）"),
    ("格位滑动可用", "搭配页上装卡左滑：白T恤 1/3 → 球衣 2/3——交互在，线索无（C1 实证）"),
    ("抽取落定按钮不可见", "抽取动画结束 9 秒后 uiautomator 树中只有「就吃 红烧排骨？」文字，无确认按钮节点"),
    ("marker 配色核对", "堂食=0xFF2E9C8A 青绿 / 外卖=0xFF4E9BD8 蓝 / 自做=0xFF8CBE4F 草绿，全挤冷色区，与 spec 红/琥珀/绿不符"),
    ("随机一套工作正常", "W1 点「随机一套」：外套→飞行夹克、上装→牛津纺衬衫，槽位刷新正确"),
]

# ---------------- 模板 ----------------
CSS = """
@page { size: 210mm 297mm; margin: 0; }
html, body { margin: 0; padding: 0; width: 210mm; background: #F7F8FA;
  font-family: "Hiragino Sans GB","PingFang SC","Heiti SC",sans-serif;
  color: #1D2129; line-break: strict; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
* { box-sizing: border-box; }
.page { width: 210mm; height: 297mm; overflow: hidden; position: relative;
  padding: 12mm 14mm 10mm; background: #F7F8FA; break-after: page;
  display: flex; flex-direction: column; }
.page:last-child { break-after: auto; }
img { display: block; }

/* ---------- 封面 / 分节页（纯流式 flex，不用 absolute） ---------- */
.cover { padding: 18mm 16mm 16mm; background: #FFFFFF; }
.cover .kicker { font-size: 10pt; letter-spacing: 3pt; color: #86909C; font-weight: 600; }
.cover .hairline { width: 30mm; height: 0; border-top: 0.7mm solid #2F6BFF; margin-top: 7mm; }
.cover h1 { font-size: 40pt; font-weight: 800; letter-spacing: 1pt; margin: 24mm 0 0; }
.cover h1.mid { font-size: 34pt; margin-top: 30mm; }
.cover .sub { font-size: 14pt; color: #4E5969; margin-top: 7mm; }
.cover .summary { font-size: 10.5pt; line-height: 1.75; color: #4E5969;
  margin-top: 20mm; width: 104mm; }
.cover .phones { display: flex; gap: 6mm; margin-left: auto; margin-top: auto; }
.cover .phone { width: 34mm; height: 75.6mm; border: 0.5mm solid #C9CDD4; border-radius: 4.5mm;
  padding: 2.2mm; background: #FFFFFF; }
.cover .phone img { width: 100%; height: 100%; object-fit: cover; border-radius: 2.5mm; }
.cover .meta { display: flex; gap: 10mm; font-size: 9.5pt; color: #86909C;
  border-top: 0.3mm solid #E5E6EB; padding-top: 6mm; margin-top: 12mm; }
.cover .meta b { color: #1D2129; font-weight: 600; }

/* ---------- 页眉页脚 ---------- */
.phead { display: flex; align-items: baseline; gap: 4mm; border-bottom: 0.45mm solid #E5E6EB;
  padding-bottom: 3.5mm; margin-bottom: 4mm; }
.phead .no { font-size: 13pt; font-weight: 800; color: #2F6BFF; letter-spacing: 0.5pt; }
.phead .t { font-size: 14.5pt; font-weight: 700; }
.phead .sev { margin-left: auto; font-size: 8.5pt; color: #86909C; }
.pfoot { display: flex; font-size: 8pt; color: #A9AEB8;
  border-top: 0.3mm solid #E5E6EB; padding-top: 2.5mm; margin-top: auto; }

/* ---------- 主题页 ---------- */
.concl { font-size: 10pt; line-height: 1.65; color: #1D2129; margin: 0 0 4mm; }
.concl b { color: #F53F3F; }
.problems { display: flex; flex-wrap: wrap; gap: 1.6mm 4mm; margin-bottom: 4.5mm; }
.prob { width: calc(50% - 2mm); font-size: 8.8pt; line-height: 1.5; color: #4E5969; }
.tag { display: inline-block; font-size: 7.5pt; font-weight: 700; color: #fff; border-radius: 1.2mm;
  padding: 0.3mm 1.6mm; margin-right: 1.6mm; vertical-align: 0.3mm; }
.tag.p0 { background: #F53F3F; } .tag.p1 { background: #FF7D00; } .tag.p2 { background: #86909C; }
.shots { display: flex; gap: 6mm; align-items: flex-start; flex: 1; }
.shotcol { width: 76mm; }
.shotcol img { width: 76mm; height: 168.9mm; object-fit: cover; object-position: top;
  border: 0.3mm solid #E5E6EB; border-radius: 2.5mm; background: #fff; }
.shotlabel { display: flex; align-items: center; gap: 2mm; margin-top: 2.2mm;
  font-size: 9pt; font-weight: 700; }
.shotlabel .dot { width: 2.6mm; height: 2.6mm; border-radius: 50%; }
.d-now { background: #F53F3F; } .d-new { background: #2F6BFF; }
.shotlabel span.cap { font-weight: 400; color: #86909C; font-size: 8pt; margin-left: auto; }
.points { flex: 1; display: flex; flex-direction: column; gap: 2.6mm; }
.pt { font-size: 8.8pt; line-height: 1.5; color: #4E5969; }
.pt .n { display: inline-flex; width: 5mm; height: 5mm; border-radius: 50%; background: #2F6BFF;
  color: #fff; font-size: 8pt; font-weight: 700; align-items: center; justify-content: center;
  margin-right: 1.8mm; vertical-align: -1mm; }

/* ---------- 表格 ---------- */
table { border-collapse: collapse; width: 100%; }
th { font-size: 9pt; text-align: left; color: #86909C; font-weight: 600;
  border-bottom: 0.45mm solid #C9CDD4; padding: 2mm 2.5mm; }
td { font-size: 9.3pt; line-height: 1.5; padding: 1.9mm 2.5mm; border-bottom: 0.3mm solid #E5E6EB;
  vertical-align: top; }
td.c { text-align: center; }

/* ---------- 总览页 ---------- */
.h2 { font-size: 13pt; font-weight: 800; margin: 4.5mm 0 2.5mm; }
.h2:first-child { margin-top: 0; }
.lead { font-size: 10pt; line-height: 1.75; color: #4E5969; }
.statrow { display: flex; gap: 5mm; margin: 5mm 0; }
.stat { flex: 1; background: #fff; border: 0.3mm solid #E5E6EB; border-radius: 2.5mm; padding: 4mm; }
.stat .v { font-size: 22pt; font-weight: 800; color: #2F6BFF; }
.stat .l { font-size: 8.5pt; color: #86909C; margin-top: 1mm; }
.mrow { display: flex; gap: 4mm; margin: 2mm 0; }
.mcard { flex: 1; background: #fff; border: 0.3mm solid #E5E6EB; border-radius: 2.5mm; padding: 3.5mm; }
.mcard b { font-size: 9.5pt; display: block; margin-bottom: 1.5mm; }
.mcard p { margin: 0; font-size: 8.6pt; line-height: 1.6; color: #4E5969; }
.appendix-shot { display: flex; gap: 5mm; }
.appendix-shot .acol { flex: 1; text-align: center; }
.appendix-shot img { width: 39.6mm; height: 88mm; object-fit: cover; object-position: top;
  border: 0.3mm solid #E5E6EB; border-radius: 2mm; margin: 0 auto; }
.appendix-shot .cap { font-size: 7.5pt; color: #86909C; margin-top: 1.5mm; line-height: 1.45; }
"""

def esc(s): return html.escape(s, quote=False)

def sev_counts(th):
    c = {"P0": 0, "P1": 0, "P2": 0}
    for s, _ in th["problems"]: c[s] += 1
    parts = [f"{k}×{v}" for k, v in c.items() if v]
    return " ".join(parts)

def theme_page(th, pageno):
    probs = "".join(
        f'<div class="prob"><span class="tag {p[0].lower()}">{p[0]}</span>{esc(p[1])}</div>'
        for p in th["problems"])
    pts = "".join(
        f'<div class="pt"><span class="n">{n}</span>{esc(t)}</div>'
        for n, t in th["points"])
    appname = "吃啥" if th["app"] == EATS else "衣橱"
    return f"""
<section class="page">
  <div class="phead">
    <span class="no">{th['no']}</span><span class="t">{esc(th['title'])}</span>
    <span class="sev">{sev_counts(th)}</span>
  </div>
  <p class="concl">{esc(th['concl'])}</p>
  <div class="problems">{probs}</div>
  <div class="shots">
    <div class="shotcol">
      <img src="{A}/{th['shot']}" alt="">
      <div class="shotlabel"><span class="dot d-now"></span>现状截图<span class="cap">模拟器实机 · 2026-09-20</span></div>
    </div>
    <div class="shotcol">
      <img src="{A}/{th['wire']}" alt="">
      <div class="shotlabel"><span class="dot d-new"></span>改版线框<span class="cap">蓝色徽标 = 变更点</span></div>
    </div>
    <div class="points">{pts}</div>
  </div>
  <div class="pfoot"><span>{appname} · UX 评审报告</span><span style="margin-left:auto">{pageno}</span></div>
</section>"""

def build():
    pages = []
    pageno = 1

    # ---- 封面 ----
    pages.append(f"""
<section class="page cover">
  <div class="kicker">MINI-APPS · UX REVIEW</div>
  <div class="hairline"></div>
  <h1>吃啥 × 衣橱</h1>
  <div class="sub">全页面截图走查 · 问题清单 · 改版线框</div>
  <div class="summary">对「吃啥」「衣橱」两款应用共 15 个页面进行实机截图走查与交互实测，
    汇总为按严重度分级的问题清单，并针对每个页面给出灰盒改版线框：
    蓝色数字徽标对应右侧改版要点，与现状截图左右对照。</div>
  <div class="phones">
    <div class="phone"><img src="{A}/eats-01-home.png" alt=""></div>
    <div class="phone"><img src="{A}/wardrobe-01-outfit.png" alt=""></div>
  </div>
  <div class="meta">
    <span>日期 <b>2026-09-20</b></span><span>范围 <b>2 应用 · 15 页面</b></span>
    <span>图版 <b>15 原图 + 15 线框</b></span><span>方案 <b>9 组 · 徽标编号对应</b></span>
  </div>
</section>""")
    pageno += 1

    # ---- 总览与方法 ----
    crows = "".join(
        f"<tr><td class='c'><b>{c[0]}</b></td><td>{esc(c[1])}</td><td class='c'>{esc(c[2])}</td>"
        f"<td class='c'><span class='tag {c[3].lower()}'>{c[3]}</span></td><td>{esc(c[4])}</td></tr>"
        for c in commons)
    pages.append(f"""
<section class="page">
  <div class="phead"><span class="no">00</span><span class="t">总评 · 方法与共性问题</span></div>
  <p class="lead">两款应用信息架构与功能闭环健康，视觉有统一底子；问题集中在三类系统性缺陷——
  <b>滑动交互无可见线索、长表单保存不吸底且键盘无避让、核心动作被埋没</b>。
  下列六项跨应用共性问题建议优先修复（一次改动覆盖多页）。</p>
  <div class="statrow">
    <div class="stat"><div class="v">15</div><div class="l">走查页面（吃啥 7 · 衣橱 8）</div></div>
    <div class="stat"><div class="v">13</div><div class="l">P0 级问题</div></div>
    <div class="stat"><div class="v">14</div><div class="l">P1 级问题</div></div>
    <div class="stat"><div class="v">6</div><div class="l">交互项实测验证</div></div>
  </div>
  <div class="h2">评审方法</div>
  <div class="mrow">
    <div class="mcard"><b>实机截图走查</b><p>Pixel 6 画像模拟器安装最新 debug 包，逐页导航截取 15 张原图（含抽取落定、随机一套等过程态）。</p></div>
    <div class="mcard"><b>视觉模型逐页评审</b><p>每页对照 Airbnb / Things / Linear 质量标准产出 P0/P1/P2 问题清单，再经人工合并去重。</p></div>
    <div class="mcard"><b>交互实测验证</b><p>uiautomator 走查断言 + 源码核对：筛选联动、键盘遮挡、格位滑动、落定按钮可见性、marker 配色（见附录）。</p></div>
  </div>
  <div class="h2">跨应用共性问题（优先修）</div>
  <table>
    <tr><th style="width:9mm">#</th><th>问题</th><th style="width:36mm">涉及页面</th><th style="width:12mm">级别</th><th style="width:52mm">修法（对应线框）</th></tr>
    {crows}
  </table>
  <div class="pfoot"><span>总评</span><span style="margin-left:auto">{pageno}</span></div>
</section>""")
    pageno += 1

    # ---- 分节页（吃啥）----
    def section_divider(label, en, count, pageno, shots):
        imgs = "".join(f'<div class="phone"><img src="{A}/{s}" alt=""></div>' for s in shots)
        return f"""
<section class="page cover">
  <div class="kicker">{en}</div>
  <div class="hairline"></div>
  <h1 class="mid">{label}</h1>
  <div class="sub">{count} 个页面 · 现状与改版线框对照</div>
  <div class="phones">{imgs}</div>
  <div class="meta"><span>UX 评审报告 · 2026-09-20</span><span style="margin-left:auto">{pageno}</span></div>
</section>"""
    pages.append(section_divider("吃啥 · EATS", "PART 01", "7", pageno, ["eats-02-drawn.png", "eats-03-map.png", "eats-05-detail.png"]))
    pageno += 1
    for th in [t for t in themes if t["app"] == EATS]:
        pages.append(theme_page(th, pageno)); pageno += 1
    pages.append(section_divider("衣橱 · WARDROBE", "PART 02", "8", pageno, ["wardrobe-03-records.png", "wardrobe-08-export.png", "wardrobe-01-outfit.png"]))
    pageno += 1
    for th in [t for t in themes if t["app"] == WRD]:
        pages.append(theme_page(th, pageno)); pageno += 1

    # ---- 落地拆分 ----
    pages.append(f"""
<section class="page">
  <div class="phead"><span class="no">→</span><span class="t">落地拆分建议</span></div>
  <p class="lead">按 AGENTS.md spec-driven 流程，确认后先写迭代提案再动工。建议拆两个迭代，
  先修四项共性 P0（都是「功能在但用户够不着 / 猜不到」），照片容器化可等接入抠图能力时一并做。</p>
  <div class="h2">eats / it-004 · 交互补课</div>
  <table>
    <tr><th style="width:14mm">线框</th><th>内容</th><th style="width:20mm">对应</th></tr>
    <tr><td class="c">O1</td><td>首页卡组线索（露边 + ‹n/m›计数）与落定结果块闭环</td><td class="c">E1 · E2</td></tr>
    <tr><td class="c">O2</td><td>地图图例 + 回位按钮 + 配色回归 spec（红/琥珀/绿）</td><td class="c">E3</td></tr>
    <tr><td class="c">O3</td><td>表单吸底保存两态 + 全表单 imePadding（含列表搜索）</td><td class="c">E7</td></tr>
    <tr><td class="c">O4</td><td>详情页主操作吸底</td><td class="c">E5 · E6</td></tr>
    <tr><td class="c">O5</td><td>列表筛选收纳单行化</td><td class="c">E4</td></tr>
  </table>
  <div class="h2">wardrobe / it-011 · 可发现性与叙事</div>
  <table>
    <tr><th style="width:14mm">线框</th><th>内容</th><th style="width:20mm">对应</th></tr>
    <tr><td class="c">O6</td><td>格位 ‹n/n› 胶囊 + 首次 coach 动画 + 按钮主次</td><td class="c">R1</td></tr>
    <tr><td class="c">O7</td><td>人体叙事拼贴组件（W8 卡面 / W7 主视觉复用）+ 单品行可点</td><td class="c">R3 · R4</td></tr>
    <tr><td class="c">O8</td><td>导出面板折叠高级维度 + Prompt 高对比 + 复制反馈</td><td class="c">R8</td></tr>
    <tr><td class="c">O9</td><td>角色切换当前态强化</td><td class="c">R2</td></tr>
    <tr><td class="c">O3</td><td>表单吸底保存（与 eats 同步改造）</td><td class="c">R7</td></tr>
    <tr><td class="c">C5/C6</td><td>列表单行筛选 + 网格化；照片统一容器（或并入 C5 后续迭代）</td><td class="c">R5 · R6</td></tr>
  </table>
  <div class="h2">优先级</div>
  <p class="lead">第一梯队：C1–C4（共 13 处 P0，两 App 一次修完）→ 第二梯队：O1 落定闭环 / O7 拼贴叙事 / O8 导出折叠（各 App 核心体验）→
  第三梯队：C5 照片容器（建议与抠图能力一起）、C6 筛选收纳。</p>
  <div class="pfoot"><span>落地拆分</span><span style="margin-left:auto">{pageno}</span></div>
</section>""")
    pageno += 1

    # ---- 附录 ----
    trows = "".join(f"<tr><td><b>{esc(a)}</b></td><td>{esc(b)}</td></tr>" for a, b in tests)
    pages.append(f"""
<section class="page">
  <div class="phead"><span class="no">A</span><span class="t">附录 · 交互实测记录与资产</span></div>
  <div class="h2">六项实测验证（uiautomator / 源码核对）</div>
  <table>
    <tr><th style="width:34mm">项目</th><th>结论</th></tr>
    {trows}
  </table>
  <div class="h2">实证截图</div>
  <div class="appendix-shot">
    <div class="acol"><img src="{A}/eats-02b-drawn-verify.png" alt="">
      <div class="cap">抽取落定 9 秒后：仅见「就吃 XX？」一行小字贴在底部导航上方，确认按钮不在可视区（E2-P0 实证）</div></div>
    <div class="acol"><img src="{A}/wardrobe-09-slot-swiped.png" alt="">
      <div class="cap">搭配页上装卡左滑实测：白T恤 1/3 → 球衣 2/3，格位滑动可用但无可见线索（R1-P0 / C1 实证）</div></div>
    <div class="acol"><img src="{A}/eats-08-filtered.png" alt="">
      <div class="cap">类型筛选实测：仅留外卖后卡组即时切换为外卖候选——逻辑正确，缺候选数反馈（E1-P0 实证）</div></div>
  </div>
  <div class="h2">资产清单</div>
  <p class="lead" style="font-size:9pt">原图 15 张（assets/ 前缀 eats- / wardrobe-）、改版线框 14 张（wf-01 ~ wf-14，tools/draw_wireframes.py 生成）、
  证据截图 3 张；本报告 report.html 由 tools/build_report.py 生成，可改数据后重出。</p>
  <div class="pfoot"><span>附录</span><span style="margin-left:auto">{pageno}</span></div>
</section>""")

    doc = f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<title>吃啥 × 衣橱 · UX 评审报告 · 2026-09-20</title>
<style>{CSS}</style>
</head>
<body>
{''.join(pages)}
</body>
</html>"""
    out = os.path.join(BASE, "report.html")
    with open(out, "w", encoding="utf-8") as f:
        f.write(doc)
    print("written", out, len(doc), "bytes")

if __name__ == "__main__":
    build()
