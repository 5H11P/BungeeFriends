package net.simplyrin.bungeefriends.api.spigot.example;

import net.simplyrin.bungeefriends.api.spigot.SpigotFriendsAPI;
import net.simplyrin.bungeefriends.api.spigot.SpigotFriendsAPI.FriendInfo;
import net.simplyrin.bungeefriends.api.spigot.SpigotFriendsAPI.PartyMemberInfo;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 示例插件，展示如何使用BungeeFriends API
 */
public class ExamplePlugin extends JavaPlugin {

    private SpigotFriendsAPI api;

    @Override
    public void onEnable() {
        // 获取API实例
        this.api = SpigotFriendsAPI.getInstance();
        getLogger().info("示例插件已启用，成功获取BungeeFriends API！");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c此命令只能由玩家执行！");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "friends":
                // 获取好友列表并转换为FriendInfo对象
                api.getFriendDetailList(player).thenAccept(friendDetails -> {
                    List<FriendInfo> friends = friendDetails.stream()
                            .map(FriendInfo::new)
                            .collect(Collectors.toList());
                    
                    player.sendMessage("§6=== 你的好友列表 (使用API) ===");
                    if (friends.isEmpty()) {
                        player.sendMessage("§7你还没有好友。");
                        return;
                    }
                    
                    for (FriendInfo friend : friends) {
                        String status = friend.isOnline() ? "§a在线" : "§c离线";
                        player.sendMessage("§7- " + friend.getDisplayName() + " §7[" + status + "§7]");
                        player.sendMessage("  §8UUID: §7" + friend.getUuid());
                        player.sendMessage("  §8名称: §7" + friend.getName());
                        player.sendMessage("  §8前缀: §7" + friend.getPrefix());
                    }
                }).exceptionally(ex -> {
                    player.sendMessage("§c获取好友列表时出错: " + ex.getMessage());
                    return null;
                });
                break;
                
            case "party":
                // 获取组队成员列表并转换为PartyMemberInfo对象
                api.getPartyMemberDetailList(player).thenAccept(partyDetails -> {
                    List<PartyMemberInfo> members = partyDetails.stream()
                            .map(PartyMemberInfo::new)
                            .collect(Collectors.toList());
                    
                    player.sendMessage("§6=== 你的组队成员列表 (使用API) ===");
                    if (members.isEmpty()) {
                        player.sendMessage("§c你当前不在任何组队中。");
                        return;
                    }
                    
                    player.sendMessage("§e成员数量: §7" + members.size());
                    for (PartyMemberInfo member : members) {
                        String status = member.isOnline() ? "§a在线" : "§c离线";
                        String leaderTag = member.isLeader() ? " §6[队长]" : "";
                        boolean isYou = member.getName().equalsIgnoreCase(player.getName());
                        String youTag = isYou ? " §a(你)" : "";
                        
                        player.sendMessage("§7- " + member.getDisplayName() + youTag + leaderTag + " §7[" + status + "§7]");
                        player.sendMessage("  §8UUID: §7" + member.getUuid());
                        player.sendMessage("  §8名称: §7" + member.getName());
                    }
                }).exceptionally(ex -> {
                    player.sendMessage("§c获取组队成员列表时出错: " + ex.getMessage());
                    return null;
                });
                break;
                
            case "check":
                if (args.length < 2) {
                    player.sendMessage("§c用法: /example check <玩家名称>");
                    return true;
                }
                // 检查是否是好友
                api.areFriends(player, args[1]).thenAccept(areFriends -> {
                    player.sendMessage("§6=== 好友关系检查 (使用API) ===");
                    player.sendMessage("§e玩家: §7" + args[1]);
                    player.sendMessage("§e是否是好友: §7" + (areFriends ? "§a是" : "§c否"));
                }).exceptionally(ex -> {
                    player.sendMessage("§c检查好友关系时出错: " + ex.getMessage());
                    return null;
                });
                break;
                
            case "isleader":
                // 检查是否是组队队长
                api.isPartyLeader(player).thenAccept(isLeader -> {
                    player.sendMessage("§6=== 组队队长检查 (使用API) ===");
                    player.sendMessage("§e是否是队长: §7" + (isLeader ? "§a是" : "§c否"));
                }).exceptionally(ex -> {
                    player.sendMessage("§c检查组队队长状态时出错: " + ex.getMessage());
                    return null;
                });
                break;
                
            default:
                sendHelp(player);
                break;
        }

        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("§9----------------------------------------------------");
        player.sendMessage("§e/example friends §7- 显示你的好友列表");
        player.sendMessage("§e/example party §7- 显示你的组队成员列表");
        player.sendMessage("§e/example check <玩家名> §7- 检查与指定玩家的好友关系");
        player.sendMessage("§e/example isleader §7- 检查你是否是组队队长");
        player.sendMessage("§9----------------------------------------------------");
    }
}