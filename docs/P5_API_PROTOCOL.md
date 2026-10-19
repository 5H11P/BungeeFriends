# P5 双端 API 与消息协议阶段记录

状态：公开 API、协议及双端处理代码已实现；Java 8 目标编译和有限单元契约测试通过。完整 Maven 构建、真实代理与后端消息联调尚未通过，因此 P5 整阶段待验收。

## 部署与入口

- 代理端同时安装 `BungeeFriends` 核心和 `BungeeFriends-BungeeAPI`；后者通过 `bungee.yml` 依赖核心。每个需要调用 API 或接收传送事件的 Spigot 后端安装 `BungeeFriend-SpigotAPI`。保留 `Main`、`net.simplyrin.bungeefriends` 和旧的 `BungeeFriends.getAPI()` 入口。
- 代理插件 `BungeeFriendsAPI.getInstance()` 提供同步查询、好友及组队操作。参数为玩家对象或 UUID；详情返回 DTO 列表，未知玩家的按名称查询返回空列表。存储失败以 `IllegalStateException` 等运行时异常传播；好友/组队变更保留原有受检异常。调用者应在 Bungee 允许访问玩家状态的上下文中使用，不应从 Bukkit 主线程跨端直接调用。
- 后端插件 `SpigotFriendsAPI.getInstance()` 在插件未启用时同步抛出 `IllegalStateException`，启用后返回 API 门面；方法以 `Player` 作为请求者并返回 `CompletableFuture`。响应的列表和布尔值会校验结构，代理拒绝返回带状态码的 `ApiException`，已发出请求遇到离线、停用、繁忙及超时时 Future 异常完成。Future 回调可能在 Bukkit 主线程执行；耗时工作应自行调度。取消 Future 会移除待处理请求并取消超时任务。
- 不带请求者的按名称查询使用一个在线且有 `friends.api.read-others` 权限的玩家作为承载者；没有此类玩家时 Future 异常完成。更推荐显式传入请求者。
- 代理侧 `PartyWarpEvent` 来自核心有界异步队列；后端侧同名事件在目标服务器的 Bukkit 主线程触发，内容是一次已排程传送的快照，不能视作连接成功回执。

## 协议 v1

| 字段/通道 | 约定 |
| --- | --- |
| `bungeefriends:api` | 请求、响应及命令投递；`bungeefriends:warp` 只传送事件 |
| 公共头 | 版本字节 `1`、消息种类、随机 UUID 请求 ID、操作者 UUID、签发毫秒时间、操作名、状态码、字符串字段数 |
| 种类 | `REQUEST=1`、`RESPONSE=2`、`COMMAND=3`、`EVENT=4` |
| 状态 | `OK=0`、`INVALID=1`、`DENIED=2`、`NOT_FOUND=3`、`ERROR=4`、`UNSUPPORTED=5` |
| 限额 | 整包最多 30000 字节；操作名最多 64 字符；最多 256 个字段，每字段最多 512 字符；拒绝尾随数据、截断及未知版本/种类 |
| 时间 | 请求、命令和事件接受签发时间在当前时间前 30 秒至后 5 秒内；时钟须同步 |

请求字段按方法参数顺序排列。好友及队伍列表是字符串列表；详情列表每五个字段组成一项；布尔值只接受 `true`/`false`；无队长时 `getOwner` 返回空字符串。错误响应的第一个字段是可读原因，调用者应以状态码分支，不解析文案。未知操作返回 `UNSUPPORTED`；无法解码、过期或来源不符的请求被丢弃，后端最终以超时结束。

代理把消息中的操作者 UUID 与承载玩家绑定，并核对该玩家当前所在服和发送服务器；执行前再次核对。读取其他玩家好友列表需要 `friends.api.read-others`；读取其他队伍需要该权限或双方同队。`addToParty` 需要 `friends.api.add-to-party` 和队长身份。`executeCommand` 需要 `friends.api.execute-command`、在线目标、目标仍属于操作者队伍、操作者仍是队长。请求 ID 在代理端保留 60 秒，重复请求复用响应；同 ID 不同内容被拒绝。

命令标签必须同时列在代理 `plugins/BungeeFriends-BungeeAPI/config.yml` 的 `Allowed-Commands` 和目标后端插件配置 `allowed-commands` 中，两端默认均为空。只允许无前导 `/`、无控制字符、最长 256 字符的单条命令。代理将命令投递到目标当前服务器，后端按目标 UUID 查找本服在线玩家并去重执行；承载消息的玩家可能是另一人。`executeCommand` 返回 `queued` 仅表示代理的服务器消息队列接受投递，不保证后端执行成功。后端不代替目标玩家绕过原命令权限。

后端请求每隔 60 tick 重试，最多发送 3 次；标准 20 TPS 下约 9 秒超时，低 TPS 会拉长实际耗时。玩家离线、插件停用和 Future 取消都会清理待处理请求。升级应同时替换代理与所有后端的 API JAR；混合协议版本会被拒绝并可能表现为超时。重启会清空内存去重缓存；跨重启的严格一次执行不在 v1 保证范围内，不应用于不可重复的高价值操作。插件消息以可信代理和后端插件为边界，不防御同一服务器内恶意插件伪造消息。

## 已有验证与剩余验收

2026-10-20：核心新增 API、Bungee API 和 Spigot API 以 `javac --release 8` 编译通过。Bungee 侧 JUnit 8 项、Spigot 侧 JUnit 5 项通过，覆盖协议版本/大小/畸形数据、命令白名单、来源与跨服队伍策略、请求去重、列表/布尔解析，以及成功、拒绝、超时、离线、重复响应、停用和重新启用时的 Future 状态。测试对策略和请求状态使用可控输入；没有完整穿过真实 Bungee/Spigot 消息入口。

完整 Maven reactor 在核心模块解析 `maven-replacer-plugin:1.4.1` 时失败，本地离线仓库没有该插件；手工 `javac` 验证不等于可部署 JAR 构建。取得依赖后须重新执行 `mvn clean test` 与 `mvn package`，检查双端 JAR 是否包含 `Protocol.class`、入口配置及许可证。

真实环境验收需使用一个代理、两个后端和至少三名玩家：在两个后端之间切换队长，分别验证查询成功/拒绝、队长给另一后端成员投递白名单命令、不同承载玩家收到响应和命令、重复消息仅执行一次、目标离线、权限撤销、插件停用/重新启用、超时与版本不一致。记录代理/后端日志、请求 ID、命令实际执行次数和每个 Future 最终状态；通过后再勾选 P5 的双端契约测试项。
