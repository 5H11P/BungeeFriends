package net.simplyrin.bungeefriends.api.spigot;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 监听玩家命令并通过BungeeAPI与BungeeFriends通信
 * 实现队员跟随执行命令的功能
 */
public class PartyCmdSenderListener implements Listener {

    private final BungeeFriendsAPISpigot plugin;
    private final SpigotFriendsAPI api;
    private final FileConfiguration config;
    
    public PartyCmdSenderListener(BungeeFriendsAPISpigot plugin) {
        this.plugin = plugin;
        this.api = SpigotFriendsAPI.getInstance();
        this.config = plugin.getConfig();
        
        // 保存默认配置
        plugin.saveDefaultConfig();
        
        // 添加默认命令配置
        if (!config.contains("partycmdsender.commands")) {
            List<String> defaultCommands = List.of(
                "join", 
                "play", 
                "join $args", 
                "play $args", 
                "game", 
                "game $args"
            );
            config.set("partycmdsender.commands", defaultCommands);
            config.set("partycmdsender.debug", false);
            config.set("partycmdsender.command-delay", 10);
            plugin.saveConfig();
        }
        
        plugin.getLogger().info("PartyCmdSender监听器已启用！");
    }
    
    /**
     * 监听玩家命令
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String command = event.getMessage().substring(1); // 去除斜杠
        
        // 立即保存玩家UUID和命令信息，以防玩家离开服务器
        final String playerName = player.getName();
        final String playerUUID = player.getUniqueId().toString();
        
        // 检查命令是否匹配配置中的命令，先进行这个检查，因为它不需要异步操作
        if (!isCommandInConfig(command)) {
            return;
        }
        
        // 立即创建一个任务，不依赖于玩家对象，以防玩家离线
        // 这个任务将在短暂延迟后执行，确保即使玩家立即离线也能处理命令
        int immediateDelay = 1; // 1 tick = 0.05秒
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (config.getBoolean("partycmdsender.debug")) {
                plugin.getLogger().info("检测到玩家 " + playerName + " 执行命令: " + command + "，正在检查是否为队长");
            }
            
            // 检查玩家是否在队伍中并且是队长
            checkIfPartyLeader(player).thenAccept(isLeader -> {
                if (isLeader) {
                    if (config.getBoolean("partycmdsender.debug")) {
                        plugin.getLogger().info("确认 " + playerName + " 是队长，正在处理命令: " + command);
                    }
                    
                    // 获取队伍成员并发送命令
                    sendCommandToPartyMembers(player, command);
                    
                    // 添加延迟任务，确保即使队长传送到其他服务器，命令也能发送给队员
                    int delay = config.getInt("partycmdsender.command-delay", 10);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        // 再次尝试发送命令给队伍成员，以防队长已经传送或离线
                        // 使用UUID而不是Player对象，因为Player对象可能已经无效
                        sendCommandToPartyMembersByUUID(playerUUID, command);
                    }, delay);
                }
            }).exceptionally(ex -> {
                plugin.getLogger().warning("检查玩家队伍状态时出错: " + ex.getMessage());
                return null;
            });
        }, immediateDelay);
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
        List<String> configCommands = config.getStringList("partycmdsender.commands");
        
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
        api.getPartyMemberDetailList(leader).thenAccept(members -> {
            if (members != null) {
                String leaderUUID = leader.getUniqueId().toString();
                
                for (Map<String, Object> member : members) {
                    // 跳过队长自己
                    if (member.get("uuid").toString().equals(leaderUUID)) {
                        continue;
                    }
                    
                    // 检查成员是否在线
                    boolean isOnline = (boolean) member.get("online");
                    if (isOnline) {
                        String memberName = (String) member.get("name");
                        Player memberPlayer = Bukkit.getPlayer(memberName);
                        
                        // 如果成员在当前服务器，直接执行命令
                        if (memberPlayer != null && memberPlayer.isOnline()) {
                            int delay = config.getInt("partycmdsender.command-delay", 10);
                            
                            // 使用延迟执行，模拟BungeeCord的行为
                            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                if (config.getBoolean("partycmdsender.debug")) {
                                    plugin.getLogger().info("让队员 " + memberPlayer.getName() + " 执行命令: " + command);
                                }
                                memberPlayer.chat("/" + command);
                            }, delay); // 默认0.5秒延迟
                        } else {
                            // 成员不在当前服务器，通过BungeeAPI发送命令请求
                            sendCommandRequestToBungee(leader, memberName, command);
                        }
                    }
                }
            }
        }).exceptionally(ex -> {
            plugin.getLogger().warning("获取队伍成员列表时出错: " + ex.getMessage());
            return null;
        });
    }
    
    /**
     * 通过BungeeAPI发送命令请求
     */
    private void sendCommandRequestToBungee(Player sender, String targetPlayer, String command) {
        // 使用sendCustomRequest方法发送自定义请求
        try {
            plugin.sendCustomRequest(sender, "executeCommand", targetPlayer, command)
                .thenAccept(response -> {
                    if (config.getBoolean("partycmdsender.debug")) {
                        plugin.getLogger().info("命令请求已发送给 " + targetPlayer + ": " + command);
                    }
                })
                .exceptionally(ex -> {
                    // 如果发送失败，记录错误但不中断流程
                    plugin.getLogger().warning("发送命令请求时出错: " + ex.getMessage() + "，但将继续尝试执行命令");
                    return null;
                });
        } catch (Exception e) {
            // 捕获任何可能的异常，记录错误但不中断流程
            plugin.getLogger().warning("发送命令请求时发生异常: " + e.getMessage() + "，但将继续尝试执行命令");
        }
    }
    
    /**
     * 通过UUID发送命令给队伍成员
     */
    private void sendCommandToPartyMembersByUUID(String leaderUUID, String command) {
        // 获取队伍成员详细信息（通过UUID）
        api.getPartyMemberDetailListByUUID(leaderUUID).thenAccept(members -> {
            if (members != null) {
                for (Map<String, Object> member : members) {
                    // 跳过队长自己
                    if (member.get("uuid").toString().equals(leaderUUID)) {
                        continue;
                    }
                    
                    // 检查成员是否在线
                    boolean isOnline = (boolean) member.get("online");
                    if (isOnline) {
                        String memberName = (String) member.get("name");
                        Player memberPlayer = Bukkit.getPlayer(memberName);
                        
                        // 如果成员在当前服务器，直接执行命令
                        if (memberPlayer != null && memberPlayer.isOnline()) {
                            int delay = config.getInt("partycmdsender.command-delay", 10);
                            
                            // 使用延迟执行，模拟BungeeCord的行为
                            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                if (config.getBoolean("partycmdsender.debug")) {
                                    plugin.getLogger().info("让队员 " + memberPlayer.getName() + " 执行命令: " + command);
                                }
                                memberPlayer.chat("/" + command);
                            }, delay); // 默认0.5秒延迟
                        } else {
                            // 成员不在当前服务器，通过BungeeAPI发送命令请求
                            // 使用一个临时的Player对象或直接使用UUID发送请求
                            sendCommandRequestToBungeeByName(memberName, command);
                        }
                    }
                }
            }
        }).exceptionally(ex -> {
            plugin.getLogger().warning("获取队伍成员列表时出错: " + ex.getMessage());
            return null;
        });
    }
    
    /**
     * 通过玩家名称向Bungee发送命令请求
     * 当队长已经离线时使用
     */
    private void sendCommandRequestToBungeeByName(String targetName, String command) {
        try {
            plugin.sendCustomRequestByName("executeCommand", targetName, command).exceptionally(ex -> {
                plugin.getLogger().warning("向Bungee发送命令请求时出错: " + ex.getMessage());
                return null;
            });
        } catch (Exception e) {
            plugin.getLogger().warning("向Bungee发送命令请求时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
}