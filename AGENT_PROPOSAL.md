# Android Dev Agent — 全自动自主 Agent 架构方案

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

## 二、核心架构

```
┌─────────────────────────────────────────────────┐
│                   用户界面层                      │
│  ┌───────────┐ ┌──────────┐ ┌────────────────┐  │
│  │ 对话界面   │ │ 文件浏览  │ │ 任务进度面板    │  │
│  └─────┬─────┘ └────┬─────┘ └───────┬────────┘  │
│        └─────────────┼───────────────┘           │
│                      ▼                            │
│              ┌──────────────┐                     │
│              │  Agent 核心   │ ◄── 自主循环        │
│              │  (大脑/调度)  │     Plan→Act→Observe│
│              └──────┬───────┘                     │
│                     │                             │
│         ┌───────────┼───────────┐                 │
│         ▼           ▼           ▼                 │
│  ┌────────────┐ ┌────────┐ ┌──────────┐          │
│  │  LLM 调用   │ │ 工具箱  │ │ 记忆系统  │          │
│  │  (思考/推理) │ │ (行动)  │ │ (上下文)  │          │
│  └────────────┘ └───┬────┘ └──────────┘          │
│                      ▼                            │
│              ┌──────────────┐                     │
│              │  项目沙箱层    │                     │
│              │  (文件系统+    │                     │
│              │   构建环境)    │                     │
│              └──────────────┘                     │
└─────────────────────────────────────────────────┘
```

---

## 三、Agent 核心循环：Plan → Act → Observe

这是整个系统的心脏，Agent 通过这个循环自主工作：

```
用户: "给项目加一个登录页面"
          │
          ▼
    ┌─────────────┐
    │ 1. PLAN     │  LLM 分析项目结构，拆解任务
    │   - 读取项目 │  → 需要创建 LoginActivity
    │   - 制定计划 │  → 需要创建布局文件
    │   - 排序步骤 │  → 需要修改 AndroidManifest
    └──────┬──────┘  → 需要添加网络请求
           ▼
    ┌─────────────┐
    │ 2. ACT      │  执行一个具体步骤
    │   - 选择工具 │  → 调用 write_file 工具
    │   - 执行操作 │  → 写入 LoginActivity.kt
    └──────┬──────┘
           ▼
    ┌─────────────┐
    │ 3. OBSERVE  │  观察执行结果
    │   - 检查输出 │  → 文件写入成功
    │   - 编译验证 │  → 调用 gradle_build
    │   - 发现问题 │  → 编译报错：缺少 import
    └──────┬──────┘
           ▼
      有错误？─── 是 ──→ 回到 PLAN（修复问题）
           │
           否
           ▼
      还有下一步？── 是 ──→ 回到 ACT
           │
           否
           ▼
       ✅ 任务完成，汇报结果
```

---

## 四、工具箱（Agent 可调用的能力）

Agent 通过 Function Calling 调用工具，这是它"动手"的方式：

### 4.1 文件操作工具
| 工具名 | 功能 | 参数 |
|--------|------|------|
| `read_file` | 读取项目中的文件内容 | path: String |
| `write_file` | 创建或覆盖文件 | path: String, content: String |
| `edit_file` | 精确编辑文件（搜索替换） | path: String, old: String, new: String |
| `list_files` | 列出目录结构 | path: String, depth: Int |
| `delete_file` | 删除文件 | path: String |

### 4.2 构建与运行工具
| 工具名 | 功能 | 参数 |
|--------|------|------|
| `gradle_build` | 执行 Gradle 构建 | task: String (如 "assembleDebug") |
| `read_logcat` | 读取 Logcat 日志 | filter: String?, lines: Int |
| `check_syntax` | 检查 Kotlin/Java 语法 | code: String, language: String |

### 4.3 项目分析工具
| 工具名 | 功能 | 参数 |
|--------|------|------|
| `analyze_project` | 分析项目结构、依赖、架构 | project_path: String |
| `search_code` | 在项目中搜索代码 | query: String, file_pattern: String |
| `find_usages` | 查找类/方法的引用 | symbol: String |

### 4.4 系统工具
| 工具名 | 功能 | 参数 |
|--------|------|------|
| `ask_user` | 向用户提问（需要决策时） | question: String |
| `web_search` | 搜索文档/解决方案 | query: String |

---

## 五、LLM 调用方式 — Function Calling

Agent 通过 OpenAI 兼容的 Function Calling 机制与 LLM 交互：

```kotlin
// 每次请求的结构
val request = ChatCompletionRequest(
    model = modelName,
    messages = conversationHistory,  // 包含之前的对话和工具结果
    tools = availableTools,          // Agent 可用的工具列表
    tool_choice = "auto"             // LLM 自主决定是否调用工具
)

// LLM 返回两种结果：
// 1. 纯文本回复 → 直接展示给用户
// 2. tool_calls → Agent 需要执行工具
//    → 执行工具，将结果追加到对话
//    → 再次调用 LLM，让它根据工具结果继续推理
```

### 工具定义示例（OpenAI Function Calling 格式）

```json
{
  "type": "function",
  "function": {
    "name": "write_file",
    "description": "创建或覆盖项目中的文件",
    "parameters": {
      "type": "object",
      "properties": {
        "path": { "type": "string", "description": "文件相对路径" },
        "content": { "type": "string", "description": "文件完整内容" }
      },
      "required": ["path", "content"]
    }
  }
}
```

---

## 六、记忆系统（上下文管理）

LLM 有 token 限制，不能把整个项目塞进去。需要智能的上下文管理：

### 6.1 短期记忆（当前任务）
- 对话历史（用户指令 + Agent 思考 + 工具调用结果）
- 当前任务计划和进度
- 最近修改的文件列表

### 6.2 长期记忆（项目知识）
- 项目结构摘要（自动生成并缓存）
- 关键文件索引（每个文件的一句话摘要）
- 构建配置信息
- 依赖关系图

### 6.3 上下文窗口策略
```
总 Token 预算: 8000
├── System Prompt:      1000  (角色定义 + 工具说明)
├── 项目摘要:           1500  (结构 + 关键文件)
├── 对话历史:           4000  (最近 N 轮)
├── 当前文件内容:       1000  (正在操作的文件)
└── 预留生成空间:       500
```

---

## 七、项目沙箱 — 安全地操作文件

### 7.1 项目选择
- 用户选择手机上的 Android 项目目录（通过 SAF/存储权限）
- Agent 只能在选定的项目目录内操作
- 支持从 Git 克隆项目

### 7.2 安全机制
- **沙箱隔离**：Agent 只能操作用户选定的项目目录
- **操作确认**：危险操作（删除文件、执行 Gradle）首次需用户确认
- **自动备份**：修改文件前自动创建 `.agent-backup/` 备份
- **Git 集成**：每个任务完成后自动创建 Git commit

### 7.3 构建环境
- 使用设备上的 Gradle Wrapper（`./gradlew`）
- 需要设备上有 JDK（Termux 或用户手动安装）
- 或者使用远程构建服务

---

## 八、用户界面设计

### 8.1 主界面 — 对话 + 实时状态
```
┌─────────────────────────────────┐
│  🤖 Android Dev Agent     [⚙️]  │
├─────────────────────────────────┤
│                                 │
│  👤 给项目加一个登录页面         │
│                                 │
│  🤖 好的，我来分析项目结构...    │
│     📁 读取项目文件...          │
│     📋 制定计划：               │
│       1. 创建 LoginActivity     │
│       2. 创建 activity_login.xml│
│       3. 修改 AndroidManifest   │
│       4. 添加网络请求逻辑        │
│                                 │
│  🤖 正在执行步骤 1/4...         │
│     ✏️ 写入 LoginActivity.kt    │
│     ✏️ 写入 activity_login.xml  │
│     ✏️ 修改 AndroidManifest.xml │
│     🔨 执行 Gradle 构建...      │
│     ❌ 构建失败：缺少 import     │
│     🔧 自动修复中...            │
│     ✏️ 编辑 LoginActivity.kt    │
│     🔨 重新构建...              │
│     ✅ 构建成功！               │
│                                 │
│  🤖 任务完成！已添加登录页面。   │
│     修改了 3 个文件，新建 2 个。 │
│     [查看改动] [撤销]           │
│                                 │
├─────────────────────────────────┤
│  💬 输入指令...          [发送]  │
└─────────────────────────────────┘
```

### 8.2 辅助面板（侧滑/Tab 切换）
- **文件浏览**：实时查看项目文件树，点击查看文件内容
- **任务进度**：当前任务的步骤列表和执行状态
- **构建日志**：Gradle 构建输出和 Logcat

### 8.3 设置页面
- API Key 配置（支持 OpenAI / DeepSeek / 自定义端点）
- 模型选择
- 项目路径选择
- 自动确认级别（全部自动 / 危险操作确认 / 全部确认）

---

## 九、技术实现要点

### 9.1 Agent 循环引擎
```kotlin
class AgentEngine(
    private val llmProvider: LLMProvider,
    private val toolExecutor: ToolExecutor,
    private val contextManager: ContextManager
) {
    suspend fun run(task: String): Flow<AgentEvent> = flow {
        val messages = mutableListOf<Message>()
        messages.add(systemPrompt())
        messages.add(userMessage(task))

        var maxIterations = 20  // 防止无限循环

        while (maxIterations-- > 0) {
            // 1. 调用 LLM
            val response = llmProvider.chatWithTools(messages, tools)

            // 2. 如果 LLM 要调用工具
            if (response.hasToolCalls()) {
                for (toolCall in response.toolCalls) {
                    emit(AgentEvent.ToolCall(toolCall))
                    // 3. 执行工具
                    val result = toolExecutor.execute(toolCall)
                    emit(AgentEvent.ToolResult(toolCall, result))
                    // 4. 将结果追加到对话
                    messages.add(toolResultMessage(toolCall.id, result))
                }
            } else {
                // 5. LLM 认为任务完成
                emit(AgentEvent.Complete(response.content))
                break
            }
        }
    }
}
```

### 9.2 工具执行器
```kotlin
class ToolExecutor(private val projectPath: String) {
    fun execute(call: ToolCall): String = when (call.name) {
        "read_file" -> readFile(call.args["path"]!!)
        "write_file" -> writeFile(call.args["path"]!!, call.args["content"]!!)
        "edit_file" -> editFile(call.args["path"]!!, call.args["old"]!!, call.args["new"]!!)
        "list_files" -> listFiles(call.args["path"]!!, call.args["depth"]?.toInt() ?: 3)
        "gradle_build" -> gradleBuild(call.args["task"] ?: "assembleDebug")
        "read_logcat" -> readLogcat(call.args["filter"], call.args["lines"]?.toInt() ?: 50)
        "search_code" -> searchCode(call.args["query"]!!, call.args["file_pattern"])
        else -> "Unknown tool: ${call.name}"
    }
}
```

### 9.3 Function Calling API 适配
```kotlin
data class ChatCompletionRequest(
    val model: String,
    val messages: List<Message>,
    val tools: List<ToolDefinition>? = null,
    val toolChoice: String = "auto"  // "auto" | "none" | {"type":"function","function":{"name":"xxx"}}
)

data class ChatCompletionResponse(
    val choices: List<Choice>
) {
    fun hasToolCalls(): Boolean = choices.firstOrNull()?.message?.toolCalls?.isNotEmpty() == true
}
```

---

## 十、实施路线图

### Phase 1：核心 Agent 引擎（1-2 周）
- [ ] 实现 Agent 循环（Plan→Act→Observe）
- [ ] 实现 Function Calling 协议
- [ ] 实现文件操作工具（read/write/edit/list）
- [ ] 实现对话界面 + 实时状态展示

### Phase 2：构建与调试能力（1 周）
- [ ] 实现 Gradle 构建工具
- [ ] 实现 Logcat 读取工具
- [ ] 实现自动修复循环（编译失败→分析→修复→重试）
- [ ] 实现项目分析工具

### Phase 3：体验优化（1 周）
- [ ] 上下文窗口管理（项目摘要、文件索引）
- [ ] Git 集成（自动 commit、查看 diff、撤销）
- [ ] 文件浏览面板
- [ ] 操作确认机制和备份

### Phase 4：高级能力（持续迭代）
- [ ] 代码搜索工具
- [ ] 向用户提问机制（ask_user）
- [ ] 多任务管理
- [ ] 项目模板生成

---

## 十一、与当前代码的对比

| 维度 | 当前实现 | 目标实现 |
|------|---------|---------|
| 交互方式 | 聊天问答 | 自主执行任务 |
| LLM 调用 | 纯文本生成 | Function Calling |
| 文件操作 | 无 | 读写编辑项目文件 |
| 构建能力 | 模拟 | 真实调用 Gradle |
| 错误处理 | 返回错误信息 | 自动分析修复 |
| 上下文 | 单次对话 | 项目级记忆 |
| 自主性 | 零（等用户输入） | 高（自主循环直到完成） |

---

## 十二、关键风险与应对

| 风险 | 应对 |
|------|------|
| LLM 生成错误代码导致项目损坏 | 修改前自动备份 + Git commit |
| Agent 陷入无限修复循环 | 最大迭代次数限制 + 用户中断 |
| Token 消耗过大 | 智能上下文裁剪 + 增量更新 |
| 手机上 Gradle 构建慢 | 支持远程构建 + 增量编译 |
| API Key 安全 | 本地加密存储，不上传服务器 |
