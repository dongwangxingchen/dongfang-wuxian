# 东方无限 View UI 设计规范 v2（2026-09-22）

> 依据《20260922-tryallai深度研究-View质感开源项目盘点》（`黑曜/05-调研报告/`）重写。动效参数来自
> Wear M3 Expressive MotionScheme 官方源码值，仓库星数经独立核验；替代 v1 的自拟数值。
> 适用范围：主 UI（Java/Kotlin 代码构建 View 体系，无 XML）。Compose AI 页通过"视觉桥五 token"对齐（§8）。

## 0. 总原则

1. 廉价感六根源：运动曲线、按压反馈、圆角体系、文本层级、图标笔画、表面明度层级——全部按本规范系统化，不再逐页手调。
2. 视觉目标：OLED 真黑底 + 少量带色透明表面 + 连续弹簧运动 + 明确按压形变 + 克制高光边缘 + 强字阶 + 单一图标语言。
3. 任何视觉修改必须优先改 token 或可复用 View，禁止页面内 hardcode。
4. 禁第三方依赖默认不破例；开源项目当"参数库/算法库/审美样本"用（裁决表见 §10）。

## 0.5 适用范围修订（2026-09-30，DFW-5）

> **手表尺寸约束已停用。** 用户 2026-09-24 拍板「**只测手机，手表测试永久停止**」（`docs/plan/decisions.md` #13），
> 本规范 v2 写作时依据的是 **Wear M3 Expressive MotionScheme**，其中的圆屏/手表尺寸部分（本文档标题里的
> "View UI"、原 §1 中与 Wear 相关的尺寸约束、以及历史上"圆屏 204–216dp / 192dp 苛刻检查"那套口径）
> **不再作为验收依据**——本应用现在只按手机视口验收。
>
> **仍然有效、继续作为唯一真相源的部分**：
> - §2 MotionTokens（M3E 官方弹簧值）
> - §3 按压反馈
> - §4 形状四档（圆角体系）
> - §5 OLED 真黑亮度阶梯
> - §6 中文字阶
> - §7 图标语言
> - §8 Compose 视觉桥五 token
>
> 换言之：**参数与 token 照用，尺寸与验收口径按手机**。
> 文档标题里的 "wear" 是历史命名，为避免批量改旧文件（归档 T12 明确禁止）予以保留，
> 但读者应以本节为准。另：无障碍字号适配见 DFW-13（`dpText()`）。

## 1. 页面 archetype（沿 v1）

新 UI 先问属于哪类，不许自由发明结构：
- `ListScreen` 软件库 / 下载 / 设置列表
- `ToolScreen` 工具箱单功能页
- `DetailScreen` 软件详情 / 下载详情
- `Conversation` AI 对话（vendored RikkaHub/Compose 岛屿）

圆屏从 ~204–216dp 起设计，192dp 做苛刻检查；边距用相对百分比；可点区域 ≥48×48dp；关键文本 ≥12sp 且过 font scale。

## 2. 动效 Token（MotionTokens）——M3E Wear 官方物理值

两族：**spatial**（位置/尺寸/形状，允许轻微 overshoot）与 **effects**（颜色/alpha，damping=1.0 禁 overshoot）。

| Token | stiffness | dampingRatio | View 用途 |
|---|---|---|---|
| SPATIAL_DEFAULT | 350f | 0.75f | 按钮释放、选中恢复、卡片位移 |
| SPATIAL_FAST | 800f | 0.70f | 快速小位移、图标/形态切换 |
| SPATIAL_SLOW | 200f | 0.80f | 页面 hero、大容器位移 |
| EFFECT_DEFAULT | 500f | 1.00f | 色彩/alpha 常规变化 |
| EFFECT_FAST | 1400f | 1.00f | press-down、即时反馈 |
| EFFECT_SLOW | 260f | 1.00f | 背景色/emphasis 淡化 |

- 实现：androidx.dynamicanimation SpringForce（官方 androidx，本规范建议按"a 类"引入；若铁律也禁它，自写 ~50 行二阶弹簧积分器，方程见报告）。
- PathInterpolator 等效表（官方换算，无弹簧处用）：fast spatial (0.42,1.67,0.21,0.90) 350ms；default spatial (0.38,1.21,0.22,1.00) 500ms；slow spatial (0.39,1.29,0.35,0.98) 650ms；effects 150/200/300ms 对应 (0.31,0.94,0.34,1.00)/(0.34,0.80,0.34,1.00)/(0.34,0.88,0.34,1.00)。
- 禁止：`scale 1→.9→1.1→1` 三段人为 bounce。正确模型是物体从受压状态由弹簧回到平衡。
- 展开收起：TransitionManager/ChangeBounds；拖拽回弹：SpringAnimation；MotionLayout 仅限 seekable/touch-driven 场景。

### 2.1 官方 MDC 34.0.0 实测值（2026-09-30 核验，来源见参考库 `06/07-更新与公告UI动效专档.md`）

**先说结论：上表的弹簧值保持不变。** MDC 手机版另有一套 spring scheme（expressive 800/0.6、380/0.8、200/0.8；standard 1400/0.9、700/0.9、300/0.9），
damping 0.6 比我们的 0.70 **更弹**，与"禁三段 bounce"的取向相反；effects 也从 500/1400/260 变成 3800/1600/800（提速近 7 倍）。
换过去等于**全 app 动效重调 + 真机重验**，收益不明。**仅记为"可选升级候选"，不动现值**（现值 v1.22.3 已真机验证）。

**时长刻度**（MDC `motion_duration` 全档，做新组件时按语义取）：
`short1 50 / short2 100 / short3 150 / short4 200 / medium1 250 / medium2 300 / medium3 350 / medium4 400 / long1 450 / long2 500 / long3 550 / long4 600 / extraLong1 700 / extraLong2 800 / extraLong3 900 / extraLong4 1000`（ms）

**官方缓动四点**（PathInterpolator，无弹簧处用）：
| 名称 | 四点 | 用途 |
|---|---|---|
| standard | (0.2, 0, 0, 1) | **默认**（`LumaSwitch` 用的就是它） |
| standardDecelerate | (0, 0, 0, 1) | 入场 |
| standardAccelerate | (0.3, 0, 1, 1) | 退场 |
| emphasized | 两段路径 `M 0,0 C 0.05,0 0.133333,0.06 0.166666,0.4 C 0.208333,0.82 0.25,1 1,1` | 强调动作，**不是四点式** |
| emphasizedDecelerate | (0.05, 0.7, 0.1, 1) | 强调入场 |
| emphasizedAccelerate | (0.3, 0, 0.8, 0.15) | 强调退场 |
| linear | (0, 0, 1, 1) | 仅进度类 |

**新增 token（本规范此前没有，做新组件时按此取值）**：
- **对话框/弹窗**：入场 `300ms`、退场 `200ms`，配 standard 缓动。
- **容器变形（卡片→详情）**：进 `500ms` / 退 `400ms`。
- **sharedAxis 位移**：`30dp`（本项目早期写 24–32dp，收敛到 30dp）。
- **遮罩 scrim**：`alpha 0.32`（配真黑底，不要更黑——OLED 下会与页面糊成一片）。
- **未读角标几何**：直径 `16dp`、圆点 `6dp`、数字 `11sp`、锚点偏移 `1.5–12dp`。
  底色**用 `ThemeEngine` 的错误红，不用 `colorError` 系统值**（保证与本站主题同源；MDC 的 badge 实现里根本没有动画，官方无现成可抄）。
- **自检项**：**入场时长 ≥ 退场时长**。反过来会显得"进得急、走得慢"，是廉价感来源之一。

**已知不一致（记录在案，未擅自改）**：`MainActivity.java:1270` 的 chevron 旋转弹簧是 `800/0.75`，
而上表 `SPATIAL_FAST` 是 `800/0.70`。**规范是唯一真相 → 应改为 0.70**，但这会轻微改变已有真机验证过的手感，
故留待动效统一收尾时一并处理，不单独改。

## 3. 按压反馈（PressFeedbackController）——第一优先级

所有可点组件统一：

```
DOWN:   scale 1.00→0.94（圆按钮）/ 0.96（大卡片长胶囊）；surface 提亮 +2~4%；不做整体压暗；不震
UP:     scale →1.00，spring 350/.75；确认动作触发一次 haptic（View.performHapticFeedback）
CANCEL: scale →1.00，无 haptic
```

- ripple 降级为辅助（低 alpha）；主反馈 = scale + tint + haptic；不用灰 ripple 当主反馈。
- 触觉只传达"状态真的变了"，不做滚动装饰震。

## 4. 形状 Token（ShapeTokens）——废除 4/8/12dp 旧体系

```
Circle = 50%    Pill = height/2    Card = 20dp    Panel = 26–30dp
```

- 圆屏同构：外层更圆（panel≈28dp）→ 内层次之（card 20dp）→ 按钮 pill/circle → 图标底 circle。
- 尺寸：主圆按钮 48–52dp；次级圆按钮视觉 36–40dp、触控区 ≥48dp；胶囊高 44–48dp；信息卡片 radius **20dp（Card token 唯一值；研究给的 18–24 是允许微调带，取中值入 token，不另设档）**；卡片间距主节奏 8dp、大分组 12–16dp；细线 0.5–1dp 低 alpha；加载环 stroke 2–2.5dp 自绘（弃默认 ProgressBar）。

## 5. 表面与颜色（OLED 亮度阶梯）——不用 elevation 阴影

| 层级 | 起点 |
|---|---|
| Screen | #000000（真黑，节能且合理） |
| Surface Low / High | 白或品牌色 5–7% / 8–10% 叠加（High 至 11–14%） |
| Selected | 品牌色 16–24% tint |
| 普通描边 | 白 8–12%；高光/玻璃描边 白 18–26%，只出现一侧 |
| Secondary Text | 60–72%；Primary Text 92–100%；Disabled 38–45% |

- PremiumSurfaceDrawable：baseFill（带色半透明）+ stroke 0.5–1dp + topHighlight（左上→右下极弱渐变）+ pressedFill（+2~4% luminance）+ selectedTint + cornerMode(pill/roundRect/circle)。
- 现有 ThemeEngine 11 色扁平 Design 升级为 semantic token：surfaceLow/High/selected/stroke/highlight/pressed + 色。
- 毛玻璃分层：API 31+ 仅 hero surface 用 RenderEffect（或经用户批准解禁 BlurView，radius 14–20dp 起试，只用于底部操作胶囊/弹层）；API 26–30 用 faux glass：8–12% tinted fill + 0.5/1dp 不对称高光 + 5–8% 内径向辉光。纯黑圆表上"有 blur"本身不是目标。

## 6. 中文字阶（TypographyTokens）——用字重做层级，不用字号

| 角色 | size/行高 sp | 字重 | 用途 |
|---|---|---|---|
| Hero Number | 24–30 / 28–34 | 600–700 | 时间、数值、AI 核心状态 |
| Page Title | 18–20 / 22–24 | 620–700 | 页面标题 |
| Item Title | 15–16 / 19–21 | 580–650 | 列表主文案 |
| Body | 13.5–14.5 / 18–20 | 450–520 | 正文 |
| Label | 12–13 / 15–17 | 550–650 | 按钮/状态（按钮字重 ≥550） |
| Micro | 11–12 / 14–16 | 500–600 | 极少量辅助 |

- 标题靠 weight 不靠放大；次级信息先减 alpha 再减字号；数字可比正文更轻更大。
- 字体：**Source Han Sans / Noto Sans SC（已批准内嵌，2026-09-22 用户拍板）——3500 常用字子集 2-4MB，variable TTF，构建期 pyftsubset 离线子集化，产物进 assets；系统 sans 栈保留为降级保底**；装饰场景（诗句/签名/splash）可用霞鹜文楷，正文禁用。主 UI 与 Compose AI 页字重结构必须同源。

## 7. 图标与微组件（自绘，零依赖）

- 图标单一语言：Material Symbols / Lucide / Tabler / Phosphor 四选一 → 挑实际用的 30–50 个 → 统一 viewBox/stroke → 转 VectorDrawable 入 res/drawable。glyph 20–22dp、主动作 22–24dp；stroke/weight 全 app 1–2 档；selected 用 fill/weight/scale。禁混排 filled/framework/emoji/自制线稿。
- **默认起步 = Material Symbols（filled 档）**：圆屏小尺寸最成熟、黑底实心笔画最清晰、与 M3E 动效语言同源；最终选择由风格试验页（试衣间）截图对比后用户拍板。试衣间用**同一批 30-50 个图形概念**在四个库间换装，切换只改资产不改布局，挑选成本最低。
- **SF Symbols（苹果图标）禁止直接使用**：苹果授权仅限其自家平台，安卓 app 使用属侵权；只允许"学样子"（笔画粗细、面积、光学平衡）。想要 SF Symbols 气质的合法替代是 Phosphor（MIT，圆润+光学平衡最接近）。已核数据：Lucide 24.6k★(ISC)、Tabler 21.8k★(MIT)、Phosphor MIT。
- **材质处理层（glyph × treatment 正交组合，风格试验供用户挑选）**：
  - `flat` 纯色填充/描边——默认，最省电最清晰
  - `glass` 玻璃：半透明填充(白8-12%) + 单侧高光描边 + 底部内反光渐变；苹果 Liquid Glass 图标配方=左上+右下双角辉光，可合法抄配方。**默认 faux 实现（无实时 blur）；实时 blur 仅限 §5 的 hero surface 场景**，避免与 §5 毛玻璃分层规则打架
  - `glow` 发光：多层同色描边渐隐 或 RenderEffect blur 发光层（API 31+）；只给主动作/状态点/选中态点缀，正文图标禁用（OLED 上费电且糊）
  - 处理层做成可参数化 Drawable 包装，同一 glyph 可切 treatment——为风格试验页服务

### 7.5 交互反馈目录（治"点在石头上"）

按下=物理反馈（§3 按压模型），之外每个高频操作配一个"结果感"动效：
- **确认微弹**：操作成功的图标/数字做一次 350/.75 弹簧 overshoot
- **收藏/点赞爆效**：星星/心形弹跳 + 6-10 粒小粒子散开（参考 LikeButton ~7k★ 的弹跳曲线，自绘 ≤100 行）
- **状态呼吸光**：进行中任务的状态点 glow 缓慢呼吸（1.2-1.6s 周期，仅一处）
- **tab/选中切换**：图标 fill 或 weight 连续变形 + 表面 tint 渐变（连续 Float progress，禁瞬切）
- **页面切换三模式**（View 系用 TransitionManager/MotionLayout 实现，Material Motion 官方四模式裁三个）：
  - `sharedAxis` 同级页面前进/后退（位移 24-32dp + fade，方向跟随操作语义）
  - `containerTransform` 卡片→详情（卡片展开成页面，最强"高级感"）
  - `fadeThrough` 无空间关系的切换（旧页淡出+新页淡入+轻微 scale 0.92→1）
- **触觉语义**（AOSP haptics UX 指南）：按下轻震、确认一次标准 click、失败双震、成功长滑可加 tick 序列；全 app 触觉强度统一一档
- ShimmerDrawable（替代已归档的 Facebook Shimmer）：渐变角 35–55°、高光宽 18–24% 元素宽、base alpha 0.04–0.07、highlight alpha 0.10–0.16、单程 700–1000ms、间隔 150–300ms；只用于真正等待的 skeleton，同屏面积要小。
- 状态连续性（Telegram 工程习惯，GPL 只学不抄）：频变视觉状态做成连续 Float progress（0→1）同时驱动 fill/stroke/icon scale/text alpha/corner/translation，杜绝 if/else 瞬切；复用组件自带微型动画控制器（AnimatedValue = lerp + invalidate）。

## 8. Compose AI 页视觉桥（五 token 同源）

primary accent、surface tint、primary/secondary text alpha、shape family、motion personality 与主 UI 同源；禁止"黑底胶囊微弹簧 → 灰容器异圆角异字重"的断裂观感。Compose 侧保持 RikkaHub 结构，只改 theme 的 colorScheme/typography/shape 对齐本规范。

## 9. 截图审查矩阵、12 点评审与 Checklist

截图矩阵（AI 每次改 UI 必走 before/after/critique）：384px 主界面 / 454px 主界面 / pressed / selected / scroll mid / loading / empty / error / AI 页 / View→Compose 转场。圆屏 clipping 审计覆盖 384/396/410/450/454 五档。

12 点评审固定输出：Hierarchy / Typography / Spacing rhythm / Shape consistency / Color 明度层级 / Press affordance / Motion consistency / Round-screen clipping / OLED suitability / Loading-empty-error / 与 RikkaHub 跨界一致性 / Performance risk；按 P0（裁切/不可读/交互歧义）P1（层级/动效/形状不一致）P2（打磨/光学对齐）分级。

迁移冲突警告：web 设计技能的"禁纯黑""禁 bounce"对 OLED Wear **不适用**——真黑节能合理，spatial spring 允许受控 overshoot。移植评审框架，不移植网页规则。

机器可检查 Checklist（每次 UI 改动过一遍）：
- [ ] 视觉层级只有一个主焦点
- [ ] 无 token 外颜色 / 任意圆角（只允许 Circle/Pill/Card/Panel 四档）/ 随机 spacing
- [ ] 主 touch target ≥48dp；圆屏边缘无 clipping
- [ ] 关键文本 ≥12sp，放大字体不截断
- [ ] 列表纵向 rhythm 一致、同级 icon 统一且单一语言
- [ ] 可点组件全部走 PressFeedbackController（0.94/0.96 + spring + 确认态 haptic）
- [ ] 颜色/alpha 动画无 overshoot；spatial 动画用 MotionTokens
- [ ] 与 Roborazzi golden 无非预期差异

## 10. 采用决策表（研究 a/b/c 的裁决）

| 项目 | 裁决 |
|---|---|
| androidx.dynamicanimation（SpringForce） | **a·已批准**（用户 2026-09-22 同意引入）：官方 androidx 动画引擎，所有弹簧动效的基座 |
| Dimezis/BlurView ~4.1k★ Apache-2.0 | **c→a 有条件**：仅图片 hero/动态背景需求出现时，经用户批准解禁 |
| Telegram ~29.9k★ GPL-2.0 | **c only**：学连续 Float 状态与自绘组织，严禁复制源码 |
| facebook Shimmer（已归档）/ Lottie / Rive | **c**：当前不引入；Lottie 留待未来 hero 动画再议 |
| Material Symbols / Lucide / Tabler SVG | **b**：资产 vendor（VectorDrawable），非代码依赖 |
| Source Han Sans / Noto Sans SC | **b**：字体文件 vendor 入 res/font（体积决策交用户） |
| grocy-android / tack-android（Zedler，GPL） | **c**：View 系统精致度参照，学参数不抄码 |

## 11. 落地顺序（与 AGENTS.md 七节衔接）

- 立即（小时级）：MotionTokens → PressFeedbackController → 默认控件视觉清点 → 11 色 token 升级 semantic → ShapeTokens → 图标语言统一 → 字重层级 → ShimmerDrawable → ripple 降级。
- 一周：收敛为 ui/tokens + drawable + behavior + widget 微型设计系统；三实验（blur A/B、五档圆屏 clipping 审计、View↔Compose 视觉桥）；DESIGN.md + 截图评审技能。
- 中期：极小弹簧内核、截图驱动 UI lint（P0/P1/P2）、Compose 岛屿主题对齐。**不做**：重写主 UI 为 Compose。

## 12. 视觉回归门禁（Roborazzi，沿 v1 待接入）

依托现有 Robolectric：`软件库首页 / 软件详情 / AI 对话 / 工具箱 / 设置` 五锚点；每锚点 × 小圆屏/标准圆屏/较大屏 × 默认/放大字体；golden 同 JVM/SDK/字体环境生成；流程：实现 → 渲染 → diff → 本规范 Checklist → 超阈值继续修。
