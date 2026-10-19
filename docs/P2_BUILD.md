# P2 六模块构建与验收记录

P2 只建立构建与插件入口骨架。核心保留 Fork 原有业务源码和 Java 包名 `net.simplyrin.bungeefriends`，主类仍为 `Main`；Bungee API、Spigot API 与三个示例暂为空入口，**此阶段的这些 JAR 不提供修改版功能**。不得把 P2 构建成功当作 P3–P7 的功能或真实服务器验收。

## 构建

从仓库根目录执行：

```sh
mvn -DskipTests clean package
python scripts/verify_p2_artifacts.py
```

本机使用 `C:\Program Files\Java\jdk1.8.0_202` 的 JDK 8 完成 `clean package`；Maven 3.9.9 默认 JDK 17 下再次 `package` 也成功。首次构建需在线解析 Maven 依赖。父 POM 统一以 Java 8 `release` 编译；检查脚本验证六个主 JAR 的主类、Fork 根 `LICENSE.md` 与 class file major 52。核心生成的 `Version.java` 位于 `core/target/generated-sources/version/`，不会改动已跟踪源码。

| 模块 | 产物 | 部署位置 / 当前状态 |
| --- | --- | --- |
| `core` | `core/target/BungeeFriends-2.4.jar` | BungeeCord；原版核心源码可编译，未做真实服务器回归。另保留原工程惯用的 `BungeeFriends-2.4-jar-with-dependencies.jar`。不要同时部署两份。 |
| `bungee-api` | `bungee-api/target/BungeeFriends-BungeeAPI-2.4.jar` | BungeeCord，依赖核心；P5 前仅入口骨架。 |
| `spigot-api` | `spigot-api/target/BungeeFriend-SpigotAPI-2.4.jar` | Spigot 1.16 系列；P5 前仅入口骨架。 |
| `examples/spigot-api-example` | `examples/spigot-api-example/target/bungeefriends-spigot-api-example-2.4.jar` | Spigot，依赖 Spigot API；P6 前仅入口骨架。 |
| `examples/party-cmd-example` | `examples/party-cmd-example/target/bungeefriends-party-cmd-example-2.4.jar` | Spigot，依赖 Spigot API；P6 前仅入口骨架。 |
| `examples/party-cmd-sender` | `examples/party-cmd-sender/target/bungeefriends-party-cmd-sender-example-2.4.jar` | Spigot，依赖 Spigot API；P6 前仅入口骨架。 |

Maven 依赖方向：`bungee-api` → `core`；三个示例 → `spigot-api`。Spigot API 通过插件消息与 Bungee 侧通信，Maven 层不依赖核心。核心/服务端 API 模块间的编译依赖均使用 `provided`，许可证由 Fork 根文件原样复制进 JAR。

## 与 P1 构建阻塞的关系

用户授权在线下载后，原版 Fork 已在 JDK 8 下成功 `mvn -DskipTests package`。原 POM 的 Lombok 1.18.0 在 Maven 默认 JDK 17 下编译失败；P2 核心依赖更新到 1.18.30 后，JDK 8 与 17 均可构建。一次 JDK 17 离线构建仍因 Maven 插件本地仓库来源标识不匹配而失败，随后在线构建成功；这属于本机缓存条件，不影响上述在线构建验收。

## P3 前需要保留的边界

- 原版多语言资源暂留在核心中；按计划到 P4 再只内置中英文。
- 核心的 `main` MySQL 键值表及旧同步/异步代码仍是原版，P3 才处理存储和任务生命周期。
- 双端 API 描述符只声明插件名、入口和依赖；命令、权限、消息通道与实际 API 将在 P5/P6 加入。
- 根许可 LGPL 2.1 与原源码部分 LGPL v3 文件头的不一致仍按 P1 记录；没有改写旧文件头。
