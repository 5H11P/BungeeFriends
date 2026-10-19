# BungeeFriends 公开 API

当前 Java 包名是 `net.simplyrin.bungeefriends`。代理端安装核心 `BungeeFriends` 和 `BungeeFriends-BungeeAPI`；游戏服安装 `BungeeFriend-SpigotAPI`。下游插件将对应 API JAR 设为 `provided`，并在插件描述符声明依赖。协议、错误码与线程边界见 [P5 协议记录](P5_API_PROTOCOL.md)。

## 代理端

```java
import net.simplyrin.bungeefriends.api.BungeeFriendsAPI;

BungeeFriendsAPI api = BungeeFriendsAPI.getInstance();
boolean friends = api.areFriends(firstPlayer, secondPlayer);
java.util.List<BungeeFriendsAPI.PartyMemberInfo> party = api.getPartyMemberDetailList(firstPlayer);
```

这是同步 API；存储失败会传播异常，好友及组队变更还可能抛出相应受检异常。`getPartyMemberDetailList` 在未组队时返回空列表，`getOwner` 在未组队时返回 `null`。原有 `BungeeFriends.getAPI()` 工具入口继续存在。代理侧可监听 `net.simplyrin.bungeefriends.api.event.PartyWarpEvent`；事件表示传送已排程，不是成功连接回执。

## 游戏服

```java
import net.simplyrin.bungeefriends.api.spigot.SpigotFriendsAPI;

SpigotFriendsAPI api = SpigotFriendsAPI.getInstance();
api.getFriendDetailList(player).whenComplete((details, error) -> {
    if (error != null) {
        getLogger().warning("Friend query failed: " + error.getMessage());
        return;
    }
    // details is List<Map<String, Object>>; each map can construct FriendInfo.
});
```

`getInstance()` 需在 Spigot API 插件启用后调用。请求方法返回 `CompletableFuture`；拒绝、离线、超时和插件停用会异常完成。好友和队伍详情各包含 UUID、名称、显示名及在线状态，分别可用 `FriendInfo`、`PartyMemberInfo` 从详情 Map 构造。需要访问 Bukkit 对象时，回调先检查 `Bukkit.isPrimaryThread()` 并切回主线程。不要在主线程调用 `Future.join()` 或 `get()` 等待代理响应。

队长命令投递使用 `api.executeCommand(leader, targetNameOrUuid, commandWithoutSlash)`。它只确认代理已接受投递；代理和目标服均需允许命令标签，代理会重新校验队长、同队、在线和权限。后端的 `net.simplyrin.bungeefriends.api.spigot.event.PartyWarpEvent` 在 Bukkit 主线程触发。三个可调用示例及部署步骤见 [P6 示例记录](P6_EXAMPLES.md)。
