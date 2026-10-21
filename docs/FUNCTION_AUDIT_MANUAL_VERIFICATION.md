# 功能修复手动验收

以下步骤需要运行 BungeeCord 代理及至少两个后端服。完成每项后记录结果和代理日志。

## 私聊回复

1. 玩家 A 对在线玩家 B 执行 `/msg B hello`，A 和 B 分别执行 `/r reply`，确认双方都能回复到对方。
2. A 与 B 建立私聊后让 B 断开连接。A 执行 `/r still here`，确认提示目标离线且没有异常；B 重连后，A 再次执行 `/r welcome back`，确认消息送达。
3. 重启代理后，未建立新私聊的玩家执行 `/r test`，确认没有沿用上次运行中的回复目标。
4. 用两个玩家并发发送 `/msg` 并快速 `/r`，确认代理日志没有并发异常。

## 好友请求和玩家解析

1. A 向离线的 B 发送好友请求；B 登录后确认收到请求提示，`/friend requests` 中也能看到该请求。
2. 分别用玩家名、在线玩家名的不同大小写、UUID 执行好友添加/查询相关命令，确认解析到同一个玩家。
3. 让请求超过有效期后再登录，确认过期请求不会被提示。

## 状态和组队

1. 将游戏服名称加入 `Plugin.PartyWarp.Game-Servers`。玩家将状态设为 Away 或 AppearOffline 并切换到该服，确认状态恢复为 Online。
2. 将 `Enable-BungeeParties` 设为 true，执行 `/friendconsole reload config`，确认 `/party`、`/chat`、`/pchat` 可用；再设为 false 重载，确认这些命令及组队聊天监听不再生效。
3. 配置 `Disable-PartyCmdSender: true` 并重载，确认队长命令不会转发给队员；设为 false 后分别执行 `/join arena` 和 `/joiner arena`，确认只转发完全匹配的 `/join` 命令。
4. 从代理控制台输入匹配的队长命令，确认不会触发类型转换异常。

## 忽略列表和本地语言

1. 在 YAML 数据模式下添加、移除和清空忽略列表，然后重启代理，确认更改已保存。
2. 尝试屏蔽自己，确认命令拒绝该操作。
3. 执行 `/friend lang english`、`/friend lang chinese` 和 `/friendconsole lang <本地语言>`，确认本地切换正常；输入 `lang update` 时只显示本地语言用法，不发起网络请求或生成远程语言文件。

## 消息入口

向消息发送入口传入空目标/空消息的边界操作，并检查代理日志，确认不再出现由判空顺序导致的 `NullPointerException`。
