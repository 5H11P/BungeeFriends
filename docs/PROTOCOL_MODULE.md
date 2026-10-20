# 协议 Maven 模块

`protocol` 是 Maven reactor 中的共享协议模块，坐标为 `net.simplyrin.bungeefriends.api:BungeeFriends-Protocol:${project.version}`。它维护代理与 Spigot API 共用的消息类型、编码解码和校验逻辑，目标字节码为 Java 8。

当前 reactor 包含四个 JAR 模块和一个父 POM，共五个 Maven 项目：核心、协议、Bungee API 和 Spigot API。

`bungee-api` 与 `spigot-api` 依赖此模块，并在打包时将协议类合并进各自的 API JAR，不改变 `net.simplyrin.bungeefriends.api.protocol.Protocol` 的类名。代理和后端仍按原有方式安装 API 插件；`BungeeFriends-Protocol` JAR 是构建依赖，不是可单独安装的服务器插件。

协议单元测试位于 `protocol/src/test`。从仓库根目录运行完整验证：

```sh
mvn clean test package
python scripts/verify_p2_artifacts.py
```

## 构建脚本

| 脚本 | 用途 | 文档引用 |
| --- | --- | --- |
| `scripts/migration_snapshot.py` | 对照相邻的 `BungeeFriends-And-Party` 工作区与 Fork 基线，生成文件清单、哈希、差异和许可头审计数据；读取源工作区，并写入本仓库的 `docs/audit/`。 | `docs/audit/P1_DIFF_MATRIX.md`、`docs/MIGRATION_PLAN.md` |
| `scripts/migration_surface_inventory.py` | 用正则扫描命令、权限、配置键、中英文语言键、API 方法和插件消息请求类型，生成 `docs/audit/surface_inventory.tsv`。结果是待人工核对的词法清单。 | `docs/audit/P1_DIFF_MATRIX.md`、`docs/MIGRATION_PLAN.md` |
| `scripts/verify_p2_artifacts.py` | 检查插件 JAR 的描述符、入口类、许可、包名和 Java 版本；另检查协议库 JAR，并确保协议类包含在两个 API JAR 中。 | `docs/P2_BUILD.md`、`docs/P6_EXAMPLES.md` |

迁移审计脚本用于重建审计材料，不属于 Maven 构建步骤。产物验证脚本要求先完成 Maven 打包；协议 JAR 不含插件描述符，也不应作为插件部署。
