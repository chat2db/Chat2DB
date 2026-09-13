# Agent v2 完整工具输出

本功能仅通过 V2 工具网关和 Pi 适配层启用。V1 的工具返回、旧聊天存储、SQL 默认大字段预览保持原行为；内部完整值开关不接受 HTTP JSON 输入。

## 输出和权限

小结果直接返回。超过 32 KiB 时生成约 8 KiB 的结构化预览与 `output` 引用。文件只保存于应用数据目录的 `storage/ai-chat-history-v2/sessions/<sessionId>/tool-results/<runId>/`，按会话保留，删除会话时清理。SQL 每个 statement 的 `resultId` 对应一个 JSONL 文件，第一行保存元数据与列顺序，后续每行保存原始行数组；图表与模型读取复用同一份数据。旧 JSON 查询快照仍可读取。

`read`/`grep` 始终可读取当前会话的已发布结果和系统加载的 skill 文件。用户文件仍需开启对应工具，并处于用户工作目录；修改工作目录不移动系统结果。`find`/`ls` 按用户开关运行，大列表保存 JSONL；显式 limit 保留 hasMore。目录遍历不跟随符号链接、不访问受保护的系统数据；当前受控遍历不解释 `.gitignore`。Shell 沿用审批机制，cwd 本身不是操作系统沙箱。

工具执行状态与文件完整性分别记录。数据库分页 `hasMore` 表示还有查询页；`output.complete=false` 表示只保留了本次调用的一部分。读取部分文件也会返回来源警告。保存失败不会把已经成功执行的 SQL/命令改写成未执行，不得为恢复输出而重放写操作。

## 默认预算

| 配置 | 默认值 |
|---|---:|
| `chat2db.agent.v2.outputs.inline-bytes` | 32768 |
| `chat2db.agent.v2.outputs.preview-bytes` | 8192 |
| `chat2db.agent.v2.outputs.max-file-bytes` | 268435456 |
| `chat2db.agent.v2.outputs.max-session-bytes` | 1073741824 |
| `chat2db.agent.v2.outputs.max-total-bytes` | 5368709120 |
| JVM `-Dchat2db.agent.v2.outputs.max-capture-bytes` | 33554432 |

最后一项是 V2 SQL 调用累计保留值预算，跨 statement/resultset 共享。文本、CLOB、二进制通过 JDBC 流读取；达到预算后保留片段并标记 `CAPTURE_BUDGET_EXCEEDED`。该限制约束应用保留的数据，不能保证每种 JDBC 驱动内部均不缓冲。驱动专用 EXPLAIN 字符串继续使用既有驱动接口。

读取和搜索单页 JSON 返回约束在 16 KiB 内；支持超长单行的 UTF-8 游标续读。搜索每页最多扫描 4 MiB、返回 100 个命中；无命中且 hasMore=true 时必须继续游标才能判断文件是否包含内容。正则使用 RE2，不支持回溯引用或 lookaround；超长行采用有限窗口与 4 KiB 重叠并明确提示范围限制。普通关键词长度最多 512 个字符。

## 验证入口

- 后端：`AgentOutputStorageImplTest`、`AiAgentOutputServiceImplTest`、`AgentQueryResultStorageImplTest`、`AiAgentFileAccessServiceImplTest`、`AgentOutputControllerTest`、`AgentOutputFileExportTest`。
- V1 默认行为和 V2 捕获：`AgentFullResultValuesTest`、`DefaultSQLExecutorLargeCellTest`、`BoundedJdbcValueReaderTest`、`JDBCDataValueLargeCellTest`，以及 SQLServer/DM 方言回归。
- 前端：`yarn run test:agent-chat`、`yarn run lint`、`yarn run build:web:community --app_version=0.0.0`。
- Pi：在 agent 模块运行 `node --experimental-vm-modules --test src/test/js/chat2db-tools.test.mjs src/test/js/chat2db-tools-routing.test.mjs src/test/js/chat2db-output.test.mjs`。
- 数据库：`python3 script/test/agent-v2-lab/verify_live.py`。

Playwright 验收应覆盖实际查询生成大文件、预览外搜索、分段读取、下载、加载/取消/重试、查询结果绘图、审批拒绝和批准、命令运行中取消、切换工作目录、重启后沿用旧文件及跨会话访问拒绝。桌面保存对话框和 Windows PowerShell 需要各自平台验证。
