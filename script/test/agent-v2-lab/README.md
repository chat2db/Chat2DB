# Agent v2 MySQL 测试库

这套数据专门覆盖新的 Agent v2 数据发现、查询、chart skill、超大工具输出、按需文件读取、会话隔离和审批流程。它与旧测试库分开，只使用 `agent_v2_lab` 和 `agent_v2_scope_lab` 两个固定 schema。

目标是本地 Docker 容器 `mysql`，镜像 `mysql:8.4`，端口 `3306`。初始化脚本会先确认两个目标 schema 均不存在。密码只在容器进程内通过已有 `MYSQL_ROOT_PASSWORD` 转为 `MYSQL_PWD`，脚本不输出密码，也不将密码写入文件或命令参数。

数据库创建与 Chat2DB 数据源登记是独立步骤。初始化会创建固定测试库并执行数据库断言；Agent 和页面验收按下方清单执行。生成的 `10_data.sql` 不纳入版本控制，初始化前自动重建。

## 文件与执行顺序

| 文件 | 用途 | 是否写数据库 |
|---|---|---|
| `generate.py` | 生成确定性 schema、数据、预期结果和校验 SQL | 否 |
| `00_schema.sql` | 两个新库、中文注释、主外键和必要索引 | 是，仅新库 |
| `10_data.sql` | 固定业务和边界数据，约 537 KB SQL | 是，仅新库 |
| `20_verify.sql` | 53 项只读计数、关系、金额和大字段校验 | 否 |
| `verify_live.py` | 执行 53 项断言和 11 个图表查询，逐行比较精确预期，失败则非零退出 | 否 |
| `40_chart_queries.sql` | 11 种图表可复用查询及字段映射 | 否 |
| `45_multidimensional_charts.sql` | V2 分组、多维堆叠、双轴组合及分组散点查询 | 否 |
| `50_agent_scenarios.sql` | 按场景单独操作的验收目录，不能整体执行 | 混合，审批写入仅测试靶表 |
| `30_reset_cases.sql` | 恢复审批和幂等案例的基线 | 是，仅两个测试靶表 |
| `initialize.sh` | 目标库存在就停止；生成、创建、填充并校验 | 是 |
| `rebuild.sh` | 核对两个库的 owner/version/seed 后重建 | 是，仅固定且有正确标记的库 |
| `mysql.sh` | 使用已有本地容器内的 MySQL 客户端 | 由传入 SQL 决定 |
| `expected.json` | 精确行数、金额、各图表数据及字段哈希 | 否 |
| `ParseSql.java` | 使用已安装 Druid 进行离线 MySQL 语法解析 | 否 |

准备完成后，实际首次创建命令：

```bash
cd script/test/agent-v2-lab
bash initialize.sh
```

`initialize.sh` 不会复用或覆盖已有同名库，也不使用 `mysql --force`。失败后不能直接当作成功；应查看具体错误和两个库的标记，再决定是否使用受保护的重建脚本。

实际创建后额外核对数据文件磁盘占用，例如由操作者对容器中这两个目录执行 `du`。生成器计算的数据正文约 **7.0 MB**；即使计入 InnoDB 页、索引和存储开销，也预留了足够空间满足 **150 MiB** 上限。不要将容器整体磁盘变化视为这两个库的大小，其他任务可能同时使用容器。

## 业务数据与约束

所有日期固定在 2026 年 1—6 月，不依赖当前时间或随机数。订单每月分别为 24、36、48、60、72、84 笔；成交单价按月份逐步增长，图表有真实趋势而非六个月相同的值。

| 表 | 行数 | 主要用途 |
|---|---:|---|
| `customers` | 48 | 四个销售大区、三类会员、可空联系电话；id 1/2 故意同名 |
| `products` | 12 | 四类商品、标价、成本和库存 |
| `orders` | 324 | 订单状态、运费、折扣与成交总额 |
| `order_items` | 972 | 每单 3 条明细，保留成交价和数量 |
| `payments` | 289 | 259 笔成功、30 次失败尝试；收入只计成功 |
| `refunds` | 23 | 14 笔成功、9 笔待处理；净收入只扣成功退款 |
| `event_log` | 600 | 每行消息固定 4096 个 UTF-8 字节；200 行一页必定超过 512 KiB |
| `output_documents` | 4 | 2 MiB 长单行 TEXT、2 MiB JSON 正文、2400 行文本、控制字符 |
| `value_edges` | 6 | 高精度小数、SQL NULL、空串、空格、重复显示值、二进制值 |
| `approval_sandbox` | 2 | 审批、拒绝、事务和失败批次的固定写入靶表 |
| `idempotency_probe` | 0 | 显式唯一键幂等案例 |
| `_lab_manifest` | 1 | 固定 owner、version 和 seed，作为重建保护 |

`agent_v2_scope_lab` 中有 2 行 `customers`、2 行 `orders` 和 1 行 manifest。同名表的数据和结构不同，订单合计为 **3.33**，`scope_marker='scope_b'`。主库客户 id 1/2 与对照库 id 1/2 不是同一个业务对象。

必须保持这些财务定义：订单总额 = 明细金额 + 运费 − 优惠；失败支付不计收入；待处理退款不扣净收入。不能把商品当前标价替代订单明细中的成交价，也不能联接失败支付后重复累加订单。

基准汇总：

| 指标 | 预期 |
|---|---:|
| 成功支付金额 | 32265.51 |
| 成功退款金额 | 442.11 |
| 净收入 | **31823.40** |
| 订单状态 | CANCELLED 32，PENDING 33，PAID 33，SHIPPED 66，COMPLETED 160 |
| 漏斗 | 创建 324 → 支付 259 → 已发货 226 → 完成 160 |
| 支付渠道成功笔数 | ALIPAY 87，CARD 86，WECHAT 86 |

| 月份 | 所有订单 | 成功支付订单 | 成功支付金额 |
|---|---:|---:|---:|
| 2026-01 | 24 | 19 | 2201.77 |
| 2026-02 | 36 | 29 | 3354.52 |
| 2026-03 | 48 | 39 | 4731.98 |
| 2026-04 | 60 | 48 | 5947.80 |
| 2026-05 | 72 | 57 | 7223.87 |
| 2026-06 | 84 | 67 | 8805.57 |

## 11 类图表验收

在 V2 会话中加载 `/skill:chart`，让模型先发现表和列，再查询并使用真实返回的 `resultId` 绘图。每次图表所用数据与 `expected.json` 比较，不仅确认出现了图片。

| 类型 | 查询编号 | x / y | 结果行数 |
|---|---|---|---:|
| Column | C01 | month / revenue | 6 |
| Bar | C02 | category / revenue | 4 |
| Line | C03 | month / revenue | 6 |
| AreaLine | C04 | month / order_count | 6 |
| Pie | C05 | region / revenue | 4 |
| RingPie | C06 | provider / payment_count | 3 |
| RosePie | C07 | category / revenue | 4 |
| Funnel | C08 | stage / orders，保留 stage_order | 4 |
| Scatter | C09 | order_count / total_spend，两个轴均数值 | 48 |
| Statistics | C10 | 只设 yField=net_revenue | 1 |
| Combo | C11 | month，revenue 柱形、paid_orders 折线，左右轴 | 6 |

示例完整任务：“在 agent_v2_lab 中按月统计成功支付金额和成功支付订单数，用组合图展示金额柱形与订单数折线。不要计入失败支付，先检查元数据和实际结果。”

中文元数据发现：“找出用于统计已确认净收入的表，说明哪些支付和退款状态应参与，查询净收入并绘制指标卡。”预期需要 payments/refunds，答案 31823.40，而不是依靠表名猜测或把待处理退款计入。

## 大结果、文件和交互验收

多维图表使用 `45_multidimensional_charts.sql`：M01 按月份/地区返回长表，直接用 `groupBy=["region"]` 生成四个系列；M02 按月份/地区/渠道分组，用 `groupBy=["region","provider"]`；M03 将四个地区的金额柱堆叠在左轴、订单数折线放右轴；M04 验证散点图不会丢掉横坐标相同的客户。查询结果页大小设为 200，并检查 `hasMore=false`。金额合计仍应为 32265.51，M03 成功订单合计为 259。

页面验收检查每个系列的实际数据、图例切换、图表/表格切换和刷新恢复；缺失月份/地区组合应为缺失值而非零。另用专用结果测试 SQL NULL、空串、文本 `NULL`、分组标签含分隔符、同一月份/组重复行和超过 32 个系列。后两类应报参数/粒度错误且不生成图表，不能自动截断或聚合。

1. **O01：完整 TEXT。** 查询 `output_documents.id=1`。数据库原值恰好 2,097,152 个 UTF-8 字节，只有一行，含汉字与 emoji。初始工具响应必须只有有界预览与文件引用。通过 read/grep 定位 `NEEDLE_TEXT_TAIL_9F2A`，验证预览外内容真实可读。下载后解析 JSONL/JSON 提取该字段，以 `expected.json` 中 SHA-256 比对原字段，而不是把包装文件的哈希与原字段哈希混比。
2. **O02：完整 JSON。** `$.body` 恰好 2,097,152 个 ASCII 字符，`$.tail=NEEDLE_JSON_TAIL_7B3C`。不能依赖 MySQL JSON 对象键顺序，也不要比较 CAST 后的空格排版；按 JSON 语义验证正文哈希和 tail 字段。
3. **O03：多行与查询分页。** `db_query` 使用 `pageSize=200`，依次 page 1/2/3：id 范围 1—200、201—400、401—600；各 200 行，hasMore 为 true/true/false。每页自己的完整文件应只含这次实际取得的 200 行。不得因文件完整就宣称 600 行都已查询。
4. **文件读取分段。** 读取 O01 文件多次直到结束，每次正文不超过后端读取预算；nextCursor 必须前进。汉字、emoji 不得变成替换字符。超长 JSONL 行不应卡死或要求启用 Bash。对于 O07，文档内部换行在 JSONL 字符串里被转义，文件物理行号不等于文档内部的第 2399 行；搜索 `NEEDLE_LINES_2399` 仍应找到数据。
5. **O04：多 SQL。** 同一工具依次得到小结果、大结果、小结果；每条 statement 的状态、resultId 和 output 引用按顺序对应。文件预览、查看、下载不增加模型工具计数，后续模型 read/grep 才按真实调用计数。
6. **值保真。** O05 两列同名 `duplicate_name` 必须保留列顺序和值；第一行小数严格为 `9007199254740993.1234567890`。O06 区分 NULL、空串、前后空格和相同显示文本的不同 id。O07 保留 CRLF、tab、双引号、反斜杠、emoji 与 NUL。
7. **工具权限。** 关闭用户 read/grep/bash/write/edit 后，系统结果的 read/grep 仍可用；用户工作目录文件仍遵循关闭状态。切换工作目录不改变已保存结果。已加载 chart skill 的说明仍可读。不要将工作目录当成 Shell 沙箱。
8. **当前会话隔离。** 用会话 A 生成文件，在会话 B 传 A 的 artifactId/绝对路径应拒绝；同一账号的两个会话也必须隔离。`..`、用户工作目录中的指向系统目录的符号链接不能绕过只读和归属检查。删除专用测试会话后文件应不可读；不得删除用户历史会话。
9. **重启与 UI。** 保留 A，重启独立测试后端，恢复 A 后查看、搜索、下载仍可用。打开工具详情后连续触控板等效滚动、快速搜索、立即取消、切换会话再回来；旧请求不能覆盖新结果，详情和滚动位置不应被每批 Agent 事件重置。检查网络响应大小，不能把完整字段藏在 details 或事件里。
10. **不足额与失败。** 配额、磁盘写失败和取消使用独立 runtime 的专用配置/临时输出目录测试，不填满此 MySQL 容器或用户磁盘。先用较小配额查询 O01，已保存部分必须明确 complete=false 并可通过引用读取；不得把成功 SQL 改报“执行失败”，不得自动重跑有写入的批次。容量保护必须在超大值占满堆之前生效，另以受控内存预算测试；本 fixture 默认数据无需制造 OOM。
11. **重复调用。** 同一 run/toolCallId 的传输重放应得到相同 artifact 引用；不同 toolCallId 的独立 SELECT 可以产生不同文件。A05 是显式 SQL 唯一键幂等案例，两次执行仍仅一行 attempts=1，不能把这个结论外推为 Agent 会自动去重普通 UPDATE。
12. **V1 对照。** 在 V1 会话查询同一大字段，确认仍使用原工具协议、原截断/预览行为，不出现 V2 output 文件入口；普通 SQL 编辑器的大字段预览仍工作。V2 目录不应用于 V1 的结果生命周期。

每项记录 sessionId、runId、toolCallId、artifactId、实际工具 description/耗时、请求次数及关键结果；只保留专用测试会话的数据，不混用用户正在操作的会话。

## 审批、事务和失败批次

仅使用 `approval_sandbox` 与 `idempotency_probe`。每轮开始执行 `30_reset_cases.sql`。

- A01 普通 SELECT 无写入审批；id 1/2 金额为 100.00/200.00。
- A02 第一次拒绝 UPDATE，note 必须保持 baseline；第二次批准，note 变为 approved_once，金额不变。拒绝后不能继续执行该条写入。
- A03 批次经批准后执行 START TRANSACTION、两次 UPDATE、ROLLBACK 和 SELECT。两个金额仍为基线；验证是在同一有效事务上下文中执行。
- A04 先重置，然后批准“UPDATE；失败 SELECT；UPDATE”批次。错误继续关闭时，第一条在 autocommit 下已生效为 101.00，中间列不存在报错，最后一条未执行，id 2 仍为 200.00。模型需报告部分执行，不能自动重放导致 id 1 再加一次。
- A05 明确幂等 INSERT 连续两次后，唯一键 agent-v2-lab:once 只有一行，payload=fixed-payload，attempts=1。

## 重置与清理

恢复可变案例，不动业务基线：

```bash
bash mysql.sh < 30_reset_cases.sql
python3 verify_live.py
```

完整重建仅允许固定两个 schema，并在任何 DROP 之前完成全部现存库的 owner/version/seed 检查；无标记、标记不符或读取失败一律停止。不存在的一个 schema 不妨碍恢复另一个已确认属于本 fixture 的 schema。

```bash
bash rebuild.sh --confirm-owned-schemas
```

脚本不删除其他库、容器、数据源、历史会话或磁盘目录。若初始化在创建标记之前被外部中断，应人工核对具体残留，不能绕过所有权检查强行重建。

## 已完成的离线检查

- 先执行 `python3 generate.py`，再执行 `python3 generate.py --check`：检查业务金额关系、外键引用、成功支付唯一性、退款金额边界、48 点散点图、漏斗、大小预算和生成文件是否一致。`--check` 不写文件、不连接数据库。
- 两次独立生成后的 schema/data/expected/verify 文件 SHA-256 应完全相同。
- `bash -n` 检查全部 shell 脚本；Python 语法编译检查。
- Druid 1.2.18 MySQL 解析：schema、数据、断言、重置、11 图表及场景 SQL 全部通过。它是离线语法检查，不代替 MySQL 8.4 实际执行。
- 执行初始化之前再次确认容器版本及目标 schema 不存在；初始化后运行 `python3 verify_live.py` 验证真实数据库。

初始化后继续完成真实 Agent v2/V1 与 Playwright 验收、实际磁盘占用和受控配额测试。
