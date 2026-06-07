# Android Dev Agent — 全自动自主 Agent 架构方案（v2）

> 基于 OpenHands、SWE-agent、Aider、Composio SWE-Kit 等主流 Agent 产品的调研成果

---

## 一、产品定位

一个运行在 Android 手机上的**全自动自主编程 Agent**。用户只需给出高层目标（如"给这个项目加一个登录页面"），Agent 会自主完成：

- 理解项目结构和代码
- 规划任务步骤
- 读写项目文件
- 调用 Gradle 编译
- 读取 Logcat 诊断错误
- 自动修复问题
- 循环迭代直到任务完成

**核心差异**：不是"聊天机器人"，而是"能动手的程序员"。

---

## 二、行业调研 — 从顶级 Agent 产品学到了什么

### 2.1 OpenHands（原 OpenDevin）— SWE-Bench 77%

**核心创新：CodeAct 范式**
- 不用 20 个专用工具的 JSON Schema，只给 LLM 3 个核心能力：**Bash + Python + 文件编辑器**
- LLM 用"写代码→跑代码→看结果"的循环替代传统文本/JSON 工具调用
- CodeAct 2.1 切换到 **Function Calling**，性能大幅提升

**关键架构组件**：
| 组件 | 作用 | 我们要借鉴的 |
|------|------|-------------|
| **Event Stream** | 追加写入的事件日志，系统唯一真相源 | ✅ Agent 所有操作记录为不可变事件流 |
| **Condenser** | 上下文压缩器，智能裁剪对话历史 | ✅ 超出 token 预算时自动摘要旧对话 |
| **Skills（Microagents）** | 按场景自动加载的知识模块 | ✅ Android 开发专用知识（Gradle、Manifest 规则等） |
| **Stuck Detection** | 检测 Agent 是否陷入循环 | ✅ 连续 3 次相同错误时自动切换策略 |
| **Security Policy** | 操作确认和风险分析 | ✅ 危险操作需用户确认 |
| **Sub-agent Delegation** | 子 Agent 并行处理子任务 | 🔜 后期考虑 |

### 2.2 SWE-agent（Princeton）— NeurIPS 2024

**核心创新：ACI（Agent-Computer Interface）**

SWE-agent 最重要的发现：**为 LLM 优化的接口设计，比模型本身更重要**。

**ACI 四大设计原则**：

| 原则 | 含义 | 我们的应用 |
|------|------|-----------|
| **Simplicity** | 命令简单，1-3 个参数，LLM 容易生成 | ✅ 工具参数极简化，如 `edit path start:end text` |
| **Compactness** | 操作紧凑，一步完成有意义的工作 | ✅ `edit_file` 一步完成搜索替换，不用 sed |
| **Informative Feedback** | 每次操作后返回结构化反馈 | ✅ 写文件后返回"✅ 写入成功，共 42 行"；编辑后返回 diff |
| **Guardrails** | 守卫机制，防止错误级联 | ✅ 编辑后自动 lint，语法错误则回滚并返回错误 |

**关键发现**：
- 编辑后自动 lint 检查 → 解决率提升 3%
- 窗口化文件浏览（100行/页）vs 全文件显示 → 解决率提升 5.3%
- 无反馈的命令（如 rm）返回确认信息 → 减少重复操作

### 2.3 Aider — SWE-Bench Lite SOTA

**核心创新：Architect/Editor 分离**

- **Architect**（强推理模型）：专注理解问题、设计方案
- **Editor**（快速模型）：将方案转化为精确的代码编辑指令
- 分离后 o1-preview + DeepSeek 组合达到 85% SOTA

**其他亮点**：
| 特性 | 说明 | 我们要借鉴的 |
|------|------|-------------|
| **Repo Map** | 用 Tree-sitter 生成代码结构图，只发送相关部分 | ✅ 项目结构摘要，避免发送整个文件 |
| **Git 原生** | 每次修改自动 commit，随时可回滚 | ✅ 每步操作自动 Git commit |
| **多编辑格式** | diff / whole / architect 三种模式 | ✅ 支持 search-replace 和 whole-file 两种编辑模式 |
| **交互式** | 用户始终在控制中，可随时纠正 | ✅ 用户可中断、修改、重试 |

### 2.4 Composio SWE-Kit — SWE-Bench 48.6%

**核心创新：多专家 Agent 协作**

- **Software Engineer Agent**：任务分配、流程控制
- **CodeAnalyzer Agent**：代码分析、类/方法定位
- **Editor Agent**：文件导航和修改

用 LangGraph 状态机编排，每个 Agent 有独立的工具集。

**我们要借鉴的**：后期可拆分为 Analyzer + Editor 两个角色，但 Phase 1 先用单 Agent。

---

## 三、核心架构

```
┌──────────────────────────────────────────────────────────┐
│                      用户界面层                           │
│  ┌───────────┐  ┌──────────┐  ┌────────────────────┐    │
│  │ 对话界面   │  │ 文件浏览  │  │ 任务进度 + 事件流   │    │
│  └─────┬─────┘  └────┬─────┘  └─────────┬──────────┘    │
│        └──────────────┼──────────────────┘               │
│                       ▼                                   │
│            ┌──────────────────┐                           │
│            │   Agent Engine   │ ◄── 自主循环               │
│            │  (状态机/调度器)  │     Think→Act→Observe     │
│            └────────┬─────────┘                           │
│                     │                                     │
│       ┌─────────────┼──────────────┐                      │
│       ▼             ▼              ▼                      │
│ ┌──────────┐ ┌────────────┐ ┌──────────────┐             │
│ │ LLM 层    │ │  工具箱     │ │  记忆系统     │             │
│ │(Function  │ │ (ACI 优化)  │ │(Condenser +  │             │
│ │ Calling)  │ │            │ │ Event Stream)│             │
│ └──────────┘ └─────┬──────┘ └──────────────┘             │
│                      ▼                                    │
│            ┌──────────────────┐                           │
│            │   项目沙箱层      │                           │
│            │ (文件系统 + Git + │                           │
│            │  构建环境 + Lint) │                           │
│            └──────────────────┘                           │
└──────────────────────────────────────────────────────────┘
```

---

## 四、Agent 核心循环 — 借鉴 OpenHands Event Stream

整个系统基于**不可变事件流**（Event Stream），这是 OpenHands 的核心设计：

```
用户: "给项目加一个登录页面"
          │
          ▼
    ┌─────────────────────────────────────────────┐
    │            Agent Engine 循环                  │
    │                                              │
    │  1. THINK (LLM 推理)                         │
    │     → 分析当前状态，决定下一步                  │
    │     → 输出：文本思考 + 工具调用请求             │
    │                                              │
    │  2. ACT (执行工具)                            │
    │     → 执行工具，获得结果                       │
    │     → 自动 lint/guardrail 检查                │
    │     → 记录到 Event Stream                     │
    │                                              │
    │  3. OBSERVE (观察结果)                        │
    │     → 检查工具输出 + lint 结果                 │
    │     → Condenser 压缩旧上下文                   │
    │     → Stuck Detection 检测循环                 │
    │                                              │
    │  4. 循环回到 1，直到任务完成或达到上限           │
    └─────────────────────────────────────────────┘
          │
          ▼
    ✅ 任务完成 → 自动 Git commit → 汇报结果
```

### 4.1 Event Stream（借鉴 OpenHands）

所有 Agent 操作记录为不可变事件，这是系统的唯一真相源：

```kotlin
sealed class AgentEvent {
    data class UserMessage(val content: String) : AgentEvent()
    data class AssistantThought(val content: String) : AgentEvent()
    data class ToolCall(val name: String, val args: Map<String, String>) : AgentEvent()
    data class ToolResult(val callId: String, val output: String, val success: Boolean) : AgentEvent()
    data class LintResult(val errors: List<String>, val passed: Boolean) : AgentEvent()
    data class BuildResult(val success: Boolean, val output: String) : AgentEvent()
    data class TaskComplete(val summary: String, val filesChanged: List<String>) : AgentEvent()
    data class StuckDetected(val reason: String) : AgentEvent()
}
```

### 4.2 Stuck Detection（借鉴 OpenHands）

检测 Agent 是否陷入循环，自动切换策略：

```kotlin
class StuckDetector {
    fun detect(events: List<AgentEvent>): StuckState {
        // 1. 连续 3 次相同工具调用 + 相同参数 → 陷入循环
        // 2. 连续 3 次构建失败且错误相同 → 无法修复
        // 3. 总迭代超过 20 次 → 可能任务过于复杂
        // 4. Token 消耗超过预算 → 需要压缩上下文
    }
}
```

---

## 五、工具箱 — ACI 优化设计（借鉴 SWE-agent）

### 设计原则（来自 SWE-agent 的 ACI 理论）

1. **每个工具 1-3 个参数**，LLM 容易正确生成
2. **操作后返回结构化反馈**，不返回空结果
3. **编辑后自动 lint**，语法错误自动回滚
4. **文件窗口化浏览**，不一次性返回整个文件

### 5.1 文件操作工具

| 工具名 | 功能 | 参数 | ACI 优化点 |
|--------|------|------|-----------|
| `read_file` | 读取文件（窗口化） | path, start_line?, end_line? | 默认返回前 100 行，支持翻页 |
| `write_file` | 创建或覆盖文件 | path, content | 返回"✅ 写入成功，共 N 行" |
| `edit_file` | 搜索替换编辑 | path, old_text, new_text | 替换后自动 lint，失败则回滚 |
| `insert_code` | 在指定行插入代码 | path, line_number, code | 插入后返回上下文 5 行 |
| `list_files` | 列出目录结构 | path, max_depth? | 返回树形结构，限制深度 |
| `delete_file` | 删除文件 | path | 返回"✅ 已删除"，需确认 |

### 5.2 构建与验证工具

| 工具名 | 功能 | 参数 | ACI 优化点 |
|--------|------|------|-----------|
| `gradle_build` | 执行 Gradle 构建 | task? | 返回结构化结果：成功/失败+错误摘要 |
| `run_tests` | 运行测试 | test_class? | 返回通过/失败数量 + 失败详情 |
| `read_logcat` | 读取 Logcat | filter?, lines? | 只返回最近 N 行，自动过滤重复 |
| `lint_check` | 对文件做语法检查 | path | 返回错误列表 + 行号 + 修复建议 |

### 5.3 项目分析工具

| 工具名 | 功能 | 参数 | ACI 优化点 |
|--------|------|------|-----------|
| `analyze_project` | 分析项目结构 | - | 返回：模块列表 + 依赖图 + 关键文件摘要 |
| `search_code` | 搜索代码 | query, file_pattern? | 返回：文件名 + 行号 + 匹配行上下文 |
| `find_usages` | 查找引用 | symbol, path? | 返回：引用位置列表 |

### 5.4 系统工具

| 工具名 | 功能 | 参数 | ACI 优化点 |
|--------|------|------|-----------|
| `ask_user` | 向用户提问 | question | 用户回答后继续执行 |
| `git_commit` | 提交当前改动 | message | 返回 commit hash |
| `git_diff` | 查看未提交的改动 | - | 返回 diff 摘要 |
| `git_revert` | 撤销最近一次改动 | - | 需用户确认 |

### 5.5 编辑守卫（Guardrail）— 借鉴 SWE-agent

这是 SWE-agent 最重要的创新之一，消融实验证明提升 3% 解决率：

```kotlin
class EditGuardrail(private val projectPath: String) {
    fun executeEdit(call: ToolCall): ToolResult {
        // 1. 执行编辑
        val result = editFile(call)
        
        // 2. 自动 lint 检查
        val lintErrors = lintCheck(call.args["path"]!!)
        
        if (lintErrors.isNotEmpty()) {
            // 3. 语法错误 → 自动回滚
            revertEdit(call.args["path"]!!)
            return ToolResult(
                success = false,
                output = "❌ 编辑导致语法错误，已自动回滚:\n" +
                         lintErrors.joinToString("\n") + 
                         "\n请修正后重试。"
            )
        }
        
        // 4. 无错误 → 返回编辑后的上下文
        return ToolResult(
            success = true,
            output = "✅ 编辑成功:\n" + getContext(call.args["path"]!!)
        )
    }
}
```

---

## 六、LLM 交互 — Function Calling（借鉴 OpenHands CodeAct 2.1）

OpenHands CodeAct 2.1 从"纯代码执行"切换到 **Function Calling**，性能大幅提升。我们直接采用 Function Calling：

### 6.1 System Prompt 设计

```
你是一个专业的 Android 开发 Agent。你可以自主完成 Android 项目的开发任务。

## 你的工作方式
1. 先分析项目结构，理解代码库
2. 制定清晰的执行计划
3. 逐步执行，每步都验证结果
4. 遇到错误自动分析修复
5. 任务完成后汇报结果

## 重要规则
- 修改文件前先读取当前内容
- 每次编辑后检查语法错误
- 构建失败时先分析错误信息再修复
- 不确定时使用 ask_user 向用户提问
- 一次只做一个编辑操作，验证后再继续

## 可用工具
[工具列表...]
```

### 6.2 Android 专用 Skills（借鉴 OpenHands Microagents）

按场景自动注入的领域知识，减少 LLM 犯错：

```kotlin
object AndroidSkills {
    val GRADLE_SKILL = """
    ## Android Gradle 知识
    - 构建 APK: ./gradlew assembleDebug
    - 常见构建错误：SDK 版本不匹配、依赖冲突、ProGuard 规则
    - build.gradle 关键配置：compileSdk, targetSdk, minSdk, dependencies
    """
    
    val MANIFEST_SKILL = """
    ## AndroidManifest 知识
    - 每个 Activity 必须在 Manifest 中注册
    - 权限声明：<uses-permission>
    - Application 类需要 android:name 属性
    """
    
    val COMPOSE_SKILL = """
    ## Jetpack Compose 知识
    - @Composable 函数是 UI 的基本单元
    - 状态管理：remember, mutableStateOf, StateFlow
    - 副作用：LaunchedEffect, SideEffect
    - 导航：NavHost, composable, navigate
    """
}
```

---

## 七、记忆系统 — Condenser（借鉴 OpenHands）

### 7.1 Event Stream → Condenser → LLM Context

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│  Event Stream │ ──► │   Condenser  │ ──► │  LLM Context │
│  (完整历史)   │     │  (智能压缩)   │     │  (窗口内)    │
│  无限长度     │     │  保留关键信息  │     │  ≤ 8000 token│
└──────────────┘     └──────────────┘     └──────────────┘
```

### 7.2 Condenser 压缩策略

```kotlin
class ContextCondenser {
    fun condense(events: List<AgentEvent>, maxTokens: Int): List<Message> {
        val budget = TokenBudget(maxTokens)
        
        // 1. System Prompt — 必须保留
        budget.allocate(1000, systemPrompt)
        
        // 2. 项目摘要 — 长期记忆，必须保留
        budget.allocate(1500, projectSummary)
        
        // 3. 最近 N 轮对话 — 完整保留
        val recentEvents = events.takeLast(10)
        budget.allocate(4000, recentEvents.toMessages())
        
        // 4. 旧对话 — 压缩为摘要
        val oldEvents = events.dropLast(10)
        val summary = summarize(oldEvents)  // 调用 LLM 生成摘要
        budget.allocate(1000, summary)
        
        // 5. 当前操作的文件内容
        budget.allocateRemaining(currentFileContent)
        
        return budget.build()
    }
}
```

### 7.3 项目摘要（借鉴 Aider Repo Map）

Aider 用 Tree-sitter 生成代码结构图，只发送相关符号定义。我们实现一个简化版：

```kotlin
class ProjectSummaryGenerator {
    fun generate(projectPath: String): ProjectSummary {
        return ProjectSummary(
            structure = generateDirectoryTree(projectPath, maxDepth = 3),
            keyFiles = analyzeKeyFiles(projectPath),  // 每个文件一句话摘要
            dependencies = parseGradleDependencies(projectPath),
            manifestInfo = parseManifest(projectPath)
        )
    }
}
```

---

## 八、项目沙箱 — 安全机制

### 8.1 三级安全策略（借鉴 OpenHands Security Policy）

```kotlin
enum class SecurityLevel {
    AUTO_CONFIRM,      // 全部自动执行（信任 Agent）
    DANGEROUS_CONFIRM,  // 危险操作需确认（默认）
    ALL_CONFIRM         // 所有操作需确认（谨慎模式）
}

class SecurityPolicy(private val level: SecurityLevel) {
    fun needsConfirmation(action: AgentEvent.ToolCall): Boolean = when (level) {
        AUTO_CONFIRM -> false
        DANGEROUS_CONFIRM -> action.name in listOf("delete_file", "gradle_build", "git_revert")
        ALL_CONFIRM -> true
    }
}
```

### 8.2 自动 Git 集成（借鉴 Aider）

Aider 的核心哲学：**每次修改自动 commit，随时可回滚**。

```kotlin
class GitIntegration(private val projectPath: String) {
    fun autoCommit(message: String): String {
        // 每次工具执行后自动 commit
        // commit message 包含工具名和参数摘要
        // 用户可随时 git revert 回退
    }
    
    fun getDiff(): String {
        // 返回当前未提交的改动
    }
    
    fun revertLastCommit(): Result<Unit> {
        // 撤销最近一次 Agent 操作
    }
}
```

---

## 九、用户界面设计

### 9.1 主界面 — 对话 + 实时事件流

```
┌─────────────────────────────────┐
│  🤖 Android Dev Agent     [⚙️]  │
│  📁 MyProject           [📂]    │
├─────────────────────────────────┤
│                                 │
│  👤 给项目加一个登录页面         │
│                                 │
│  🤖 让我先分析项目结构...       │
│  ┌─────────────────────────┐    │
│  │ 📋 执行计划:             │    │
│  │ 1. 分析现有项目结构      │    │
│  │ 2. 创建 LoginActivity   │    │
│  │ 3. 创建登录布局 XML      │    │
│  │ 4. 注册到 Manifest      │    │
│  │ 5. 添加网络请求逻辑      │    │
│  │ 6. 构建验证              │    │
│  └─────────────────────────┘    │
│                                 │
│  🤖 步骤 1/6: 分析项目...       │
│     📁 list_files → ✅          │
│     📄 read_file: MainActivity  │
│                                 │
│  🤖 步骤 2/6: 创建 Activity    │
│     ✏️ write_file: LoginActivity│
│     → ✅ 写入成功，共 68 行      │
│     🔍 lint_check → ✅ 通过     │
│                                 │
│  🤖 步骤 6/6: 构建验证          │
│     🔨 gradle_build             │
│     → ❌ 失败: 缺少 import      │
│     🔧 自动修复...              │
│     ✏️ edit_file: +import       │
│     🔨 重新构建 → ✅ 成功!       │
│                                 │
│  🤖 ✅ 任务完成！               │
│     新建 2 文件，修改 3 文件     │
│     [查看 Diff] [撤销] [继续]   │
│                                 │
├─────────────────────────────────┤
│  💬 输入指令...          [发送]  │
└─────────────────────────────────┘
```

### 9.2 设置页面
- API Key + Base URL + 模型选择（支持 OpenAI / DeepSeek / 自定义）
- 项目路径选择
- 安全级别（自动 / 危险确认 / 全部确认）
- 最大迭代次数
- Token 预算

---

## 十、技术实现要点

### 10.1 Agent Engine（核心循环）

```kotlin
class AgentEngine(
    private val llmProvider: LLMProvider,
    private val toolExecutor: ToolExecutor,
    private val condenser: ContextCondenser,
    private val stuckDetector: StuckDetector,
    private val securityPolicy: SecurityPolicy,
    private val eventStream: MutableList<AgentEvent>
) {
    suspend fun run(task: String): Flow<AgentEvent> = flow {
        eventStream.add(AgentEvent.UserMessage(task))
        
        var iterations = 0
        val maxIterations = 20
        
        while (iterations++ < maxIterations) {
            // 1. 压缩上下文
            val context = condenser.condense(eventStream, maxTokens = 8000)
            
            // 2. 调用 LLM
            val response = llmProvider.chatWithTools(context, toolDefinitions)
            
            if (response.hasToolCalls()) {
                for (toolCall in response.toolCalls) {
                    // 3. 安全检查
                    if (securityPolicy.needsConfirmation(toolCall)) {
                        emit(AgentEvent.AwaitingConfirmation(toolCall))
                        // 等待用户确认...
                    }
                    
                    // 4. 执行工具
                    val result = toolExecutor.execute(toolCall)
                    eventStream.add(AgentEvent.ToolCall(toolCall.name, toolCall.args))
                    eventStream.add(AgentEvent.ToolResult(toolCall.id, result.output, result.success))
                    emit(AgentEvent.ToolResult(toolCall.id, result.output, result.success))
                    
                    // 5. 自动 Git commit
                    gitIntegration.autoCommit("${toolCall.name}: ${toolCall.args}")
                }
            } else {
                // 6. 任务完成
                eventStream.add(AgentEvent.TaskComplete(response.content, changedFiles))
                emit(AgentEvent.TaskComplete(response.content, changedFiles))
                break
            }
            
            // 7. Stuck Detection
            val stuckState = stuckDetector.detect(eventStream)
            if (stuckState.isStuck) {
                emit(AgentEvent.StuckDetected(stuckState.reason))
                break
            }
        }
    }
}
```

### 10.2 工具执行器（带 Guardrail）

```kotlin
class ToolExecutor(
    private val projectPath: String,
    private val editGuardrail: EditGuardrail,
    private val gitIntegration: GitIntegration
) {
    fun execute(call: ToolCall): ToolResult = when (call.name) {
        "read_file" -> {
            val content = readFile(call.args["path"]!!, call.lineRange())
            val windowed = content.windowed(defaultPageSize)
            ToolResult(true, "📄 ${call.args["path"]} (行 ${windowed.range}):\n${windowed.text}")
        }
        
        "write_file" -> {
            writeFile(call.args["path"]!!, call.args["content"]!!)
            val lineCount = call.args["content"]!!.lines().size
            ToolResult(true, "✅ 写入成功: ${call.args["path"]}, 共 $lineCount 行")
        }
        
        "edit_file" -> {
            editGuardrail.executeEdit(call)  // 带自动 lint + 回滚
        }
        
        "gradle_build" -> {
            val result = executeGradle(call.args["task"] ?: "assembleDebug")
            if (result.success) {
                ToolResult(true, "✅ 构建成功")
            } else {
                val errorSummary = extractErrorSummary(result.output)
                ToolResult(false, "❌ 构建失败:\n$errorSummary")
            }
        }
        
        // ... 其他工具
        else -> ToolResult(false, "未知工具: ${call.name}")
    }
}
```

---

## 十一、实施路线图

### Phase 1：核心 Agent 引擎（第 1-2 周）
- [ ] 实现 Event Stream 架构
- [ ] 实现 Agent Engine 循环（Think→Act→Observe）
- [ ] 实现 Function Calling 协议（OpenAI 兼容）
- [ ] 实现文件操作工具（read/write/edit/list）+ Guardrail
- [ ] 实现对话界面 + 实时事件流展示
- [ ] 实现 API Key 配置

### Phase 2：构建与调试能力（第 3 周）
- [ ] 实现 Gradle 构建工具
- [ ] 实现 Logcat 读取工具
- [ ] 实现 lint_check 工具
- [ ] 实现自动修复循环（编译失败→分析→修复→重试）
- [ ] 实现 Stuck Detection

### Phase 3：记忆与安全（第 4 周）
- [ ] 实现 Context Condenser（上下文压缩）
- [ ] 实现项目摘要生成（Repo Map）
- [ ] 实现 Android Skills（领域知识注入）
- [ ] 实现 Git 集成（自动 commit / diff / revert）
- [ ] 实现安全策略（三级确认机制）
- [ ] 实现文件浏览面板

### Phase 4：高级能力（持续迭代）
- [ ] 实现 Architect/Editor 双模型分离（借鉴 Aider）
- [ ] 实现 ask_user 工具（向用户提问）
- [ ] 实现项目分析工具（analyze_project / search_code）
- [ ] 多任务管理
- [ ] 远程构建支持

---

## 十二、与当前代码的对比

| 维度 | 当前实现 | 目标实现 |
|------|---------|---------|
| 交互方式 | 聊天问答 | 自主执行任务 |
| LLM 调用 | 纯文本生成 | Function Calling |
| 文件操作 | 无 | 读写编辑 + Guardrail + 自动 lint |
| 构建能力 | 模拟 | 真实调用 Gradle + 错误分析 |
| 错误处理 | 返回错误信息 | 自动分析修复 + Stuck Detection |
| 上下文 | 单次对话 | Event Stream + Condenser |
| 安全 | 无 | 三级确认 + Git 自动备份 |
| 自主性 | 零 | 高（自主循环 + 守卫机制） |

---

## 十三、关键风险与应对

| 风险 | 应对 | 来源 |
|------|------|------|
| LLM 生成错误代码导致项目损坏 | Guardrail 自动 lint + 回滚 + Git 自动 commit | SWE-agent |
| Agent 陷入无限修复循环 | Stuck Detection + 最大迭代限制 | OpenHands |
| Token 消耗过大 | Condenser 智能压缩 + 项目摘要缓存 | OpenHands |
| LLM 不理解 Android 特定规则 | Android Skills 领域知识自动注入 | OpenHands Microagents |
| 手机上 Gradle 构建慢 | 增量编译 + 远程构建支持 | - |
| API Key 安全 | 本地 Android Keystore 加密存储 | - |
| LLM 工具调用格式错误 | 简化工具参数 + 结构化反馈 | SWE-agent ACI |
