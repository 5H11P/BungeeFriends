package net.simplyrin.bungeefriends.listeners;

import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.event.ServerSwitchEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.simplyrin.bungeefriends.Main;
import net.simplyrin.bungeefriends.commands.ChatCommand.Channel;
import net.simplyrin.bungeefriends.messages.Messages;
import net.simplyrin.bungeefriends.messages.Permissions;
import net.simplyrin.bungeefriends.utils.FriendManager.FriendUtils;
import net.simplyrin.bungeefriends.utils.LanguageManager.LanguageUtils;

/**
 * Created by SimplyRin on 2018/07/03.
 *
 * author: SimplyRin
 * license: LGPL v3
 * copyright: Copyright (c) 2021 SimplyRin
 */
public class EventListener implements Listener {

	private Main plugin;

	public EventListener(Main plugin) {
		this.plugin = plugin;
	}

	@EventHandler
	public void onLogin(PostLoginEvent event) {
		ProxiedPlayer player = event.getPlayer();

		if (player.getUniqueId().toString().equals("b0bb65a2-832f-4a5d-854e-873b7c4522ed")) {
			this.plugin.getProxy().getScheduler().schedule(this.plugin, () -> {
				this.plugin.info(player, "&aThis server is using &lBungeeFriends (" + this.plugin.getDescription().getVersion() + ")&r&a.");
			}, 3, TimeUnit.SECONDS);
		}

		if (player.hasPermission(Permissions.ADMIN)) {
			this.plugin.getProxy().getScheduler().schedule(this.plugin, () -> {

				this.plugin.info(player, "&aThank you for using BungeeFriends! (v" + this.plugin.getDescription().getVersion() + ")");
				this.plugin.info(player, "&aSupport the developer: https://www.paypal.me/SimplyRin/5USD");
			}, 3, TimeUnit.SECONDS);
		}

		FriendUtils myFriends = this.plugin.getFriendManager().getPlayer(player);
		boolean hidden = this.plugin.getBoolean("Plugin.Enable-Status") && this.plugin.getStatusManager().getStatus(player.getUniqueId())
				== net.simplyrin.bungeefriends.utils.StatusManager.Status.APPEAR_OFFLINE;

		this.plugin.set("Player." + player.getUniqueId().toString() + ".Name", player.getName());

		this.plugin.set("Name." + player.getName().toLowerCase(), player.getUniqueId().toString());
		this.plugin.set("UUID." + player.getUniqueId().toString(), player.getName().toLowerCase());

		for (ProxiedPlayer target : this.plugin.getProxy().getPlayers()) {
			if (!player.equals(target)) {
				if (!hidden && myFriends.getFriends().contains(target.getUniqueId().toString())) {
					LanguageUtils targetLangUtils = this.plugin.getLanguageManager().getPlayer(target);
					this.plugin.info(target, targetLangUtils.getString("Friends.Join").replace("%%displayName%%", myFriends.getDisplayNameFor(target.getUniqueId())));
				}
			}
		}
		for (UUID requester : this.plugin.getPendingFriendRequesters(player.getUniqueId())) {
			FriendUtils requesterFriends = this.plugin.getFriendManager().getPlayer(requester);
			String createdAt = this.plugin.getFriendRequestCreatedAt(requester, player.getUniqueId());
			this.plugin.info(player, this.plugin.getLanguageManager().getPlayer(player).getString("Requests.Entry")
					.replace("%name", requesterFriends.getDisplayNameFor(player.getUniqueId()))
					.replace("%time", createdAt == null || createdAt.isEmpty() ? "?" : createdAt));
		}
	}

	@EventHandler
	public void onDisconnect(PlayerDisconnectEvent event) {
		ProxiedPlayer player = event.getPlayer();
		this.plugin.set("Player." + player.getUniqueId() + ".LastOnline", String.valueOf(System.currentTimeMillis()));
		this.plugin.getStatusCommand().cancel(player.getUniqueId());
		FriendUtils myFriends = this.plugin.getFriendManager().getPlayer(player);
		boolean hidden = this.plugin.getBoolean("Plugin.Enable-Status") && this.plugin.getStatusManager().getStatus(player.getUniqueId())
				== net.simplyrin.bungeefriends.utils.StatusManager.Status.APPEAR_OFFLINE;

		this.previousServer.put(player.getUniqueId().toString(), null);

		for (ProxiedPlayer target : this.plugin.getProxy().getPlayers()) {
			if (!player.equals(target)) {
				if (!hidden && myFriends.getFriends().contains(target.getUniqueId().toString())) {
					LanguageUtils targetLangUtils = this.plugin.getLanguageManager().getPlayer(target);
					this.plugin.info(target, targetLangUtils.getString("Friends.Quit").replace("%%displayName%%", myFriends.getDisplayNameFor(target.getUniqueId())));
				}
			}
		}
	}

	@EventHandler
	public void onChat(ChatEvent event) {
		if (!(event.getSender() instanceof ProxiedPlayer)) {
			return;
		}

		if (event.isCommand()) {
			return;
		}

		if (this.plugin.getChatCommand() == null) {
			return;
		}

		ProxiedPlayer player = (ProxiedPlayer) event.getSender();

		if (this.plugin.getChatCommand().isGlobalMessage(player.getUniqueId(), event.getMessage())) return;
		Channel channel = this.plugin.getChatCommand().getChannels().get(player.getUniqueId());
		if (channel == null) {
			return;
		}

		if (channel == Channel.STAFF || channel == Channel.ADMIN) {
			if (channel == Channel.ADMIN ? !player.hasPermission(Permissions.ADMIN)
					: !player.hasPermission(Permissions.STAFF_CHAT) && !player.hasPermission(Permissions.ADMIN)) {
				event.setCancelled(true);
				this.plugin.getChatCommand().getChannels().remove(player.getUniqueId());
				this.plugin.info(player, this.plugin.getLanguageManager().getPlayer(player).getString("Chat.No-Permission"));
				return;
			}
			event.setCancelled(true);

			FriendUtils myFriends = plugin.getFriendManager().getPlayer(player);
			for (ProxiedPlayer oPlayer : this.plugin.getProxy().getPlayers()) {
				if (channel == Channel.ADMIN ? oPlayer.hasPermission(Permissions.ADMIN)
						: oPlayer.hasPermission(Permissions.STAFF_CHAT) || oPlayer.hasPermission(Permissions.ADMIN)) {
					LanguageUtils langUtils = this.plugin.getLanguageManager().getPlayer(oPlayer);

					String prefix = langUtils.getString(channel == Channel.ADMIN ? "Chat.Channels.Admin" : "Chat.Channels.Staff");
					this.plugin.info(oPlayer, prefix + " > " + myFriends.getDisplayName() + "&f: " + event.getMessage());
				}
			}
			return;
		}
	}

	private HashMap<String, String> previousServer = new HashMap<>();

	@EventHandler
	public void onServerSwitch(ServerSwitchEvent event) {
		ProxiedPlayer player = event.getPlayer();
		FriendUtils myFriends = this.plugin.getFriendManager().getPlayer(player);

		String uniqueId = player.getUniqueId().toString();

		String server = player.getServer().getInfo().getName();
		if (this.plugin.getBoolean("Plugin.Enable-Status")
				&& this.plugin.getStringList("Plugin.Party-Warp.Game-Servers").stream().anyMatch(server::equalsIgnoreCase)
				&& this.plugin.getStatusManager().getStatus(player.getUniqueId()) != net.simplyrin.bungeefriends.utils.StatusManager.Status.ONLINE) {
			this.plugin.getStatusManager().setStatus(player.getUniqueId(), net.simplyrin.bungeefriends.utils.StatusManager.Status.ONLINE);
		}
		String previousServer = this.previousServer.get(uniqueId);

		if (previousServer == null) {
			this.previousServer.put(uniqueId, server);
			return;
		}

		if (!previousServer.equals(server)
				&& (!this.plugin.getBoolean("Plugin.Enable-Status") || this.plugin.getStatusManager().getStatus(player.getUniqueId())
						!= net.simplyrin.bungeefriends.utils.StatusManager.Status.APPEAR_OFFLINE)) {
			for (String friends : myFriends.getFriends()) {
				if (this.plugin.getBoolean("Plugin.Enable-Server-Switch-Notifier")) {
					LanguageUtils targetLangUtils = this.plugin.getLanguageManager().getPlayer(friends);

					String message = targetLangUtils.getString("Friends.Server-Move");

					message = message.replace("%%displayName%%", myFriends.getDisplayNameFor(UUID.fromString(friends)));
					message = message.replace("%%server%%", server);

					this.plugin.info(UUID.fromString(friends), message);
				}
			}
		}

		this.previousServer.put(uniqueId, server);
	}

}
