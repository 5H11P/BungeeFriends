# P3 存储与异步基础阶段记录

## 范围与语义

MySQL 模式只使用修改版的多表结构，不导入旧 `main` 表或 YAML 数据。切换存储前须自行备份；`mysql.yml: Enable: false` 继续使用 YAML。`Main` 对缺失字符串返回 `""`，对缺失列表返回空列表，对缺失布尔值返回 `false`；`StatusManager` 将缺失状态解释为 `Online`，组队管理器将缺失队伍解释为 `NONE`。列表按插入顺序保存 UUID 并去重。关系表是列表的唯一权威来源，玩家表中的旧文本列表列不再读写。

| YAML 逻辑键 | MySQL 表 / 列 | 缺失值 |
| --- | --- | --- |
| `Player.<uuid>.Name` | `bungeefriends_data.name` | `""` |
| `Player.<uuid>.Language` | `language` | `""` |
| `Player.<uuid>.Prefix` | `prefix` | `""` |
| `Player.<uuid>.CustomPrefix` | `custom_prefix` | `""` |
| `Player.<uuid>.Status` | `status` | `""` |
| `Player.<uuid>.Toggle` | `toggle` | `false` |
| `Player.<uuid>.LastOnline` | `last_online` | `""` |
| `Player.<uuid>.Friends` | `bungeefriends_friends(owner_uuid, friend_uuid)` | 空列表 |
| `Player.<uuid>.FriendSince.<friend>` | 上表 `created_at`，读取时转为毫秒时间戳 | `""` |
| `Player.<uuid>.IgnoreList` | `bungeefriends_ignore(owner_uuid, ignored_uuid)` | 空列表 |
| `Player.<uuid>.Requests` | `bungeefriends_friend_requests(owner_uuid, target_uuid)` | 空列表 |
| `Player.<uuid>.RequestSentAt.<target>` | 上表 `created_at`，毫秒时间戳 | `""` |
| `Player.<uuid>.FriendPrefix.<target>` | `bungeefriends_friend_prefix(viewer_uuid, target_uuid, custom_prefix)` | `""` |
| `Parties.<uuid>.Name` | `bungeefriends_data.party_name` | `""` |
| `Parties.<uuid>.Currently-Joined-Party` | `party_current` | `""` |
| `Parties.<uuid>.Toggle/AllInvite/Mute/Private` | `party_toggle/party_allinvite/party_mute/party_private` | `false` |
| `Parties.<uuid>.Party-List` | `bungeefriends_party_members(owner_uuid, member_uuid)` | 空列表 |
| `Parties.<uuid>.Requests` | `bungeefriends_party_requests(owner_uuid, invited_uuid)` | 空列表 |
| `Parties.<uuid>.RequestSentAt.<target>` | 上表 `created_at`，读取时转为毫秒时间戳 | `""` |
| `Name.<小写名字>` / `UUID.<uuid>` | 由 `bungeefriends_data.name/uuid` 查询 | `""` |

`Name` 和 `UUID` 在 MySQL 模式下由玩家名称派生。SQL `NULL` 与缺行对字符串调用者均表现为 `""`，字面字符串 `"null"` 保持原值。好友与组队请求都有时间戳；重启后读取时仍按有效期检查。初始化新玩家时 `Toggle` 由管理器设置为 `true`，并非缺失值默认 `true`。

## 实现与验证

- 建表、补列和索引在连接时执行；失败阻止 MySQL 模式启动。已有关系表补 `position_index` 时，旧行的原始顺序无法恢复，读取采用 UUID 次序直到下一次写入。
- 查询使用预编译参数。好友双方变更、关系列表替换和整队成员关系变更各使用事务；SQL 写入在调用返回前完成。失败抛出异常，不静默回退 YAML。连接失效后下一次操作只做一次有界重连；提交结果不明的写入不会盲目重试。JDBC 连接/读写超时分别为 5/10 秒。
- YAML 保存经临时文件替换，读流关闭；已有组队数据重启后不再被自动迁移清空。一般写入由停机保存，好友/组队关键关系及请求时间在变更时保存。
- 线程池最多 16 个工作线程、1024 个等待任务；溢出明确拒绝。停机先取消插件调度，最多等待任务 30 秒，之后中断并再等待 5 秒；未排空时保留 MySQL 连接。停机后提交被拒绝，重新启用插件时显式启动线程池。请求、队长离线及传送任务可取消，挂起的到期任务上限为 4096。没有 SQL 缓存。
- Bungee 没有单一代理主线程；其调度器任务和命令线程可能并发执行。组队关系变更使用管理器锁，YAML 数据使用数据管理器锁，JDBC 编辑器同步串行化。`ProxiedPlayer.connect` 和事件投递依赖代理 API 的线程约定，仍需真实代理端检查。

2026-10-20：核心源码及测试通过 `javac --release 8`；JUnit 4.13.2/H2 2.2.224 共 15 项通过。覆盖空值、重复关系、断线失败、重启读回、好友/组队事务回滚、请求时间、私有前缀、YAML 组队与请求细节读回、线程池溢出/排空/停机后拒绝。H2 的 MySQL 兼容模式不能替代真实 MySQL；没有真实 Bungee、MySQL 停机刷盘和多玩家并发验收。完整 Maven 构建仍被未缓存的 `maven-replacer-plugin:1.4.1` 阻断。

## 阶段结论

P3 主计划的 4 项中，数据语义和存储实现 2 项已勾选；异步生命周期和完整场景验证 2 项待验收。当前结果说明代码通过了本地编译和有限测试，不能证明真实 MySQL、代理端并发与停机过程均正确。后续人工操作、预期结果和记录栏见 [P3/P4 人工验证清单](P3_P4_MANUAL_VERIFICATION.md)。
