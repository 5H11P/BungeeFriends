# P1 差异台账与迁移评估

> 审计日期：2026-10-20。相对路径以工作区根 `bfp/` 为准。此处是静态源码审计；运行行为尚未经过真实服务器验证。
>
> 精确逐文件/逐键清单见 `source_manifest.tsv`、`fork_baseline_manifest.tsv`、`source_git_status.txt`、`file_comparison.tsv`、`surface_inventory.tsv`、`license_headers.tsv`。可在 Fork 根运行 `python scripts/migration_snapshot.py` 和 `python scripts/migration_surface_inventory.py` 重建。源工作区若变化，重建清单并核对 SHA-256 后才能继续迁移。

## 1. 快照及比较口径

| 项目 | 结果 |
| --- | --- |
| Fork 基线 | Git `bc22a63`，67 个跟踪文件；P0 文档提交为 `26a1c9b`。本阶段不修改核心业务源码。 |
| 修改版来源 | Git `1801c4e` 加当前工作区，共 92 个非 `.git/`、非 `target/` 文件；`source_git_status.txt` 保存逐文件状态。 |
| 与 Fork 对应 | 47 个“同领域但内容/结构变化”，2 个仅机械包名/主类变化，43 个在 Fork 中无直接对应路径。按来源另分为 48 个继承 Fork 候选、32 个修改版扩展/搬移、7 个工作区新增、2 个许可文本和 3 个生成物。分类只作导航，不代表无对应路径的文件全是新增功能。 |
| 修改版工作区变化 | 与修改版 Git HEAD 相比：4 个字节不变、16 个机械包名/类名变化、62 个其他变动、10 个现有工作区新增文件。3 个 `dependency-reduced-pom.xml` 属 Maven 生成物，不能作为迁移源码。 |
| 审计限制 | 文件内容哈希已固定；纯文本扫描提取的是候选接口/配置/消息键，需在对应阶段复核动态键、反射和运行时分支。 |

### 文件级取舍规则

- `file_comparison.tsv` 中 `changed-or-restructured`：保留 Fork 对应文件并逐函数合并，**不**以修改版覆盖。
- `mechanical-rename-vs-fork`：优先沿用 Fork 文件；仅在确有业务改动时迁入。修改版 `CorePlugin` 不能替代原入口 `Main`。
- `new-vs-fork`：按功能阶段引入；其中许可文本、构建产物及示例要分别处理。`source_head_candidate` 帮助追踪修改版 Git 内的包名迁移/示例拆分来源。
- `license-text`：Fork 根 `LICENSE.md` 为优先依据，不复制修改版 LGPL v3 根许可覆盖它。现有 MIT 文件头原样保留。

## 2. 功能差异与处置矩阵

| 功能 / 行为 | Fork | 修改版证据 | 处置 | 验收方式 |
| --- | --- | --- | --- | --- |
| 核心身份、构建 | 单模块，`Main`，`net.simplyrin.bungeefriends`，2.4 | 根及各子模块 POM、`core/.../CorePlugin.java`、三个 `plugin.yml`，2.5/1.1.0 | P2 重新设计 Maven 模块关系；保留 Fork 主类、包名与插件身份 | Reactor 构建、检查 JAR 入口/依赖/许可资源 |
| 原有好友、组队、聊天、私聊 | 16 个已有入口及主体逻辑 | `core/.../commands/` 对应类大量改动 | P4 逐项迁移并回归旧命令，不整类覆盖 | 新旧行为矩阵、权限与配置回归 |
| 新增命令入口 | 无 `/ac`、`/achat`、`/block`、`/ignore`、`/pl`、`/status`、`/apitest` | `core/.../CorePlugin.java` 注册处；`surface_inventory.tsv` | 前 6 项 P4 迁移；`/apitest` 属调试入口，待确认是否仅开发环境提供 | 每条命令的成功/拒绝/禁用别名场景 |
| 好友增强 | 基础好友/请求/忽略 | `FriendCommand.java` 有 `removeall`、`force-add`、`prefix resetall` 等；好友备注、请求时间与列表显示改动 | P4 迁移，按 P3 存储映射复核；权限不可扩大 | 列表、备注、请求有效期、离线玩家及批量操作 |
| 状态与可见性 | 无 `/status` | `StatusCommand.java`、`StatusManager.java`，在线/离开/忙碌/隐身 | P4 迁移，P3 必须先修复 MySQL 的 `Status` 缺失映射 | YAML/MySQL 两模式重启读回及隐身可见性 |
| 组队扩展 | 基础邀请/入退队/warp | `PartyCommand.java` 的人数上限、踢离线、全员邀请、静音、私有队伍、确认式 warp；`PartyWarpConfirmManager` | P4 迁移；邀请、传送和过期任务重设计调度 | 跨服、离线、退队、超时、确认/取消和权限 |
| 聊天/Staff | 原 `/chat`、组队/私聊 | `AllChatCommand.java`；`ChatCommand.java` 含 all/party/staff 模式 | P4 迁移，Staff 接收与发送权限均测试 | 切换频道、多人广播、禁言/权限 |
| YAML 与多表 MySQL | `main` 键值表 + YAML | `core/.../tools/MySQL.java` 七表；`CorePlugin` 异步写队列 | P3 重新实现映射与失败语义；仅支持新表结构，YAML 保留 | 建表、CRUD、重启读回、故障和停机刷盘 |
| 性能与异步 | 简单共享线程池、同步/异步混用 | `ThreadPool.java`、`CorePlugin`、命令和监听器增加异步/缓存/延迟 | P3 建生命周期和调度约束，P4 随功能迁移 | 线程数、队列上限、超时和关服后无任务 |
| 核心 Bungee API | 内部 `BungeeFriends.getAPI()` 工具接口 | `core/.../api/BungeeFriendsAPI.java`，好友/组队 CRUD 与详细 DTO | P5 新增外部 API，保留旧入口并定义线程/错误契约 | API 合约与宿主插件示例 |
| 双端 API | 无 | `bungee-api`、`spigot-api`：插件消息通道及 Future | P5 迁移协议与两端实现，统一数据类型和错误模型 | 14 类请求契约，超时、离线、畸形/重复消息 |
| Warp 事件 | 无跨端事件 | 核心 `PartyWarpEvent` + 两端 dispatcher/event | P4 建核心事件，P5 接跨服事件，明确投递与去重语义 | 单次触发、重复/延迟消息、玩家切服 |
| 三个示例 | 无 | `examples/spigot-api-example`、`party-cmd-example`、`party-cmd-sender` | P6 迁移，示例仅用公开 API | 三个 JAR 可构建并在 P7 联调 |
| 语言 | Fork 多语言 | 修改版只含 `Chinese.yml`、`English.yml` | P4 内置中英文；旧翻译文件按计划边界处理 | 两种语言键一致、命令消息可显示 |

## 3. 命令、权限、配置和消息表面

`surface_inventory.tsv` 逐项列出来源、Fork 对应、拟处置和测试。其命令扫描有 23 个入口：16 个两边共有，7 个修改版独有。子命令需按 `FriendCommand.java`、`PartyCommand.java` 逐分支核对；修改版组队有 `invite/leave/list/remove/kick/promote/hijack/warp/accept/toggle/disband/kickoffline/setting allinvite/mute/private/chat`，好友有 `add/remove/removeall/accept/deny/list/lang/ignore/force-add/reload/prefix/toggle`。同名子命令不表示行为相同。

权限节点扫描有 17 个：8 个共有，新增 9 个：`friends.chat.staff`、`friends.command.api-test`、`friends.command.force-add`、`friends.command.lang-update`、`friends.command.tell-bypass`、`friends.limit.bypass`、`friends.notify.update`、`friends.party.hijack`、`friends.party.private`。它们的默认授权、管理员覆盖与控制台行为需要 P4/P5 验证，不能只复制常量。

配置键扫描有 79 个：47 个共有、20 个修改版独有、12 个只在 Fork 出现。新增重点为 `Plugin.PartyWarp.*`、`Plugin.NoPartyWarp-Servers`、`Plugin.Party-Max-Size`、离线踢出/解散时间、LuckPerms 离线缓存/超时、`Enable-Status`、好友列表时间格式及新增别名开关。部分键是旧值迁移或动态前缀，不应把文本扫描结果直接当最终默认值；P4 要核对 `ConfigManager` 创建、重载与旧键迁移。

中英文资源各扫描到 300 个层级键：212 个共有、87 个修改版新增、1 个 Fork 独有（`Chat.NeedInAParty`）。修改版中英文键集合一致。P4 需核对这些键的调用方与实际文本，避免删除仍被引用的 Fork 键。

## 4. 存储键、表与映射

| 逻辑键 / 领域 | 修改版 MySQL 表或列 | 当前风险 / P3 验证 |
| --- | --- | --- |
| `Player.<uuid>.Name/Language/Prefix/CustomPrefix/Toggle/LastOnline` | `bungeefriends_data` 对应列 | 默认值、`NULL` 与空字符串区分；名称索引及大小写 |
| `Player.<uuid>.Friends` | `bungeefriends_friends(owner_uuid, friend_uuid)` | 双向好友关系、重复/撤销及部分写入；旧冗余 `friends` 列仍在数据表中 |
| `Player.<uuid>.IgnoreList` | `bungeefriends_ignore(owner_uuid, ignored_uuid)` | 空列表、重复及撤销；旧冗余 `ignorelist` 列 |
| 好友请求及时间 | `bungeefriends_friend_requests(owner_uuid, target_uuid, created_at)`；玩家表仍有 `requests` 列 | 请求方向、创建时间、过期与重启一致 |
| `Parties.<uuid>.Name/Currently-Joined-Party/Toggle/AllInvite/Mute/Private` | `bungeefriends_data.party_*` | 布尔缺失值和解散后的清理 |
| `Parties.<uuid>.Party-List/Requests` | `bungeefriends_party_members` / `bungeefriends_party_requests` | 队长、成员和邀请的原子一致；冗余列表列 |
| 好友自定义前缀 | `bungeefriends_friend_prefix(viewer_uuid, target_uuid)` | 查看者私有备注及清理 |
| `Name.<name>` / `UUID.<uuid>` | `bungeefriends_data.name`/`uuid` 查询 | 更名、重名、离线查询与索引 |
| `Player.<uuid>.Status` | **无映射列** | 修改版 `StatusManager` 写入，但 `playerColumn` 未映射；P3 阻塞修复 |

七张表的精确 DDL、索引与方法见 `core/src/main/java/net/_5h11p/bungeefriends/tools/MySQL.java`。现有 `set()` 遇到未知键常返回 `true`，部分查询捕获 SQL 异常后返回空列表/空串；`CorePlugin` 中有 `String.valueOf(value)` 写入及 10 秒停机等待。P3 要让未知映射/失败可见，明确队列未完成时的处理。原版 `tools/MySQL.java` 的动态拼接 SQL 与先删后更新逻辑不能继承。

## 5. 双端 API 协议与示例

| 部分 | 修改版表面 | P5/P6 处置 |
| --- | --- | --- |
| Bungee 公开 API | `BungeeFriendsAPI` 主类及 `FriendInfo`/`PartyMemberInfo` DTO。清单中 33 个 public 方法名包含 DTO getter/setter；主接口含好友/组队查询与变更、私有队伍、详细列表。 | 明确 DTO 不可变性、线程、空值/异常；保留原工具入口。 |
| Bungee 消息处理 | `BungeeAPIPlugin` 有 14 个请求类型：`getFriendList`、`getFriendDetailList`、`getFriendDetailListByName`、`getPartyMembers`、`getPartyMemberDetailList`、`getPartyMemberDetailListByName`、`getPartyMemberDetailListByUUID`、`areFriends`、`isInParty`、`isPartyLeader`、`isPartyPrivate`、`getOwner`、`addToParty`、`executeCommand`。 | 建版本化协议，逐项限定可调用者、参数、类型、大小、错误和响应。写操作特别审计权限。 |
| Spigot 公开 API | `SpigotFriendsAPI` 清单中 19 个 public 方法名包含 DTO getter；提供 Future 查询及 `sendCustomRequest`。 | 去掉不安全的裸类型转换；Future 在成功、拒绝、超时、玩家离线及插件停用时均结束。 |
| 跨服命令 | `PartyCmdSenderListener` 和 Bungee `executeCommand`；当前 Bungee 侧只查目标是否属于某队。 | 验证发起者为目标队伍队长，支持队长切服后代理端校验，限定命令范围，消息去重。 |
| 三个示例 | 基础 API、队长命令示例、监听配置命令的 sender 示例。 | P6 独立构建与部署说明；`List.of` 改为 Java 8 可用写法。 |

协议接收侧当前检查 `PluginMessageEvent` 发送者是 Server、接收者是玩家，但 JSON 类型强转、来源玩家/服务器状态和请求权限还需审计。Spigot `handleResponse` 将成功响应解包为 `data`，`SpigotFriendsAPI.getPartyMembers` 与 `areFriends` 却按完整 `Map` 解析，这两项已确认不一致。`sendCustomRequestByName` 的载体选择和所有写操作均需逐项限制，不能只凭携带消息的玩家身份授权。

## 6. 许可、署名与来源

| 来源 | 已见声明 | 处置 |
| --- | --- | --- |
| Fork 根及 `src/main/resources/LICENSE.md` | GNU LGPL 2.1 全文 | 以 Fork 根文本为当前项目基准；P2 打包需核对许可文件。 |
| Fork Java 文件 | 38 个 LGPL v3 文件头，3 个 MIT 文件头（逐文件见 `license_headers.tsv`） | 保留原头和 SimplyRin 署名，不批量改写；这一现存不一致需发布前核实。 |
| 修改版根 | LGPL v3 全文，另有 `GPL-3.0.txt`；父 POM 声明 LGPL-3.0-only | 不直接复制成 Fork 根许可或父 POM 元数据。 |
| 修改版当前 Java | 37 个 LGPL v3 文件头、3 个 MIT 文件头、19 个未见明确许可头；部分继承类署名 `SimplyRin, 5H11P` | 逐文件按来源和贡献保留/补充适当署名，避免把继承代码标成仅 5H11P。不能仅凭无文件头推断无许可。 |
| 插件描述符 | Fork 核心 `author: SimplyRin`，修改版核心 `author: 5H11P` | P2 核心作者改为 `SimplyRin, 5H11P`；新增 API 模块按代码来源核对作者。 |

这里是源码声明审计，不是对 LGPL 2.1、LGPL v3 与 MIT 混合情况的法律结论。已经约定保留 Fork 根许可及既有文件头；发布前需复核实际分发包和新增文件的声明。

## 7. 构建基线、阻塞与待确认项

Fork `pom.xml` 配置 `source`/`target` 1.8，但本机 `mvn 3.9.9` 使用 JDK 17，命令行 `java` 为 JDK 21；尚未验证 Java 8 实际运行。以下两次尝试均在 Maven 插件解析阶段失败，**未进入 Java 编译**：

```text
cd BungeeFriends
mvn -o -DskipTests package
  -> build-helper-maven-plugin:3.0.0 未缓存，离线无法解析
mvn -DskipTests package
  -> aliyun-maven 下载该 POM 被环境拒绝（Permission denied）
```

P2 前需可用 Maven 依赖仓库或完整离线缓存；再运行相同构建，并用 Java 8/JAR class 版本检查产物。P1 不改构建逻辑来掩盖环境问题。仓库原有未跟踪 `BungeeFriends/target/` 未纳入本阶段。

| 编号 | 类型 | 待确认/后续处置 |
| --- | --- | --- |
| D1 | 维护者取舍 | `/apitest` 是否只作为开发调试命令，不打进正式版本？默认建议仅开发环境提供；不影响 P2/P3。 |
| D2 | 发布前复核 | Fork 根 LGPL 2.1 与原文件 LGPL v3 头并存；目前保持原样，按已约定原则推进，P6 核对分发文本。 |
| D3 | 环境阻塞 | Maven 依赖下载权限/本地缓存不足；P2 构建验收必须解决。 |
| D4 | P5 安全设计 | 双端写请求和跨服命令的权限策略/白名单具体边界需随协议设计确认，不能照搬现状。 |

本阶段未迁移业务功能，也未更改两个项目的源码、许可或插件描述符。下一阶段 P2 只处理结构与构建骨架。
