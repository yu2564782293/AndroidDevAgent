# Android Dev Agent — 商用级补全方案（v3）

> 从"技术 Demo"到"可商用手机编程 Agent"的完整路线图

---

## 一、现状分析 — 我们有什么、缺什么

### ✅ 已完成（Phase 1-4）
| 模块 | 状态 | 说明 |
|------|------|------|
| Agent Engine 核心循环 | ✅ | Think→Act→Observe |
| Function Calling | ✅ | OpenAI 兼容 |
| 16 个 Agent 工具 | ✅ | 文件/构建/调试/Git/分析 |
| Event Stream | ✅ | 不可变事件流 |
| Stuck Detection | ✅ | 循环检测 |
| Context Condenser | ✅ | 上下文压缩 |
| Android Skills | ✅ | 7 个领域知识模块 |
| 安全策略 | ✅ | 三级确认 |
| 基础对话 UI | ✅ | 事件流展示 |
| 设置页面 | ✅ | API Key/安全级别 |

### ❌ 缺失 — 按优先级排列

---

## 二、P0 — 没有这些就不能用（核心体验）

### 2.1 多模态输入（图片/文件上传）

**问题**：用户只能打字输入任务，无法分享截图、设计稿、错误截图。

**方案**：
```
输入栏改造：
┌─────────────────────────────────────┐
│ 📎 📷  💬 输入指令...        [发送] │
└─────────────────────────────────────┘
  │    │
  │    └── 拍照/相册 → 图片发送给 Agent
  └── 文件选择器 → 选择文件附加到任务
```

**实现要点**：
- 图片上传：调用系统相机/相册 Intent，将图片转为 Base64 发送给多模态 LLM
- 文件上传：SAF（Storage Access Framework）选择文件，读取内容附加到任务上下文
- 支持的图片格式：PNG、JPG、WEBP
- 支持的文件格式：.kt、.java、.xml、.gradle、.txt、.json
- 图片场景：UI 截图 → Agent 识别界面元素；错误截图 → Agent 分析错误信息；设计稿 → Agent 生成对应代码

**新增工具**：
- `analyze_image` — 让 Agent 主动请求查看项目中的图片资源

**新增事件**：
- `ImageMessage` — 图片消息事件
- `FileAttachmentEvent` — 文件附件事件

---

### 2.2 内置代码编辑器

**问题**：Agent 修改了代码，用户无法在 App 内查看/编辑代码，必须切换到其他编辑器。

**方案**：
```
新增页面：CodeEditorScreen
┌─────────────────────────────────┐
│ 📄 MainActivity.kt    [💾][🔄] │
├─────────────────────────────────┤
│  1→ package com.example...     │
│  2→                             │
│  3→ import android.os.Bundle   │
│  4→ import androidx...          │
│  5→                             │
│  6→ class MainActivity : ...    │
│  7→     override fun onCre...   │
│  8→         super.onCreat...    │
│  9→         setContent {        │
│ 10→             // Agent 修改   │ ← 高亮标记 Agent 修改的行
│ 11→             MyTheme { ... } │
│ 12→         }                   │
│ 13→     }                       │
│ 14→ }                           │
└─────────────────────────────────┘
```

**实现要点**：
- 使用 Rosembed/CodeEditor 或自研基于 TextField 的轻量编辑器
- 语法高亮：Kotlin、Java、XML、Gradle
- 行号显示
- Agent 修改标记（高亮显示 Agent 最近修改的行）
- 只读/编辑模式切换
- 从对话界面点击文件路径可跳转到编辑器

**新增导航**：
- `Screen.CodeEditor(path: String)` — 代码编辑器页面

---

### 2.3 文件浏览器面板

**问题**：用户无法直观浏览项目文件结构，只能通过 Agent 的 `list_files` 工具间接查看。

**方案**：
```
新增页面：ProjectBrowserScreen（可从主界面侧滑打开）
┌─────────────────────────────────┐
│ 📁 MyProject              [🔍] │
├─────────────────────────────────┤
│ 📁 app/                        │
│   📁 src/main/                 │
│     📁 java/com/example/       │
│       📄 MainActivity.kt    3m │ ← 显示修改时间
│       📄 MyViewModel.kt     1h │
│     📁 res/                    │
│       📁 layout/               │
│       📁 values/               │
│     📄 AndroidManifest.xml     │
│   📄 build.gradle              │
│ 📄 settings.gradle             │
│ 📄 gradle.properties           │
└─────────────────────────────────┘
```

**实现要点**：
- 树形文件浏览器，懒加载子目录
- 文件图标根据类型区分（.kt/.java/.xml/.gradle/图片/其他）
- 显示最近修改时间
- 点击文件 → 打开代码编辑器
- 长按文件 → 弹出操作菜单（删除、重命名、让 Agent 分析）
- 搜索文件名功能

**新增导航**：
- `Screen.ProjectBrowser` — 项目浏览器页面

---

### 2.4 Diff 查看器

**问题**：Agent 修改了文件，用户只能看到"编辑成功"的文字提示，无法直观看到改了什么。

**方案**：
```
新增组件：DiffViewerDialog
┌─────────────────────────────────┐
│ 📝 Changes in MainActivity.kt  │
├─────────────────────────────────┤
│ - import androidx.appcompat... │ ← 红色背景（删除）
│ + import androidx.compose...   │ ← 绿色背景（新增）
│                               │
│ - class MainActivity : AppC... │
│ + class MainActivity : Comp... │
│                               │
│     override fun onCreate...   │ ← 灰色背景（未改）
│         super.onCreate(...)    │
├─────────────────────────────────┤
│        [撤销]  [确认]          │
└─────────────────────────────────┘
```

**实现要点**：
- 基于 Myers diff 算法计算差异
- 红色标记删除行，绿色标记新增行
- 支持确认/撤销操作
- 从 ToolResultBubble 的 `edit_file` 结果中触发
- 任务完成时显示所有文件的 Diff 汇总

---

### 2.5 任务历史与持久化

**问题**：App 关闭后所有对话和任务记录丢失，无法回顾。

**方案**：
- 使用 Room 数据库持久化任务记录
- 新增 `TaskHistoryScreen` 页面
- 每个任务记录包含：任务描述、状态（完成/失败/中断）、文件变更列表、耗时、Token 消耗
- 支持重新执行历史任务
- 支持从历史任务继续对话

**数据模型**：
```kotlin
@Entity
data class TaskRecord(
    @PrimaryKey val id: String,
    val task: String,
    val status: TaskStatus,       // COMPLETED / FAILED / INTERRUPTED
    val filesChanged: List<String>,
    val summary: String,
    val tokenUsage: Int,
    val createdAt: Long,
    val durationMs: Long
)
```

---

## 三、P1 — 没有这些不好用（体验提升）

### 3.1 手机端构建能力

**问题**：当前 `gradle_build` 工具依赖项目目录下有 `gradlew`，但手机上没有 JDK/Gradle 环境。

**方案（三种路线，按推荐排序）**：

| 方案 | 原理 | 优点 | 缺点 |
|------|------|------|------|
| **A. Termux 集成** | 在 App 内嵌入 Termux 运行时，提供完整 Linux 环境 | 功能最完整，可运行 Gradle/Git/Python | 体积大（~200MB），需用户安装 |
| **B. 远程构建服务** | 将代码推送到云端服务器构建 | 手机零负担，构建速度快 | 需要服务器，有网络依赖，隐私问题 |
| **C. 混合模式** | 本地轻量检查（lint/syntax）+ 远程完整构建 | 平衡体验和性能 | 实现复杂 |

**推荐方案 A（Termux 集成）**：
```
App 启动时：
1. 检测 Termux 是否已安装
2. 如已安装 → 通过 Termux:Tasker API 执行命令
3. 如未安装 → 引导用户安装，或降级为仅 lint 模式

构建流程：
1. Agent 调用 gradle_build
2. 通过 Termux 执行 ./gradlew assembleDebug
3. 实时流式输出构建日志
4. 构建成功 → 提示安装 APK
5. 构建失败 → Agent 分析错误并修复
```

**新增工具**：
- `install_apk` — 构建成功后安装 APK 到设备
- `launch_app` — 启动已安装的应用
- `run_command` — 在 Termux 环境中执行任意 Shell 命令

---

### 3.2 APK 安装与运行

**问题**：构建成功后无法直接安装和测试。

**方案**：
- 构建成功后自动检测 APK 文件
- 调用系统 PackageInstaller 安装 APK
- 安装后提供"启动应用"按钮
- 自动读取 Logcat 监控应用运行状态
- 崩溃时自动捕获异常并反馈给 Agent

**新增权限**：
```xml
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
<uses-permission android:name="android.permission.READ_LOGS" />  <!-- 需要 system/app 级别 -->
```

---

### 3.3 多项目管理

**问题**：当前只能配置一个项目路径，无法在多个项目间切换。

**方案**：
- 项目列表页面，支持添加/删除/切换项目
- 每个项目独立保存：路径、最近任务、Git 状态、摘要缓存
- 项目卡片显示：名称、路径、最近修改时间、Git 分支
- 支持从 Git URL 克隆项目

**新增页面**：
- `Screen.ProjectList` — 项目列表
- `Screen.NewProject` — 新建/导入项目

---

### 3.4 语音输入

**问题**：手机打字不方便，语音输入更自然。

**方案**：
- 集成 Android Speech-to-Text API
- 输入栏添加麦克风按钮
- 语音识别结果作为任务输入
- 支持语音+文字混合输入

---

### 3.5 通知与后台运行

**问题**：Agent 执行长任务时，切换 App 会导致任务中断。

**方案**：
- 使用 Foreground Service 保持 Agent 运行
- 显示持续通知：当前任务、进度、已用时间
- 任务完成/失败时发送通知
- 支持从通知栏停止 Agent

**新增组件**：
- `AgentService` — 前台服务
- `AgentNotificationManager` — 通知管理

---

## 四、P2 — 没有这些不够专业（专业级功能）

### 4.1 API Key 安全存储

**问题**：当前 API Key 明文存储在 SharedPreferences 中。

**方案**：
- 使用 Android Keystore + EncryptedSharedPreferences
- API Key 加密存储，不可被其他 App 读取
- 支持生物识别解锁（指纹/面部）
- 支持多 LLM Provider 配置（OpenAI/DeepSeek/Anthropic/本地模型）

---

### 4.2 Token 用量追踪与成本估算

**问题**：用户不知道每次任务消耗了多少 Token，无法控制成本。

**方案**：
- 每次任务记录：输入 Token 数、输出 Token 数、总成本
- 设置页面显示累计用量
- 支持设置 Token 预算上限
- 任务进行中实时显示已消耗 Token

**新增 UI**：
- 输入栏上方显示当前任务 Token 消耗
- 设置页面显示累计统计图表

---

### 4.3 Git 远程仓库集成

**问题**：当前 Git 只支持本地操作，无法 push/pull。

**方案**：
- 支持 Git remote 操作：clone、push、pull、fetch
- 支持分支管理：创建/切换/合并分支
- 支持 PR/MR 创建（GitHub/GitLab API）
- SSH Key 管理或 HTTPS Token 认证

**新增工具**：
- `git_clone` — 克隆远程仓库
- `git_push` — 推送到远程
- `git_pull` — 拉取远程更新
- `git_branch` — 分支管理

---

### 4.4 项目模板与快速创建

**问题**：用户无法从零创建项目，只能操作已有项目。

**方案**：
- 提供项目模板：Empty Activity、Compose Activity、Library Module
- 一键创建项目结构（build.gradle、Manifest、主 Activity）
- 支持从 GitHub 模板仓库创建

**新增页面**：
- `Screen.NewProject` — 项目创建向导

---

### 4.5 协作与分享

**问题**：无法分享 Agent 的工作结果。

**方案**：
- 导出任务报告（Markdown 格式）
- 分享文件变更 Diff
- 导出项目为 ZIP
- 生成任务执行回放（类似 OpenHands 的回放功能）

---

## 五、P3 — 锦上添花（差异化竞争力）

### 5.1 屏幕截图分析

**问题**：用户发现 Bug 时，需要手动描述问题。

**方案**：
- 集成 MediaProjection API 截取当前屏幕
- 截图自动发送给 Agent 分析
- Agent 结合 Logcat 输出诊断问题
- 一键"截图 + Logcat → Agent 分析"流程

---

### 5.2 本地 LLM 支持

**问题**：完全依赖云端 API，离线无法使用。

**方案**：
- 集成 MLC-LLM / llama.cpp 在设备端运行小模型
- 支持 Gemma-2B、Phi-3-mini 等轻量模型
- 简单任务用本地模型，复杂任务用云端模型
- 混合模式：本地模型做初步分析，云端模型做精确修改

---

### 5.3 Agent 工作流可视化

**问题**：Agent 的执行过程是黑盒，用户不知道它在做什么。

**方案**：
- 实时显示 Agent 的思考链（Chain of Thought）
- 工具调用时间线视图
- 执行步骤进度条
- 可折叠的详细日志

---

### 5.4 多 Agent 协作

**问题**：单 Agent 处理复杂任务效率低。

**方案**：
- Architect Agent（规划）+ Editor Agent（执行）双模型分工
- 支持 Agent 间消息传递
- 任务分解与并行执行

---

### 5.5 主题与个性化

**问题**：当前 UI 只有默认主题。

**方案**：
- 深色/浅色/跟随系统
- 代码编辑器主题（Monokai/Dracula/Solarized）
- 自定义 Agent 名称和头像
- 紧凑/舒适布局模式

---

## 六、实施路线图

### Sprint 1（2周）— 核心体验补全
| 优先级 | 功能 | 工作量 |
|--------|------|--------|
| P0 | 多模态输入（图片+文件上传） | 3天 |
| P0 | 文件浏览器面板 | 2天 |
| P0 | Diff 查看器 | 2天 |
| P0 | 任务历史持久化（Room） | 2天 |
| P0 | 代码编辑器（基础版） | 3天 |

### Sprint 2（2周）— 构建与运行
| 优先级 | 功能 | 工作量 |
|--------|------|--------|
| P1 | Termux 集成（本地构建） | 3天 |
| P1 | APK 安装与运行 | 2天 |
| P1 | 通知与后台运行 | 2天 |
| P1 | 多项目管理 | 2天 |
| P1 | 语音输入 | 1天 |

### Sprint 3（2周）— 专业级功能
| 优先级 | 功能 | 工作量 |
|--------|------|--------|
| P2 | API Key 加密存储 | 1天 |
| P2 | Token 用量追踪 | 2天 |
| P2 | Git 远程仓库集成 | 3天 |
| P2 | 项目模板与快速创建 | 2天 |
| P2 | 协作与分享 | 2天 |

### Sprint 4（2周）— 差异化竞争力
| 优先级 | 功能 | 工作量 |
|--------|------|--------|
| P3 | 屏幕截图分析 | 2天 |
| P3 | Agent 工作流可视化 | 3天 |
| P3 | 深色主题 + 编辑器主题 | 2天 |
| P3 | 本地 LLM 支持（调研） | 3天 |

---

## 七、新增依赖预估

| 库 | 用途 | 大小影响 |
|----|------|---------|
| Rosembed/CodeEditor | 代码编辑器 | ~2MB |
| Room | 任务历史持久化 | ~1MB |
| EncryptedSharedPreferences | API Key 加密 | ~0.5MB |
| Coil | 图片加载和缓存 | ~1MB |
| AndroidX Browser | 文件选择 | ~0.5MB |

---

## 八、新增权限预估

```xml
<!-- 多模态输入 -->
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />

<!-- APK 安装 -->
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />

<!-- 后台运行 -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<!-- 语音输入 -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />

<!-- 屏幕截图 -->
<uses-permission android:name="android.permission.PROJECT_MEDIA" />
```

---

## 九、与竞品对比

| 功能 | 我们的 App | Cursor | Replit | Aider | GitHub Copilot |
|------|-----------|--------|--------|-------|----------------|
| 手机端运行 | ✅ | ❌ | ✅(Web) | ❌ | ❌ |
| 自主 Agent | ✅ | ❌ | ❌ | ✅ | ❌ |
| 图片输入 | 🔜 | ✅ | ❌ | ❌ | ✅ |
| 代码编辑器 | 🔜 | ✅ | ✅ | ❌ | ✅ |
| 本地构建 | 🔜 | ✅ | ✅(云端) | ✅ | ❌ |
| Git 集成 | ✅(基础) | ✅ | ✅ | ✅ | ✅ |
| 多项目 | 🔜 | ✅ | ✅ | ✅ | ✅ |
| 离线使用 | 🔜 | ❌ | ❌ | ❌ | ❌ |
| 语音输入 | 🔜 | ❌ | ❌ | ❌ | ❌ |
| 截图分析 | 🔜 | ❌ | ❌ | ❌ | ❌ |

**我们的核心差异化**：唯一一个在手机上运行的**自主编程 Agent**，结合图片输入、截图分析、语音输入等移动端特有能力。
