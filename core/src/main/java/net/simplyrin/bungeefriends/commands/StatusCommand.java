package net.simplyrin.bungeefriends.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.TabExecutor;
import net.md_5.bungee.api.scheduler.ScheduledTask;
import net.simplyrin.bungeefriends.Main;
import net.simplyrin.bungeefriends.messages.MessageFrame;
import net.simplyrin.bungeefriends.messages.Messages;
import net.simplyrin.bungeefriends.tools.ThreadPool;
import net.simplyrin.bungeefriends.utils.LanguageManager.LanguageUtils;
import net.simplyrin.bungeefriends.utils.StatusManager.Status;

public class StatusCommand extends Command implements TabExecutor {
	private final Main plugin;
	private final ConcurrentHashMap<UUID, ScheduledTask> reminders = new ConcurrentHashMap<>();

	public StatusCommand(Main plugin) {
		super("status");
		this.plugin = plugin;
	}

	@Override
	public void execute(CommandSender sender, String[] args) {
		if (!(sender instanceof ProxiedPlayer)) {
			sender.sendMessage(TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', Messages.INGAME_ONLY)));
			return;
		}
		ProxiedPlayer player = (ProxiedPlayer) sender;
		ThreadPool.run(() -> changeStatus(player, args));
	}

	private void changeStatus(ProxiedPlayer player, String[] args) {
		if (plugin.getProxy().getPlayer(player.getUniqueId()) == null) return;
		LanguageUtils language = plugin.getLanguageManager().getPlayer(player);
		if (!plugin.getBoolean("Plugin.Enable-Status")) {
			plugin.infoFramed(player, language.getString("Status-Command.Disabled"), MessageFrame.MessageId.STATUS_DISABLED);
			return;
		}
		Status status = Status.fromArgs(args);
		if (status == null) {
			plugin.infoFramed(player, language.getString("Status-Command.Usage"), MessageFrame.MessageId.STATUS_USAGE);
			return;
		}
		plugin.getStatusManager().setStatus(player.getUniqueId(), status);
		String displayName = plugin.getFriendManager().getPlayer(player).getDisplayName();
		plugin.info(player, language.getString("Status.ChatBar." + status.getKey()).replace("%targetDisplayName", displayName));
		sendActionBar(player, language, status, displayName);
		cancel(player.getUniqueId());
		if (status == Status.ONLINE) return;
		UUID uuid = player.getUniqueId();
		ScheduledTask task = plugin.getProxy().getScheduler().schedule(plugin, () -> {
			ProxiedPlayer current = plugin.getProxy().getPlayer(uuid);
			if (current == null || !plugin.getBoolean("Plugin.Enable-Status")
					|| plugin.getStatusManager().getStatus(uuid) != status) {
				cancel(uuid);
				return;
			}
			LanguageUtils currentLanguage = plugin.getLanguageManager().getPlayer(current);
			String currentName = plugin.getFriendManager().getPlayer(current).getDisplayName();
			sendActionBar(current, currentLanguage, status, currentName);
		}, 2, 2, TimeUnit.SECONDS);
		reminders.put(uuid, task);
	}

	private void sendActionBar(ProxiedPlayer player, LanguageUtils language, Status status, String displayName) {
		String message = language.getString("Status.ActionBar." + status.getKey());
		if (message == null || message.isEmpty() || "false".equalsIgnoreCase(message)) return;
		player.sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(
				ChatColor.translateAlternateColorCodes('&', message.replace("%targetDisplayName", displayName))));
	}

	public void cancel(UUID uuid) {
		ScheduledTask task = reminders.remove(uuid);
		if (task != null) task.cancel();
	}

	public void close() {
		for (UUID uuid : reminders.keySet()) cancel(uuid);
	}

	@Override
	public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
		List<String> matches = new ArrayList<>();
		if (!plugin.getBoolean("Plugin.Enable-Tab-Complete")) return matches;
		String[] choices = args.length == 1 ? new String[] {"online", "away", "busy", "offline", "appear", "appear-offline", "appearoffline"}
				: args.length == 2 && "appear".equalsIgnoreCase(args[0]) ? new String[] {"offline"} : new String[0];
		String input = args.length == 0 ? "" : args[args.length - 1].toLowerCase(java.util.Locale.ROOT);
		for (String choice : choices) if (choice.startsWith(input)) matches.add(choice);
		return matches;
	}
}
