package net.simplyrin.bungeefriends.commands;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import lombok.Getter;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;
import net.simplyrin.bungeefriends.Main;
import net.simplyrin.bungeefriends.messages.MessageFrame;
import net.simplyrin.bungeefriends.messages.Messages;
import net.simplyrin.bungeefriends.messages.Permissions;
import net.simplyrin.bungeefriends.utils.LanguageManager.LanguageUtils;

/**
 * Created by SimplyRin on 2020/04/30.
 *
 * author: SimplyRin
 * license: LGPL v3
 * copyright: Copyright (c) 2021 SimplyRin
 */
public class ChatCommand extends Command {

	private Main plugin;
	@Getter
	private Map<UUID, Channel> channels = new ConcurrentHashMap<>();
	private final Map<UUID, GlobalMessage> globalMessages = new ConcurrentHashMap<>();

	private static final class GlobalMessage {
		final String text;
		final long expiresAt;
		GlobalMessage(String text, long expiresAt) { this.text = text; this.expiresAt = expiresAt; }
	}

	public void sendGlobal(ProxiedPlayer player, String message) {
		GlobalMessage marker = new GlobalMessage(message, System.currentTimeMillis() + 2000);
		globalMessages.put(player.getUniqueId(), marker);
		plugin.getProxy().getScheduler().schedule(plugin,
				() -> globalMessages.remove(player.getUniqueId(), marker), 2, TimeUnit.SECONDS);
		player.chat(message);
	}

	public boolean isGlobalMessage(UUID player, String text) {
		GlobalMessage marker = globalMessages.get(player);
		return marker != null && marker.expiresAt >= System.currentTimeMillis() && marker.text.equals(text);
	}

	public ChatCommand(Main plugin, String cmd) {
		super(cmd, null);
		this.plugin = plugin;
	}

	public enum Channel {
		ALL, PARTY, STAFF, ADMIN
	}

	@Override
	public void execute(CommandSender sender, String[] args) {
		if (!(sender instanceof ProxiedPlayer)) {
			this.plugin.info(Messages.INGAME_ONLY);
			return;
		}

		ProxiedPlayer player = (ProxiedPlayer) sender;
		LanguageUtils langUtils = this.plugin.getLanguageManager().getPlayer(player);

		if (args.length > 0) {
			if (args[0].equalsIgnoreCase("all") || args[0].equalsIgnoreCase("a") || args[0].equalsIgnoreCase("default")) {
				this.channels.remove(player.getUniqueId());
				this.plugin.infoFrame(player, MessageFrame.MessageId.CHAT_COMMAND_MESSAGES);
				this.plugin.info(player, langUtils.format("Chat.Update", "channel", langUtils.getString("Chat.Channels.All")));
				this.plugin.infoFrame(player, MessageFrame.MessageId.CHAT_COMMAND_MESSAGES);
				return;
			}
			if (args[0].equalsIgnoreCase("party") || args[0].equalsIgnoreCase("p")) {
				if (this.plugin.getPartyManager() == null
						|| !this.plugin.getPartyManager().getPlayer(player).isJoinedParty()) {
					this.plugin.infoFramed(player, langUtils.getString("Chat.NeedInAParty"), MessageFrame.MessageId.CHAT_NEED_PARTY);
					return;
				}
				this.channels.put(player.getUniqueId(), Channel.PARTY);
				this.plugin.infoFrame(player, MessageFrame.MessageId.CHAT_COMMAND_MESSAGES);
				this.plugin.info(player, langUtils.format("Chat.Update", "channel", langUtils.getString("Chat.Channels.Party")));
				this.plugin.infoFrame(player, MessageFrame.MessageId.CHAT_COMMAND_MESSAGES);
				return;
			}
			if (args[0].equalsIgnoreCase("staff") || args[0].equalsIgnoreCase("s")) {
				if (!player.hasPermission(Permissions.STAFF_CHAT) && !player.hasPermission(Permissions.ADMIN)) {
					this.plugin.infoFramed(player, langUtils.getString("Chat.No-Permission"), MessageFrame.MessageId.CHAT_NO_PERMISSION);
					return;
				}
				this.channels.put(player.getUniqueId(), Channel.STAFF);
				this.plugin.infoFrame(player, MessageFrame.MessageId.CHAT_COMMAND_MESSAGES);
				this.plugin.info(player, langUtils.format("Chat.Update", "channel", langUtils.getString("Chat.Channels.Staff")));
				this.plugin.infoFrame(player, MessageFrame.MessageId.CHAT_COMMAND_MESSAGES);
				return;
			}
			if (args[0].equalsIgnoreCase("admin")) {
				if (!player.hasPermission(Permissions.ADMIN)) {
					this.plugin.infoFramed(player, langUtils.getString("Chat.No-Permission"), MessageFrame.MessageId.CHAT_NO_PERMISSION);
					return;
				}
				this.channels.put(player.getUniqueId(), Channel.ADMIN);
				this.plugin.infoFramed(player, langUtils.format("Chat.Update", "channel", langUtils.getString("Chat.Channels.Admin")),
						MessageFrame.MessageId.CHAT_UPDATED);
				return;
			}
		}

		this.plugin.infoFrame(player, MessageFrame.MessageId.CHAT_COMMAND_MESSAGES);
		this.plugin.info(player, langUtils.getString("Chat.Usage"));
		this.plugin.infoFrame(player, MessageFrame.MessageId.CHAT_COMMAND_MESSAGES);
	}

}
