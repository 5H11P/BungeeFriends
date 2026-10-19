package net.simplyrin.bungeefriends.api.spigot.example;

import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;

import net.simplyrin.bungeefriends.api.spigot.SpigotFriendsAPI;

/** Follows configured leader commands through the public proxy API. */
public final class PartyCmdSenderSpigot extends JavaPlugin implements Listener {
	private SpigotFriendsAPI api;

	@Override
	public void onEnable() {
		saveDefaultConfig();
		api = SpigotFriendsAPI.getInstance();
		getServer().getPluginManager().registerEvents(this, this);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onCommand(PlayerCommandPreprocessEvent event) {
		String message = event.getMessage();
		if (message == null || !message.startsWith("/") || message.length() < 2) return;
		String command = message.substring(1);
		if (!CommandRules.allowed(command, getConfig().getStringList("commands"))) return;
		Player leader = event.getPlayer();
		api.getPartyMemberDetailList(leader).whenComplete((details, error) -> onMain(() -> {
			if (!isEnabled() || !leader.isOnline()) return;
			if (error != null) {
				getLogger().warning("Cannot query party for command forwarding: " + error.getMessage());
				return;
			}
			forward(leader, command, details);
		}));
	}

	private void forward(Player leader, String command, List<Map<String, Object>> details) {
		for (String target : ForwardingPlan.targets(leader.getUniqueId().toString(), details)) {
			try {
				api.executeCommand(leader, target, command).whenComplete((queued, error) -> {
					if (error != null) getLogger().warning("Command delivery to " + target
							+ " failed: " + error.getMessage());
					else if (!Boolean.TRUE.equals(queued))
						getLogger().warning("Command delivery to " + target + " was not queued");
				});
			} catch (RuntimeException error) {
				getLogger().warning("Cannot start command delivery to " + target + ": " + error.getMessage());
			}
		}
	}

	private void onMain(Runnable action) {
		if (Bukkit.isPrimaryThread()) action.run();
		else if (isEnabled()) {
			try { Bukkit.getScheduler().runTask(this, action); }
			catch (RuntimeException error) {
				if (isEnabled()) getLogger().warning("Cannot schedule party result: " + error.getMessage());
			}
		}
	}
}
