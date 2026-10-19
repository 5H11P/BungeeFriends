package net.simplyrin.bungeefriends.api.spigot.example;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import net.simplyrin.bungeefriends.api.spigot.SpigotFriendsAPI;
import net.simplyrin.bungeefriends.api.spigot.SpigotFriendsAPI.FriendInfo;
import net.simplyrin.bungeefriends.api.spigot.SpigotFriendsAPI.PartyMemberInfo;

/** Demonstrates the public asynchronous read API. */
public final class ExamplePlugin extends JavaPlugin {
	private SpigotFriendsAPI api;

	@Override
	public void onEnable() {
		api = SpigotFriendsAPI.getInstance();
		getCommand("example").setExecutor(this);
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!(sender instanceof Player)) {
			sender.sendMessage(ChatColor.RED + "A player must run this command.");
			return true;
		}
		Player player = (Player) sender;
		if (args.length == 0) {
			player.sendMessage(ChatColor.YELLOW + "/example friends|party|check <player>|isleader");
			return true;
		}
		switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
		case "friends":
			show(player, api.getFriendDetailList(player), details -> {
				player.sendMessage(ChatColor.GOLD + "Friends: " + details.size());
				for (Map<String, Object> data : details) {
					FriendInfo friend = new FriendInfo(data);
					player.sendMessage(ChatColor.GRAY + "- " + friend.getDisplayName() + " ("
							+ (friend.isOnline() ? "online" : "offline") + ")");
				}
			});
			break;
		case "party":
			show(player, api.getPartyMemberDetailList(player), details -> {
				player.sendMessage(ChatColor.GOLD + "Party members: " + details.size());
				for (Map<String, Object> data : details) {
					PartyMemberInfo member = new PartyMemberInfo(data);
					player.sendMessage(ChatColor.GRAY + "- " + member.getDisplayName()
							+ (member.isLeader() ? " [leader]" : "")
							+ (member.isOnline() ? " [online]" : " [offline]"));
				}
			});
			break;
		case "check":
			if (args.length != 2) {
				player.sendMessage(ChatColor.RED + "Usage: /example check <player>");
				return true;
			}
			show(player, api.areFriends(player, args[1]), friends -> player.sendMessage(
					ChatColor.YELLOW + args[1] + (friends ? " is your friend." : " is not your friend.")));
			break;
		case "isleader":
			show(player, api.isPartyLeader(player), leader -> player.sendMessage(
					ChatColor.YELLOW + (leader ? "You are the party leader." : "You are not the party leader.")));
			break;
		default:
			player.sendMessage(ChatColor.RED + "Unknown subcommand.");
		}
		return true;
	}

	private <T> void show(Player player, CompletableFuture<T> future, Consumer<T> success) {
		future.whenComplete((value, error) -> onMain(() -> {
			if (!isEnabled() || !player.isOnline()) return;
			if (error != null) player.sendMessage(ChatColor.RED + "API request failed: " + error.getMessage());
			else success.accept(value);
		}));
	}

	private void onMain(Runnable action) {
		if (Bukkit.isPrimaryThread()) action.run();
		else if (isEnabled()) {
			try { Bukkit.getScheduler().runTask(this, action); }
			catch (RuntimeException error) {
				if (isEnabled()) getLogger().warning("Cannot schedule API result: " + error.getMessage());
			}
		}
	}
}
