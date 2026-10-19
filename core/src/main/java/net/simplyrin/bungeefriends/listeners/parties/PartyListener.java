package net.simplyrin.bungeefriends.listeners.parties;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
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
import net.simplyrin.bungeefriends.exceptions.parties.NotJoinedException;
import net.simplyrin.bungeefriends.messages.Messages;
import net.simplyrin.bungeefriends.tools.ThreadPool;
import net.simplyrin.bungeefriends.utils.LanguageManager.LanguageUtils;
import net.simplyrin.bungeefriends.utils.PartyManager.PartyUtils;

/**
 * Created by SimplyRin on 2018/07/31.
 *
 * author: SimplyRin
 * license: LGPL v3
 * copyright: Copyright (c) 2021 SimplyRin
 */
public class PartyListener implements Listener {

	private Main plugin;

	public PartyListener(Main plugin) {
		this.plugin = plugin;
	}

	@EventHandler
	public void onLogin(PostLoginEvent event) {
		ProxiedPlayer player = event.getPlayer();
		this.plugin.cancelExpiry("party-logout:" + player.getUniqueId());
		this.plugin.cancelExpiry("party-offline-member:" + player.getUniqueId());

		this.plugin.getPartyManager().getPlayer(player);

		this.plugin.set("Parties." + player.getUniqueId().toString() + ".Name", player.getName());

		this.plugin.set("Name." + player.getName().toLowerCase(), player.getUniqueId().toString());
		this.plugin.set("UUID." + player.getUniqueId().toString(), player.getName().toLowerCase());
		this.notifyConnection(player.getUniqueId(), "Party-Connection.Rejoin");
	}

	@EventHandler
	public void onChat(ChatEvent event) {
		if (!(event.getSender() instanceof ProxiedPlayer)) {
			return;
		}

		if (event.isCommand()) {
			return;
		}

		ProxiedPlayer player = (ProxiedPlayer) event.getSender();

		if (this.plugin.getChatCommand().isGlobalMessage(player.getUniqueId(), event.getMessage())) return;
		Channel channel = this.plugin.getChatCommand().getChannels().get(player.getUniqueId());
		if (channel == null) {
			return;
		}

		if (channel.equals(Channel.PARTY)) {
			PartyUtils myParties = this.plugin.getPartyManager().getPlayer(player);
			if (!myParties.isJoinedParty()) {
				event.setCancelled(true);
				this.plugin.getChatCommand().getChannels().remove(player.getUniqueId());
				this.plugin.info(player, this.plugin.getLanguageManager().getPlayer(player).getString("Chat.NeedInAParty"));
				return;
			}

			PartyUtils partyLeader;
			try {
				partyLeader = myParties.getPartyLeader();
			} catch (NotJoinedException e) {
				this.plugin.getChatCommand().getChannels().remove(player.getUniqueId());
				return;
			}

			event.setCancelled(true);
			try {
				partyLeader.partyChat(player, event.getMessage());
			} catch (NotJoinedException e) {
			}
			return;
		}
	}

	@EventHandler
	public void partyCmdSender(ChatEvent event) {
		if (!event.isCommand()) {
			return;
		}

		// Plugin.BungeeParties.PartyCmdSender

		String cmd = event.getMessage();

		for (Entry<String, String> entry : this.plugin.getPartyCmdSenderMap().entrySet()) {
			String key = entry.getKey();
			String value = entry.getValue();

			if (cmd.startsWith(key)) {
				String gameId = cmd.replace(key, "").trim();
				String memberSend = value.replace("$args", gameId);

				ProxiedPlayer player = (ProxiedPlayer) event.getSender();

				PartyUtils myParties = this.plugin.getPartyManager().getPlayer(player);
				PartyUtils partyLeader;
				try {
					partyLeader = myParties.getPartyLeader();
				} catch (NotJoinedException e) {
					return;
				}
				boolean isPartyLeader = player.getUniqueId().toString().equals(partyLeader.getUniqueId().toString());
				if (!isPartyLeader) {
					return;
				}

				for (String stringUniqueId : partyLeader.getParties()) {
					UUID uniqueId = UUID.fromString(stringUniqueId);
					ProxiedPlayer partyMember = this.plugin.getProxy().getPlayer(uniqueId);
					if (partyMember != null) {
						partyMember.chat(memberSend);
					}
				}
				break;
			}
		}
	}

	@EventHandler
	public void onDisconnect(PlayerDisconnectEvent event) {
		ProxiedPlayer player = event.getPlayer();
		this.plugin.getPartyWarpManager().cancelFor(player.getUniqueId());
		PartyUtils myParties = this.plugin.getPartyManager().getPlayer(player);

		if (myParties == null) {
			return;
		}

		try {
			if (!myParties.isPartyOwner()) {
				int minutes = this.plugin.getConfigManager().getConfig().getInt("Plugin.Party-Offline-Member-Kick-Delay-Minutes", 5);
				this.notifyConnection(player.getUniqueId(), minutes == 0 ? "Party-Connection.Offline-No-Auto-Kick" : "Party-Connection.Offline");
				this.scheduleOfflineMemberKick(player.getUniqueId(), myParties);
				return;
			}
		} catch (NotJoinedException e) {
			return;
		}
		if (myParties.getParties().isEmpty()) return;
		final int disbandMinutes = this.plugin.getConfigManager().getConfig().getInt("Plugin.Party-Disband-Delay-Minutes", 5);

		ThreadPool.run(() -> {
			for (String partyPlayerUniqueId : myParties.getParties()) {
				PartyUtils targetPlayer = this.plugin.getPartyManager().getPlayer(partyPlayerUniqueId);
				LanguageUtils targetLangUtils = this.plugin.getLanguageManager().getPlayer(partyPlayerUniqueId);

				if (targetPlayer.getPlayer() == null) continue;
				this.plugin.info(targetPlayer.getPlayer(), targetLangUtils.getString(Messages.HYPHEN));

				this.plugin.info(targetPlayer.getPlayer(), targetLangUtils.getString("Logout.One").replace("%displayName", myParties.getDisplayName()));
				this.plugin.info(targetPlayer.getPlayer(), targetLangUtils.getString("Logout.Two").replace("%time%", String.valueOf(disbandMinutes)).replace("%time", String.valueOf(disbandMinutes)));
				if (myParties.getOnlinePlayers() <= 1)
					this.plugin.info(targetPlayer.getPlayer(), targetLangUtils.getString("Logout.Disband-When-Empty").replace("%time%", String.valueOf(disbandMinutes)).replace("%time", String.valueOf(disbandMinutes)));
				this.plugin.info(targetPlayer.getPlayer(), targetLangUtils.getString(Messages.HYPHEN));
			}

		});
		this.plugin.scheduleExpiry("party-logout:" + myParties.getUniqueId(), () -> {

			if (myParties.getPlayer() != null || !myParties.getUniqueId().toString().equals(myParties.getCurrentlyJoinedParty())) {
				return;
			}

			String uniqueId = null;
			for (String memberId : myParties.getParties()) {
				if (this.plugin.getProxy().getPlayer(UUID.fromString(memberId)) != null) {
					uniqueId = memberId;
					break;
				}
			}
			if (uniqueId != null) {

				try {
					myParties.promote(UUID.fromString(uniqueId));
					PartyUtils newParties = this.plugin.getPartyManager().getPlayer(uniqueId);

					for (String partyPlayerUniqueId : newParties.getParties()) {
						PartyUtils targetPlayer = this.plugin.getPartyManager().getPlayer(partyPlayerUniqueId);
						LanguageUtils targetLangUtils = this.plugin.getLanguageManager().getPlayer(partyPlayerUniqueId);

						this.plugin.info(targetPlayer.getPlayer(), targetLangUtils.getString(Messages.HYPHEN));
						this.plugin.info(targetPlayer.getPlayer(), targetLangUtils.getString("Logout.Become").replace("%displayName", newParties.getDisplayName()));
						this.plugin.info(targetPlayer.getPlayer(), targetLangUtils.getString(Messages.HYPHEN));
					}

					LanguageUtils targetLangUtils = this.plugin.getLanguageManager().getPlayer(uniqueId);
					this.plugin.info(newParties.getPlayer(), targetLangUtils.getString(Messages.HYPHEN));
					this.plugin.info(newParties.getPlayer(), targetLangUtils.getString("Logout.Become").replace("%displayName", newParties.getDisplayName()));
					this.plugin.info(newParties.getPlayer(), targetLangUtils.getString(Messages.HYPHEN));
				} catch (NotJoinedException e) {
				}
			} else {
				for (String partyPlayerUniqueId : myParties.getParties()) {
					PartyUtils targetPlayer = this.plugin.getPartyManager().getPlayer(partyPlayerUniqueId);
					LanguageUtils targetLangUtils = this.plugin.getLanguageManager().getPlayer(partyPlayerUniqueId);

					this.plugin.info(targetPlayer.getPlayer(), targetLangUtils.getString(Messages.HYPHEN));
					this.plugin.info(targetPlayer.getPlayer(), targetLangUtils.getString("Disband.Disbanded").replace("%displayName", myParties.getDisplayName()));
					this.plugin.info(targetPlayer.getPlayer(), targetLangUtils.getString(Messages.HYPHEN));

				}
				myParties.disband();
			}
		}, disbandMinutes, TimeUnit.MINUTES);
	}

	private void notifyConnection(UUID playerId, String key) {
		PartyUtils member = this.plugin.getPartyManager().getPlayer(playerId);
		if (!member.isJoinedParty()) return;
		PartyUtils leader;
		try { leader = member.getPartyLeader(); } catch (NotJoinedException e) { return; }
		if (leader.getUniqueId().equals(playerId)) return;
		List<String> recipients = new ArrayList<>(leader.getParties());
		recipients.add(leader.getUniqueId().toString());
		for (String recipient : recipients) {
			UUID recipientId = UUID.fromString(recipient);
			if (recipientId.equals(playerId) || this.plugin.getProxy().getPlayer(recipientId) == null) continue;
			LanguageUtils language = this.plugin.getLanguageManager().getPlayer(recipientId);
			this.plugin.info(recipientId, language.getString(key)
					.replace("%displayName", this.plugin.getFriendManager().getPlayer(playerId).getDisplayNameFor(recipientId))
					.replace("%time%", String.valueOf(this.plugin.getConfigManager().getConfig().getInt("Plugin.Party-Offline-Member-Kick-Delay-Minutes", 5))));
		}
	}

	private void scheduleOfflineMemberKick(UUID playerId, PartyUtils member) {
		int minutes = this.plugin.getConfigManager().getConfig().getInt("Plugin.Party-Offline-Member-Kick-Delay-Minutes", 5);
		if (minutes == 0) return;
		PartyUtils leader;
		try { leader = member.getPartyLeader(); } catch (NotJoinedException e) { return; }
		UUID leaderId = leader.getUniqueId();
		this.plugin.scheduleExpiry("party-offline-member:" + playerId, () -> {
			if (this.plugin.getProxy().getPlayer(playerId) != null) return;
			PartyUtils current = this.plugin.getPartyManager().getPlayer(playerId);
			if (!current.isJoinedParty()) return;
			try {
				PartyUtils currentLeader = current.getPartyLeader();
				if (!currentLeader.getUniqueId().equals(leaderId)) return;
				currentLeader.remove(playerId);
				List<String> recipients = new ArrayList<>(currentLeader.getParties());
				recipients.add(currentLeader.getUniqueId().toString());
				for (String recipient : recipients) {
					UUID recipientId = UUID.fromString(recipient);
					if (this.plugin.getProxy().getPlayer(recipientId) != null) {
						LanguageUtils language = this.plugin.getLanguageManager().getPlayer(recipientId);
						this.plugin.info(recipientId, language.getString("Remove-Party.Member-Removed")
								.replace("%targetDisplayName", this.plugin.getFriendManager().getPlayer(playerId).getDisplayNameFor(recipientId)));
					}
				}
			} catch (NotJoinedException ignored) { }
		}, minutes, TimeUnit.MINUTES);
	}

	@EventHandler
	public void onSwitch(ServerSwitchEvent event) {
		ProxiedPlayer player = event.getPlayer();
		PartyUtils partyUtils = this.plugin.getPartyManager().getPlayer(player);

		try {
			if (!partyUtils.isPartyOwner()) {
				return;
			}
		} catch (NotJoinedException e) {
			return;
		}

		if (player.getServer() == null || player.getServer().getInfo() == null) return;
		String serverName = player.getServer().getInfo().getName();
		if (serverName == null || serverName.isEmpty()) {
			return;
		}
		String lobbyFilter = this.plugin.getString("Plugin.Bypass-Lobby-Name-Contains");
		if (lobbyFilter != null && !lobbyFilter.isEmpty()
				&& serverName.toLowerCase(java.util.Locale.ROOT).contains(lobbyFilter.toLowerCase(java.util.Locale.ROOT))) {
			return;
		}
		if (this.plugin.isPartyWarpBlocked(serverName)) return;

		if (this.plugin.getBoolean("Plugin.Disable-PartyWarp")) {
			return;
		}

		this.plugin.getPartyWarpManager().startWarp(player, partyUtils, false);
	}

}
