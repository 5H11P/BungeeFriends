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
import net.simplyrin.bungeefriends.messages.MessageFrame;
import net.simplyrin.bungeefriends.messages.Messages;
import net.simplyrin.bungeefriends.tools.ThreadPool;
import net.simplyrin.bungeefriends.utils.LanguageManager.LanguageUtils;
import net.simplyrin.bungeefriends.utils.PartyManager.PartyUtils;
import net.simplyrin.bungeefriends.utils.TickDuration;
import net.simplyrin.bungeefriends.utils.PartyCommandMatcher;

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
		if (!this.plugin.isEnabledMySQL()) this.plugin.getPlayerManager().save();
		this.notifyConnection(player.getUniqueId(), "Party-Connection.Rejoin", MessageFrame.MessageId.PARTY_CONNECTION_REJOIN);
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
				this.plugin.infoFramed(player, this.plugin.getLanguageManager().getPlayer(player).getString("Chat.NeedInAParty"),
						MessageFrame.MessageId.CHAT_NEED_PARTY);
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
		if (!event.isCommand() || !this.plugin.getConfigManager().getPartyCommandSenderConfig().getBoolean("Enable", true)
				|| !(event.getSender() instanceof ProxiedPlayer)) {
			return;
		}

		// party-command-sender.yml -> Mappings

		String cmd = event.getMessage();

		for (Entry<String, String> entry : this.plugin.getPartyCmdSenderMap().entrySet()) {
			String key = entry.getKey();
			String value = entry.getValue();

			String gameId = PartyCommandMatcher.arguments(cmd, key);
			if (gameId != null) {
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
				int ticks = this.plugin.getConfigManager().getConfig().getInt("Plugin.Party.Offline-Member-Kick-Delay-Ticks", 6000);
				this.notifyConnection(player.getUniqueId(), ticks == 0 ? "Party-Connection.Offline-No-Auto-Kick" : "Party-Connection.Offline",
						ticks == 0 ? MessageFrame.MessageId.PARTY_CONNECTION_OFFLINE_NO_KICK : MessageFrame.MessageId.PARTY_CONNECTION_OFFLINE);
				this.scheduleOfflineMemberKick(player.getUniqueId(), myParties);
				return;
			}
		} catch (NotJoinedException e) {
			return;
		}
		if (myParties.getParties().isEmpty()) return;
		final int disbandTicks = this.plugin.getConfigManager().getConfig().getInt("Plugin.Party.Disband-Delay-Ticks", 6000);

		ThreadPool.run(() -> {
			for (String partyPlayerUniqueId : myParties.getParties()) {
				PartyUtils targetPlayer = this.plugin.getPartyManager().getPlayer(partyPlayerUniqueId);
				LanguageUtils targetLangUtils = this.plugin.getLanguageManager().getPlayer(partyPlayerUniqueId);

				if (targetPlayer.getPlayer() == null) continue;
				String message = targetLangUtils.getString("Logout.Leader-Disconnected")
						.replace("%displayName", myParties.getDisplayName())
						.replace("%duration%", TickDuration.format(disbandTicks, targetLangUtils));
				if (myParties.getOnlinePlayers() <= 1)
					message += "\n" + targetLangUtils.getString("Logout.Disband-When-Empty")
							.replace("%duration%", TickDuration.format(disbandTicks, targetLangUtils));
				this.plugin.infoFramed(targetPlayer.getPlayer(), message, MessageFrame.MessageId.PARTY_LOGOUT_LEADER_DISCONNECTED);
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

						this.plugin.infoFramed(targetPlayer.getPlayer(), targetLangUtils.getString("Logout.Become").replace("%displayName", newParties.getDisplayName()),
								MessageFrame.MessageId.PARTY_LEADER_CHANGED);
					}

					LanguageUtils targetLangUtils = this.plugin.getLanguageManager().getPlayer(uniqueId);
					this.plugin.infoFramed(newParties.getPlayer(), targetLangUtils.getString("Logout.Become").replace("%displayName", newParties.getDisplayName()),
							MessageFrame.MessageId.PARTY_LEADER_CHANGED);
				} catch (NotJoinedException e) {
				}
			} else {
				for (String partyPlayerUniqueId : myParties.getParties()) {
					PartyUtils targetPlayer = this.plugin.getPartyManager().getPlayer(partyPlayerUniqueId);
					LanguageUtils targetLangUtils = this.plugin.getLanguageManager().getPlayer(partyPlayerUniqueId);

					this.plugin.infoFramed(targetPlayer.getPlayer(), targetLangUtils.getString("Disband.Disbanded").replace("%displayName", myParties.getDisplayName()),
							MessageFrame.MessageId.PARTY_DISBANDED);

				}
				myParties.disband();
			}
		}, disbandMinutes, TimeUnit.MINUTES);
	}

	private void notifyConnection(UUID playerId, String key, MessageFrame.MessageId messageId) {
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
			this.plugin.infoFramed(recipientId, language.getString(key)
					.replace("%displayName", this.plugin.getFriendManager().getPlayer(playerId).getDisplayNameFor(recipientId))
					.replace("%duration%", TickDuration.format(
							this.plugin.getConfigManager().getConfig().getInt("Plugin.Party.Offline-Member-Kick-Delay-Ticks", 6000), language)), messageId);
		}
	}

	private void scheduleOfflineMemberKick(UUID playerId, PartyUtils member) {
		int ticks = this.plugin.getConfigManager().getConfig().getInt("Plugin.Party.Offline-Member-Kick-Delay-Ticks", 6000);
		if (ticks == 0) return;
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
						this.plugin.infoFramed(recipientId, language.getString("Remove-Party.Member-Removed")
								.replace("%targetDisplayName", this.plugin.getFriendManager().getPlayer(playerId).getDisplayNameFor(recipientId)),
								MessageFrame.MessageId.PARTY_MEMBER_REMOVED);
					}
				}
			} catch (NotJoinedException ignored) { }
		}, TickDuration.toMillis(ticks), TimeUnit.MILLISECONDS);
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
		String lobbyFilter = this.plugin.getString("Plugin.Party-Warp.Lobby-Server-Name-Contains");
		if (lobbyFilter != null && !lobbyFilter.isEmpty()
				&& serverName.toLowerCase(java.util.Locale.ROOT).contains(lobbyFilter.toLowerCase(java.util.Locale.ROOT))) {
			return;
		}
		if (this.plugin.isPartyWarpBlocked(serverName)) return;

		if (!this.plugin.getBoolean("Plugin.Enable-Party-Warp")) {
			return;
		}

		this.plugin.getPartyWarpManager().startWarp(player, partyUtils, false);
	}

}
