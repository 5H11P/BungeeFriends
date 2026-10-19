package net.simplyrin.bungeefriends.api.spigot.example;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import net.simplyrin.bungeefriends.api.spigot.SpigotFriendsAPI;

/** Explicit command delivery example; the proxy remains authoritative. */
public final class PartyCmdSenderExample extends JavaPlugin {
	private SpigotFriendsAPI api;

	@Override
	public void onEnable() {
		api = SpigotFriendsAPI.getInstance();
		getCommand("partycmd").setExecutor(this);
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!(sender instanceof Player)) {
			sender.sendMessage(ChatColor.RED + "A player must run this command.");
			return true;
		}
		Player leader = (Player) sender;
		if (args.length < 2) {
			leader.sendMessage(ChatColor.RED + "Usage: /partycmd <member> <command> [args...]");
			return true;
		}
		String target = args[0];
		String commandLine = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
		api.isPartyLeader(leader).thenCompose(isLeader -> {
			if (!isLeader) {
				CompletableFuture<Boolean> denied = new CompletableFuture<>();
				denied.completeExceptionally(new IllegalStateException("Only the party leader may send commands"));
				return denied;
			}
			return api.executeCommand(leader, target, commandLine);
		}).whenComplete((queued, error) -> onMain(() -> {
			if (!isEnabled() || !leader.isOnline()) return;
			if (error != null) leader.sendMessage(ChatColor.RED + "Command request failed: " + error.getMessage());
			else leader.sendMessage(queued ? ChatColor.GREEN + "Command queued for " + target + "."
					: ChatColor.RED + "Command was not queued.");
		}));
		return true;
	}

	private void onMain(Runnable action) {
		if (Bukkit.isPrimaryThread()) action.run();
		else if (isEnabled()) {
			try { Bukkit.getScheduler().runTask(this, action); }
			catch (RuntimeException error) {
				if (isEnabled()) getLogger().warning("Cannot schedule command result: " + error.getMessage());
			}
		}
	}
}
