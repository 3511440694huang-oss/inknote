# 墨笺 InkNote

面向墨水屏与 32 位老设备的本地 Markdown 笔记。把 Obsidian 最常用的那套「文件夹即仓库 + 文件树 + 阅读/编辑 + 双链」搬进一个极轻的原生 App：**零动画、零联网、零原生库**。

![release](https://img.shields.io/github/v/release/3511440694huang-oss/inknote?label=release&color=1a1a1a)
![license](https://img.shields.io/github/license/3511440694huang-oss/inknote?label=license&color=1a1a1a)
![platform](https://img.shields.io/badge/Android-6.0%2B-1a1a1a)
![tests](https://img.shields.io/badge/tests-74%20passed-1a1a1a)
![kotlin](https://img.shields.io/badge/Kotlin-2.0-1a1a1a)

<img src="tools/icon-preview.png" width="520" alt="墨笺图标">

| 项 | 值 |
|---|---|
| 平台 | Android 6.0+（minSdk 23 / targetSdk 34），32 位与 64 位通用 |
| 技术栈 | Kotlin 2.0.21 · Jetpack Compose（BOM 2024.02.00 / Material3）· 纯自研 Markdown 与 LaTeX 渲染 |
| 包名 | `com.inknote.app` |
| 当前版本 | 1.2.0（versionCode 3） |
| 许可证 | [MIT](LICENSE) |

---

## 简介

墨水屏的刷新方式和 LCD 完全不同：全刷会黑闪、局部刷新有残影、动画几乎不可读。墨笺据此重新设计整个交互——没有转场、没有涟漪、没有惯性滚动，所有状态变化瞬时跳变；界面只用五级灰和 1dp 线条，字距行距按书页调过。

在墨水屏上翻页靠的不是手指滑动，而是屏幕边缘的**两个小按钮**：整页上翻 / 下翻，翻完自动对齐到完整段落或整行，所有可滚动界面都能用。

笔记就是 `.md` 文件，进不了数据库、不加密、不联网；APK 里没有任何 `.so` 原生库，32 位老安卓设备照常安装。

---

## 特性

### 仓库与文件

- 内置私有仓库，安装即用（不申请存储权限、不申请网络权限）
- 「打开文件夹…」把任意目录当仓库，走 SAF 持久授权，只读写 `.md` / `.txt` 等文本文件
- 文件夹浏览、面包屑、新建笔记 / 文件夹、重命名、删除、按名称或时间排序
- 侧栏：快速切换、最近打开、恢复内置仓库、设置、关于

### 编辑与阅读

- 编辑 / 阅读一键瞬时切换，无转场；返回与切后台自动保存
- **正文字号 12–40sp 任意整数**：`−` `＋` 逐 1sp 调、长按连续调、点数字直接输入，设置页带实时预览；行距、宋体/黑体、页边距均可调
- 自研 Markdown 渲染：标题（setext / ATX）、粗斜体、删除线、高亮、行内代码、代码块、引用（可嵌套）、分隔线、有序 / 无序 / 任务列表（可嵌套、可多行）、表格（列对齐）、图片占位
- 全仓库全文搜索（带上下文摘录），按名称快速切换

### 链接与双链

- 外链 `[文字](https://…)`、`<自动链接>`、裸 URL：交给系统浏览器，App 自身不需要网络权限
- 站内链接 `[文字](笔记.md)`、笔记内锚点 `[文字](#小节)`、双链 `[[笔记]]` / `[[笔记|别名]]` / `[[笔记#小节]]` / `[[#小节]]`
- `#标签` 直接跳全文搜索；目标笔记不存在时就地新建
- 反向链接：扫描全仓库，列出引用当前笔记的位置

### 数学公式

- 行内 `$…$`、`\(…\)`：线性化为可读 Unicode 内联正文（`x^{2}` → `x²`，`\frac{a}{b}` → `a/b`）
- 块级 `$$…$$`、`\[…\]`：盒模型几何排版 + Canvas 自绘（分式、根号、上下标、`\left( \right)` 自动拉伸、重音、∑∏∫ 上下限堆叠、矩阵、cases）
- 未知命令不再漏反斜杠：退化为命令名文本；解析有字符数与嵌套深度保护，脏输入不会崩

### 墨水屏

- 纸白 / 反色两套五级灰对偶切换
- 全局无动画、无涟漪、无惯性滚动；点击反馈靠形状与边框
- 所有按钮 1dp 细边框、细折线箭头，不糊成黑块

---

## 翻页与内容对齐

- **按钮**：26×44dp、1dp 边框、细折线箭头；到顶 / 到底变灰；长按 0.52s 后每 0.36s 连续翻一页（松手不会多翻一页）
- **位置**：跟随「设置 → 翻页 → 按钮位置」在屏幕左 / 右；内容区自动留出边距，不会被按钮压字
- **覆盖范围**：笔记阅读、笔记编辑、首页文件列表、全文搜索、侧栏、设置页
- **阅读模式**：`LazyColumn` 关闭手势滚动，只接受按钮翻页。每次先按「一屏高度 − 重叠行数」瞬时 `scrollBy`，再向最近的**完整段落块**吸附——屏幕顶部永远是一段话的开头
- **编辑模式**：直接操作 `EditText` 的 `scrollY`，移动后把偏移量**对齐到整行高度**的整数倍——顶部永远是完整的一行
- **列表页**：按「一屏 − 重叠行」滚动后吸附到**完整条目开头**
- 重叠行数可调 0 / 1 / 2 / 3，默认 1 行；行高由字号 × 行距实时换算，所以改字号后翻页仍然对齐

---

## 超链接规则

| 写法 | 行为 |
|---|---|
| `[文字](https://example.com)`、`<https://…>`、裸 `https://…` / `www.…` | 交给系统浏览器 / 对应 App 打开 |
| `[文字](笔记.md)`、`[文字](../目录/笔记.md)` | 按文件名在仓库里找同名笔记并打开（找不到可新建） |
| `[文字](#某个标题)` | 在当前笔记内滚到对应标题 |
| `[[笔记]]`、`[[笔记\|别名]]` | 打开同名笔记 |
| `[[笔记#小节]]` | 打开该笔记并滚到「小节」标题 |
| `[[#小节]]` | 当前笔记内定位 |
| `#标签` | 跳到全文搜索（`#` 后不能是纯数字，避免颜色值误判） |

锚点匹配会忽略 `#`、空格、大小写和 Markdown 标记（`**粗体**` 标题也能匹配），先精确匹配再宽松包含匹配。

---

## 安装

### 直接安装

到 [Releases](https://github.com/3511440694huang-oss/inknote/releases) 下载 `InkNote-<版本>.apk`，用系统文件管理器打开安装。覆盖安装会保留原有笔记与设置。

> 部分墨水屏 ROM 阉割了「安装未知应用」页面。此时请用系统文件管理器打开 APK，或执行 `adb install -r InkNote-<版本>.apk`。

### 从源码构建

环境要求：JDK 17、Android SDK（compileSdk 36）、Gradle 8.11.1。

```bash
# 指定本机 SDK 路径
echo "sdk.dir=/your/android-sdk" > local.properties

bash build.sh                        # release APK
bash build.sh :app:assembleRelease   # 同上（显式）
bash build.sh :app:testDebugUnitTest # 单元测试
```

产物：`app/build/outputs/apk/release/app-release.apk`。

> 在 arm64 设备上就地构建时，AGP 默认的 aapt2 可能无法执行，可用本机 aarch64 版本覆盖：
> `android.aapt2FromMavenOverride=/path/to/aapt2`（写进 `local.properties` 或作为 `-P` 参数）。
> release 与 debug 均使用仓库内的 `keystore/debug.keystore`（标准 debug 签名，无私有密钥），可直接安装验证。

---

## 使用

1. 安装后首次打开会自动生成两篇示例笔记（「欢迎使用墨笺」「翻页与对齐示例」）。
2. 想用自己的目录：侧栏 →「打开文件夹…」→ 选择任意文件夹，之后笔记就是该目录下的 `.md` 文件。
3. 改字号：侧栏 →「设置」→ 显示 → 字号。点 `−` / `＋`、长按连续调、或点数字直接输入（12–40sp），下面的预览框就是实际效果。
4. 左上角 `☰` 打开侧栏；右上角 `＋` 新建笔记 / 文件夹，放大镜搜索。
5. 打开笔记后，右上角在「编辑 / 阅读」之间切换；`⋮` 可重命名、删除、看反向链接、复制全文。
6. 翻页：屏幕边缘的上 / 下小按钮；长按可连续翻。

### 数据放在哪

- 内置仓库：`/sdcard/Android/data/com.inknote.app/files/vault/`（卸载 App 会一并删除，重要笔记请用「打开文件夹」指向自己的目录）
- 外部仓库：你选中的那个目录本身
- 本 App **不申请网络权限**，没有任何数据会离开设备

---

## 项目结构

```
app/src/main/java/com/inknote/app/
├── App.kt                 应用入口 + 服务容器
├── MainActivity.kt        单 Activity，主页 / 笔记 / 设置常驻组合 + 跨笔记锚点
├── data/
│   ├── Settings.kt        设置与最近打开（SharedPreferences + StateFlow，字号夹紧 / 步进）
│   ├── Vault.kt           仓库抽象：FileVault（内置）/ SafVault（外部文件夹）
│   └── VaultManager.kt    仓库切换、首次示例笔记
└── ui/
    ├── theme/Theme.kt     纸面五级灰 / 宋体黑体 / 字号与行距令牌
    ├── Ink.kt             无动画组件库（按钮 / 覆盖层 / 输入框）+ 自绘图标
    ├── Paging.kt          全局翻页按钮 + 列表 / 编辑器的整页滚动与内容对齐
    ├── Markdown.kt        Markdown 解析（块级 + 行内 AST）与渲染（链接 / 公式 / 列表）
    ├── math/              LaTeX：解析 / 排版 / 线性化 / 符号表 / Canvas 绘制
    ├── HomeScreen.kt      文件浏览器 / 侧栏 / 新建 / 重命名 / 删除
    ├── SearchScreen.kt    快速切换 + 全文搜索
    ├── NoteScreen.kt      编辑 / 阅读 / 翻页 / 超链接与锚点 / 反向链接
    └── SettingsScreen.kt  设置页（字号：步进 / 直接输入 / 实时预览）
```

---

## 测试

渲染、解析、仓库与设置层都是纯 Kotlin，可在 JVM 上直接验证（不依赖 Robolectric / 模拟器）：

```bash
bash build.sh :app:testDebugUnitTest
```

| 测试类 | 组数 | 覆盖 |
|---|---|---|
| `MarkdownTest` | 34 | 块级 / 行内解析、链接、列表、表格、公式边界 |
| `MathTest` | 18 | LaTeX 解析与线性化，含「输出不得残留反斜杠 / 美元符」不变式 |
| `RobustnessTest` | 6 | 脏输入模糊测试（超长、未闭合、嵌套爆深） |
| `VaultTest` | 6 | 仓库路径处理与文件名归一 |
| `SettingsTest` | 10 | 字号夹紧 / 步进 / 行高换算 |

当前：**74/74 通过**。

构建产物核对（1.2.0）：APK 6,264,447 字节，`com.inknote.app` / versionCode 3 / minSdk 23 / targetSdk 34，`lib/*.so` 数量为 0，权限只有框架自动生成的 `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`（无网络权限）。

---

## 更新记录

### 1.2.0

- 新增：字号可自定义（12–40sp 任意整数），支持步进、长按连续调、直接输入数字，设置页实时预览
- 改进：正文、标题、表格、代码块、公式、列表标记随字号联动；翻页步长按字号 × 行距实时换算
- 修复：翻页按钮长按后松手会多翻一页（1.1.0 遗留）

### 1.1.0

- 新增：全局翻页按钮（首页 / 搜索 / 设置 / 侧栏 / 阅读 / 编辑），整页翻 + 内容对齐
- 新增：完整超链接体系（外链 / 站内链接 / 锚点 / 双链 / 自动链接 / `#标签` / 反向链接）
- 新增：自研 LaTeX 渲染引擎（行内线性化 + 块级盒模型排版）
- 改进：Markdown 解析器重写（setext 标题、嵌套列表、缩进代码块、表格对齐、硬换行、转义）
- 修复：「切换阅读」按钮无效；翻页按钮过粗

### 1.0.0

- 首个版本：本地 Markdown 仓库、文件树、编辑 / 阅读、五级灰主题、侧栏与搜索

---

## 已知限制

- 图片以「[图片] alt」占位显示，不做位图渲染
- 行内公式是 Unicode 线性化，不是排版级；只有块级公式是真实排版
- 表格列宽等分，单元格内的块级内容按行内处理
- 不做云同步 / 多端；笔记本身就是文件，可自行用 Syncthing、网盘同步
- 无动画是刻意的：翻页、弹层、切换全部瞬时完成

---

## 贡献

欢迎 Issue 与 PR。提交前请确认：

```bash
bash build.sh :app:compileDebugKotlin   # 编译通过
bash build.sh :app:testDebugUnitTest    # 单测全绿
```

代码风格：Kotlin 官方风格、Compose 只依赖官方库、不引入原生库（保持 32 位兼容与零网络权限）。

---

## 许可证

[MIT](LICENSE)

## 致谢

- 图标：Seedream 5.5 生成的扁平笔尖图标（`tools/make_icons.py` 负责切图与自适应图标）
- UI 框架：[Jetpack Compose](https://developer.android.com/jetpack/compose)
- 交互与信息架构参考：[Obsidian](https://obsidian.md/)
- 公式排版参考：[KaTeX](https://katex.org/) 的符号支持表
