# P6 示例、配置与质量阶段记录

状态：三个示例已从空入口迁入，JDK 8 六模块干净构建、31 项自动测试及 JAR 静态检查通过；真实双端示例联调尚未执行，不能判定 P6 整阶段验收完成。

## 部署与操作

1. 先在代理安装本仓库同一次构建的 `BungeeFriends` 与 `BungeeFriends-BungeeAPI` JAR，在两个 Spigot 1.16 后端安装 `BungeeFriend-SpigotAPI`。三个示例只装在需要操作的后端，均通过 `plugin.yml` 的 `depend` 等待 Spigot API。不要同时部署同模块的普通 JAR 和 `jar-with-dependencies` JAR。
2. `spigot-api-example` 提供 `/example friends`、`/example party`、`/example check <玩家>`、`/example isleader`，权限 `bungeefriends.api.example` 默认开放。它只调用公开查询 API，并将异步结果切回 Bukkit 主线程显示。
3. `party-cmd-example` 提供 `/partycmd <队员> <命令> [参数]`，权限 `partycmdexample.use` 默认开放。它先查询队长身份，再通过公开 `executeCommand` 给一个在线队员投递；返回“已排队”不表示目标已经执行。
4. `party-cmd-sender` 监听未取消的玩家命令；仅当命令匹配其 `config.yml` 的 `commands` 规则，且 API 返回的队伍快照确认发起者为队长时，向每名在线队员发起一次 `executeCommand`。默认 `commands: []`，须显式启用。规则支持 `say` 或 `bw join $args` 形式，按完整命令词边界匹配；同名重复成员只投递一次。
5. 命令标签还须同时列入代理 `plugins/BungeeFriends-BungeeAPI/config.yml` 的 `Allowed-Commands` 和**每个目标后端** `plugins/BungeeFriend-SpigotAPI/config.yml` 的 `allowed-commands`。两端默认都为空；目标玩家仍须拥有原命令本身的执行权限。先在测试环境只允许无副作用的命令，再测试跨服投递。

示例建议先只安装 API 查询示例，确认双端协议可用后再安装一个命令示例。`party-cmd-example` 是显式单目标投递；`party-cmd-sender` 是自动跟随，配置包含 `/partycmd` 标签时可能产生两套业务触发，部署时不要把它加入自动跟随规则。

## 从修改版迁移

- 停止代理及后端并备份插件配置、YAML 数据与 MySQL 数据库。修改版使用 `net._5h11p.bungeefriends`，本 Fork 保留 `net.simplyrin.bungeefriends`；下游插件必须更新 import 并重新编译。Java 入口仍为 `Main`，原 `BungeeFriends.getAPI()` 不变。
- 先保留原数据副本，在隔离环境验证新核心的数据映射。YAML 与 MySQL 模式不会自动互相导入；不要在生产数据目录直接切换存储模式。数据库结构、状态及旧数据边界见 [P3 记录](P3_STORAGE.md) 与 [人工验证清单](P3_P4_MANUAL_VERIFICATION.md)。
- 修改版 `party-cmd-sender` 的 `commands` 规则可按标签或 `子命令 $args` 形式迁入，但本 Fork 默认清空，需逐条审查。旧版同服直接调度执行、跨服另走请求的路径已统一为代理投递；不迁入旧的自定义消息请求或 `List.of` 调用。
- API 协议固定为 v1；代理与所有后端一起升级。旧修改版协议/JAR 不与本 Fork 的 v1 混用。插件数据目录名称和 `plugin.yml` 依赖名称以本仓库构建产物为准；分别复核旧配置键和授权节点，切勿仅凭旧文件存在就视为已生效。
- 验证通过后才替换生产 JAR；保留旧 JAR 和数据备份作为回退点。回退须成套恢复代理、后端 API 与示例 JAR；在新版中写入的数据是否能由旧版读取，需要在隔离环境另行证明。

## 质量问题核对

| 项目 | 修改版基线 | 本次改动 | 验证及限制 |
| --- | --- | --- | --- |
| 重复查询 | 原跟随监听器依次调用 `isInParty`、`isPartyLeader`、`getPartyMemberDetailList` | 使用一次 `getPartyMemberDetailList`，从成员快照确认队长并选目标 | 源码路径核对；没有真实负载与延迟数据，不能声称吞吐提升 |
| 重复执行 | 原监听器同服直接延迟执行、跨服再发请求 | 所有目标统一走公开 `executeCommand`；去除重复 UUID，仅在线成员且跳过队长 | `ForwardingPlanTest` 覆盖重复/离线/队长；真实代理重试及停机后的执行次数仍需 P5/P7 联调 |
| 日志噪声 | 原示例启停及每次命令成功都写日志 | 被动跟随不记录成功投递，仅在查询或投递失败时记录原因 | 源码核对；尚无运行时日志量基线 |
| 生命周期 | 原示例异步回调可能在玩家离线或插件停用后访问 Bukkit 对象，且有额外延迟任务 | 回调切回主线程并检查启用/在线；示例不持有定时任务，待处理请求由 P5 API 清理 | Java 8 编译；真实重载与停机竞态需联调 |
| 公开 API | 原命令示例直接引用 `SpigotAPIPlugin` 与泛型 `sendCustomRequest` | 三个示例只调用 `SpigotFriendsAPI`，使用类型化查询和 `executeCommand` | Java 8 编译及静态依赖检查通过 |
| 测试运行路径 | YAML 测试将临时目录固定为 `core/target`，从 Maven 模块目录启动时失败 | 改用系统临时目录，不依赖工作目录 | JDK 8 `clean test package` 中核心 15 项通过 |

## 构建、测试与许可

2026-10-20：三个示例先以 `javac --release 8` 编译通过。随后本机使用 JDK 8、原有 Maven 全局镜像配置和用户仓库缓存执行离线 `clean test package`，七个 reactor 项目全部通过；核心 15 项、Bungee API 8 项、Spigot API 5 项、命令示例 3 项测试合计 31 项通过。`python scripts/verify_p2_artifacts.py` 验证六个 JAR 的入口、Fork 许可和 class major 52；双端 API JAR 含 `Protocol.class`，跟随示例 JAR 含 `config.yml`。直接使用当前沙箱的默认 Maven 仓库仍可能因无下载权限失败；JDK 17 从用户目录读取缓存 JAR 时也会出现访问异常，不能用其表面上的 `BUILD SUCCESS` 代替上述 JDK 8 干净构建。该结果仍不证明示例已在真实服务器运行。

来源审计见 [P1 差异矩阵](audit/P1_DIFF_MATRIX.md) 和 `audit/license_headers.tsv`。Fork 根 `LICENSE.md` 与父 POM 声明 LGPL 2.1；原有部分 Java 文件头声明 LGPL v3/MIT；核心描述符列出 `SimplyRin, 5H11P`，API 与三个示例描述符列出 `5H11P`。本次未改写旧文件头，也未把两种 LGPL 声明视为已统一。Maven 资源配置将 Fork 根许可放入六个模块；最终 JAR 内容及许可表述仍须在 P7 发布前核对。

## 人工验证记录

| 场景 | 操作及预期 | 结果 / 证据 |
| --- | --- | --- |
| 查询成功与拒绝 | A/B/C 在两服分别运行 `/example` 查询；记录好友、空队伍、离线玩家及代理拒绝响应 | 待填 |
| 显式跨服 | A 为队长、B 在另一服，用 `/partycmd B say test`；B 恰执行一次，A 得到排队反馈 | 待填 |
| 自动跟随 | 两端白名单只允许 `say`；A 执行一次，在线同服及异服成员各执行一次，离线成员不执行 | 待填 |
| 切服与权限 | A 切服后重试；撤销队长或 `friends.api.execute-command` 后应被代理拒绝 | 待填 |
| 生命周期 | 请求中令 A 离线、停用/重启后端 API、重复发送同请求 ID；Future 有终态，命令不重复 | 待填 |

人工记录须包含 Git 提交、六个 JAR 的 SHA-256、代理/Spigot/Java 版本、三名玩家及两服名称、权限和白名单配置、日志时间与执行次数。P5 的完整双端契约与 P7 的真实环境发布门槛仍独立保留，不能用本页的预期结果代替通过记录。
