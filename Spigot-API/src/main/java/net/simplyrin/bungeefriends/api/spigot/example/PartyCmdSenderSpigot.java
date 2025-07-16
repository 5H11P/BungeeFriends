package net.simplyrin.bungeefriends.api.spigot.example;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;

import net.simplyrin.bungeefriends.api.spigot.SpigotFriendsAPI;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 示例插件：监听玩家命令并通过BungeeAPI与BungeeFriends通信
 * 实现队员跟随执行命令的功能
 */
public class PartyCmdSenderSpigot extends JavaPlugin implements Listener {

    private SpigotFriendsAPI api;
    
    @Override
    public void onEnable() {
        // 获取API实例
        this.api = SpigotFriendsAPI.getInstance();
        
        // 注册事件监听器
        getServer().getPluginManager().registerEvents(this, this);
        
        // 注册命令
        getCommand("partycmdsender").setExecutor(this);
        
        // 保存默认配置
        saveDefaultConfig();
        
        getLogger().info("PartyCmdSender Spigot插件已启用！");
    }
    
    @Override
    public void onDisable() {
        getLogger().info("PartyCmdSender Spigot插件已禁用！");
    }
    
    /**
     * 监听玩家命令
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String command = event.getMessage().substring(1); // 去除斜杠
        
        // 检查玩家是否在队伍中并且是队长
        checkIfPartyLeader(player).thenAccept(isLeader -> {
            if (isLeader) {
                // 检查命令是否匹配配置中的命令
                if (isCommandInConfig(command)) {
                    // 获取队伍成员并发送命令
                    sendCommandToPartyMembers(player, command);
                }
            }
        }).exceptionally(ex -> {
            getLogger().warning("检查玩家队伍状态时出错: " + ex.getMessage());
            return null;
        });
    }
    
    /**
     * 检查玩家是否是队伍队长
     */
    private CompletableFuture<Boolean> checkIfPartyLeader(Player player) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        
        // 首先检查玩家是否在队伍中
        api.isInParty(player).thenAccept(inParty -> {
            if (inParty) {
                // 然后检查是否是队长
                api.isPartyLeader(player).thenAccept(result::complete)
                    .exceptionally(ex -> {
                        result.complete(false);
                        return null;
                    });
            } else {
                result.complete(false);
            }
        }).exceptionally(ex -> {
            result.complete(false);
            return null;
        });
        
        return result;
    }
    
    /**
     * 检查命令是否在配置中
     */
    private boolean isCommandInConfig(String command) {
        List<String> configCommands = getConfig().getStringList("commands");
        
        // 检查完整命令匹配
        if (configCommands.contains(command)) {
            return true;
        }
        
        // 检查命令前缀匹配
        String cmdBase = command.split(" ")[0];
        for (String configCmd : configCommands) {
            if (configCmd.endsWith(" $args") && command.startsWith(configCmd.substring(0, configCmd.length() - 6))) {
                return true;
            } else if (command.startsWith(configCmd + " ")) {
                return true;
            } else if (configCmd.equals(cmdBase)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 发送命令给队伍成员
     */
    private void sendCommandToPartyMembers(Player leader, String command) {
        // 获取队伍成员详细信息
        api.getPartyMemberDetailList(leader).thenAccept(result -> {
            if (result instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> members = (List<Map<String, Object>>) result;
                
                for (Map<String, Object> member : members) {
                    // 跳过队长自己
                    if (member.get("uuid").toString().equals(leader.getUniqueId().toString())) {
                        continue;
                    }
                    
                    // 检查成员是否在线且在同一服务器
                    boolean isOnline = (boolean) member.get("online");
                    if (isOnline) {
                        String memberName = (String) member.get("name");
                        Player memberPlayer = Bukkit.getPlayer(memberName);
                        
                        // 如果成员在当前服务器，直接执行命令
                        if (memberPlayer != null && memberPlayer.isOnline()) {
                            // 使用延迟执行，模拟BungeeCord的行为
                            Bukkit.getScheduler().runTaskLater(this, () -> {
                                memberPlayer.chat("/" + command);
                            }, 10L); // 0.5秒延迟
                        } else {
                            // 成员不在当前服务器，通过BungeeAPI发送命令请求
                            sendCommandRequestToBungee(leader, memberName, command);
                        }
                    }
                }
            }
        }).exceptionally(ex -> {
            getLogger().warning("获取队伍成员列表时出错: " + ex.getMessage());
            return null;
        });
    }
    
    /**
     * 通过BungeeAPI发送命令请求
     */
    private void sendCommandRequestToBungee(Player sender, String targetPlayer, String command) {
        // 这里需要实现通过BungeeAPI发送命令请求的逻辑
        // 由于当前API中没有直接的方法，可以考虑扩展API或使用插件消息通道
        
        // 示例：使用自定义请求类型
        api.sendCustomRequest(sender, "executeCommand", targetPlayer, command)
            .thenAccept(response -> {
                getLogger().info("命令请求已发送给 " + targetPlayer);
            })
            .exceptionally(ex -> {
                getLogger().warning("发送命令请求时出错: " + ex.getMessage());
                return null;
            });
    }
}