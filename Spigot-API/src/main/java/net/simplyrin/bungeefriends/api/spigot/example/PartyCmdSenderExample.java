package net.simplyrin.bungeefriends.api.spigot.example;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import net.simplyrin.bungeefriends.api.spigot.BungeeFriendsAPISpigot;
import net.simplyrin.bungeefriends.api.spigot.SpigotFriendsAPI;

/**
 * PartyCmdSender功能示例插件
 * 展示如何使用API让队长执行命令时队员跟随执行
 */
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
        
        getLogger().info("PartyCmdSender示例插件已启用！");
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
                String commandStr = cmdBuilder.toString();
                
                // 执行命令
                player.chat("/" + commandStr);
                player.sendMessage(ChatColor.GREEN + "命令已执行，队员将跟随执行相同命令！");
                
                // 注意：实际的命令执行和队员跟随执行由PartyCmdSenderListener处理
                // 这里只是演示如何通过插件让玩家执行命令
            } else {
                player.sendMessage(ChatColor.RED + "只有队长才能使用此命令！");
            }
        }).exceptionally(ex -> {
            player.sendMessage(ChatColor.RED + "检查队长状态时出错: " + ex.getMessage());
            return null;
        });

        return true;
    }
}