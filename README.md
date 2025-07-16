# BungeeFriends API

这是一个用于BungeeFriends插件的API实现，允许Spigot服务器与BungeeCord服务器通信，以获取好友和组队信息。

## 项目结构

- **Bungee-API**: BungeeCord端的API插件，负责处理来自Spigot的请求并返回数据
- **Spigot-API**: Spigot端的API插件，提供API接口供其他Spigot插件使用

## 安装说明

1. 编译两个API插件：
   ```
   cd Bungee-API
   mvn clean package
   cd ../Spigot-API
   mvn clean package
   ```

2. 将生成的jar文件放入相应的服务器插件目录：
   - `Bungee-API/target/BungeeFriendsAPI-Bungee-1.0.0.jar` → BungeeCord的plugins目录
   - `Spigot-API/target/BungeeFriendsAPI-Spigot-1.0.0.jar` → Spigot的plugins目录

3. 确保BungeeCord服务器上已安装BungeeFriends插件

4. 重启BungeeCord和Spigot服务器

## 使用方法

### 在Spigot插件中使用API

1. 在你的Spigot插件的`plugin.yml`中添加依赖：
   ```yaml
   depend: [BungeeFriendsAPI-Spigot]
   ```

2. 在你的代码中获取API实例：
   ```java
   import net.simplyrin.bungeefriends.api.spigot.BungeeFriendsAPISpigot;
   
   // 获取API实例
   BungeeFriendsAPISpigot api = BungeeFriendsAPISpigot.getInstance();
   ```

3. 使用API方法：
   ```java
   // 获取玩家的好友列表
   api.getFriendDetailList(player).thenAccept(result -> {
       if (result instanceof List) {
           @SuppressWarnings("unchecked")
           List<Map<String, Object>> friendDetails = (List<Map<String, Object>>) result;
           
           // 处理好友列表...
           for (Map<String, Object> friend : friendDetails) {
               String name = (String) friend.get("name");
               boolean isOnline = (boolean) friend.get("online");
               // 使用好友信息...
           }
       }
   });
   ```

### 可用的API方法

#### 好友相关

- `getFriendList(Player player)`: 获取玩家的好友UUID列表
- `getFriendDetailList(Player player)`: 获取玩家的好友详细信息列表
- `getFriendDetailListByName(String playerName)`: 通过玩家名称获取好友详细信息列表
- `areFriends(Player player, String targetName)`: 检查两个玩家是否是好友

#### 组队相关

- `getPartyMembers(Player player)`: 获取玩家的组队成员UUID列表
- `getPartyMemberDetailList(Player player)`: 获取玩家的组队成员详细信息列表
- `getPartyMemberDetailListByName(String playerName)`: 通过玩家名称获取组队成员详细信息列表
- `isInParty(Player player)`: 检查玩家是否在组队中
- `isPartyLeader(Player player)`: 检查玩家是否是组队队长


#### 自定义请求

- `sendCustomRequest(Player player, String requestType, String... args)`: 发送自定义请求到BungeeCord

### PartyCmdSender功能

这个功能允许队长执行命令时，队员自动跟随执行相同的命令，包括通过Spigot插件执行的命令。

#### 配置方法

1. 编辑`plugins/BungeeFriendsAPI-Spigot/config.yml`文件：

```yaml
# BungeeFriendsAPI-Spigot 配置文件

# PartyCmdSender 配置
partycmdsender:
  # 需要监听的命令列表
  # 支持以下格式：
  # - "完整命令" - 精确匹配整个命令
  # - "命令前缀 $args" - 匹配命令前缀，后面的参数会被传递
  # - "命令" - 匹配命令名称，忽略参数
  commands:
    - "join"
    - "play"
    - "join $args"
    - "play $args"
    - "game"
    - "game $args"
  
  # 调试模式
  debug: false
  
  # 命令执行延迟（单位：tick，20tick = 1秒）
  command-delay: 10
```

2. 根据需要修改命令列表，添加你想要队员跟随执行的命令

3. 重启服务器或重载插件

#### 使用方法

1. 创建一个队伍，并成为队长

2. 当队长执行配置中的命令时（无论是直接在聊天框输入还是通过Spigot插件执行），队员会自动执行相同的命令

3. 如果队员在同一个服务器，会直接执行命令；如果在不同服务器，会通过BungeeCord转发命令

#### 示例插件

项目中包含了一个PartyCmdSender功能的示例插件：

```java
public class PartyCmdSenderExample extends JavaPlugin {

    private BungeeFriendsAPISpigot api;
    private SpigotFriendsAPI friendsAPI;

    @Override
    public void onEnable() {
        // 获取API实例
        this.api = BungeeFriendsAPISpigot.getInstance();
        this.friendsAPI = SpigotFriendsAPI.getInstance();
        
        // 注册命令
        getCommand("partycmd").setExecutor(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "此命令只能由玩家执行！");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            player.sendMessage(ChatColor.RED + "用法: /partycmd <命令> [参数...]");
            return true;
        }

        // 检查玩家是否是队长
        friendsAPI.isPartyLeader(player).thenAccept(isLeader -> {
            if (isLeader) {
                // 构建要执行的命令
                StringBuilder cmdBuilder = new StringBuilder(args[0]);
                for (int i = 1; i < args.length; i++) {
                    cmdBuilder.append(" ").append(args[i]);
                }
                String command = cmdBuilder.toString();
                
                // 执行命令
                player.chat("/" + command);
                player.sendMessage(ChatColor.GREEN + "命令已执行，队员将跟随执行相同命令！");
                
                // 注意：实际的命令执行和队员跟随执行由PartyCmdSenderListener处理
            } else {
                player.sendMessage(ChatColor.RED + "只有队长才能使用此命令！");
            }
        });

        return true;
    }
}
```

使用方法：
1. 队长输入 `/partycmd join bedwars` 命令
2. 队长会执行 `/join bedwars` 命令
3. 队员会自动跟随执行 `/join bedwars` 命令

### API使用示例

项目中包含了一个示例插件，展示如何使用API：

```java
public class ExamplePlugin extends JavaPlugin {

    private BungeeFriendsAPISpigot api;

    @Override
    public void onEnable() {
        // 获取API实例
        this.api = BungeeFriendsAPISpigot.getInstance();
    }

    // 使用API的示例方法
    private void showFriends(Player player) {
        api.getFriendDetailList(player).thenAccept(result -> {
            if (result instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> friendDetails = (List<Map<String, Object>>) result;
                
                player.sendMessage(ChatColor.GOLD + "=== 你的好友列表 ===");
                for (Map<String, Object> friend : friendDetails) {
                    boolean isOnline = (boolean) friend.get("online");
                    String status = isOnline ? ChatColor.GREEN + "在线" : ChatColor.RED + "离线";
                    player.sendMessage(ChatColor.GRAY + "- " + friend.get("displayName") + " " 
                            + ChatColor.GRAY + "[" + status + ChatColor.GRAY + "]");
                }
            }
        });
    }
}
```

## 命令

### Spigot端命令

- `/bfspigot help` - 显示帮助信息
- `/bfspigot friends` - 显示你的好友列表
- `/bfspigot frienddetails` - 显示你的好友详细信息列表
- `/bfspigot frienddetailsbyname <玩家名>` - 显示指定玩家的好友详细信息列表
- `/bfspigot party` - 显示你的组队信息
- `/bfspigot partydetails` - 显示你的组队成员详细信息列表
- `/bfspigot partydetailsbyname <玩家名>` - 显示指定玩家的组队成员详细信息列表
- `/bfspigot check <玩家名>` - 检查与指定玩家的好友关系
- `/bfspigot inparty` - 检查你是否在组队中
- `/bfspigot isleader` - 检查你是否是组队队长
- `/bfspigot info` - 显示API信息

## 通信协议

本API使用JSON格式进行Spigot和BungeeCord之间的通信，通过BungeeCord的插件消息通道`BungeeFriends`传输数据。

### 请求格式

```json
{
  "requestType": "getFriendList",
  "requestId": "玩家UUID:请求类型",
  "args": ["可选参数1", "可选参数2"]
}
```

### 响应格式

```json
{
  "requestId": "玩家UUID:请求类型",
  "success": true,
  "data": ["数据内容，可以是数组、对象、布尔值、数字或字符串"]
}
```

或者错误响应：

```json
{
  "requestId": "玩家UUID:请求类型",
  "success": false,
  "error": "错误信息"
}
```

## 注意事项

1. 确保BungeeCord的`config.yml`中启用了插件消息通道：
   ```yaml
   settings:
     bungeecord: true
   ```

2. 确保Spigot的`spigot.yml`中启用了插件消息通道：
   ```yaml
   settings:
     bungeecord: true
   ```

3. API方法返回的是`CompletableFuture`对象，需要使用异步处理方式获取结果

4. 响应数据可能是不同类型（List、Map、Boolean等），使用时需要进行类型检查和转换

## 许可证

本项目使用与BungeeFriends相同的许可证
# 开源许可协议
**・[bStats-Metrics | GNU Lesser General Public License v3.0](https://github.com/Bastian/bStats-Metrics/blob/master/LICENSE)**

**・[LuckPerms | MIT License](https://raw.githubusercontent.com/lucko/LuckPerms/master/LICENSE.txt)**
