package net.simplyrin.bungeefriends.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.TabExecutor;
import net.simplyrin.bungeefriends.Main;
import net.simplyrin.bungeefriends.exceptions.parties.NotInvitedException;
import net.simplyrin.bungeefriends.exceptions.parties.NotJoinedException;
import net.simplyrin.bungeefriends.messages.MessageFrame;
import net.simplyrin.bungeefriends.messages.Messages;
import net.simplyrin.bungeefriends.messages.Permissions;
import net.simplyrin.bungeefriends.tools.ThreadPool;
import net.simplyrin.bungeefriends.utils.LanguageManager.LanguageUtils;
import net.simplyrin.bungeefriends.utils.MessageBuilder;
import net.simplyrin.bungeefriends.utils.PartyManager.PartyUtils;

/**
 * Created by SimplyRin on 2018/07/31.
 *
 * author: SimplyRin
 * license: LGPL v3
 * copyright: Copyright (c) 2021 SimplyRin
 */
public class PartyCommand extends Command implements TabExecutor {

	private Main plugin;

	public PartyCommand(Main plugin) {
		super("party", null, "p");
		this.plugin = plugin;
	}

	@Override
	public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
		List<String> matches = new ArrayList<>();
		if (!plugin.getBoolean("Plugin.Enable-Tab-Complete") || args == null || args.length == 0) return matches;
		String input = args[args.length - 1] == null ? "" : args[args.length - 1].toLowerCase(java.util.Locale.ROOT);
		if (args.length == 1) {
			for (String command : new String[] {"invite", "leave", "list", "remove", "kick", "promote", "disband", "accept", "hijack", "setting", "warp", "toggle", "chat", "kickoffline", "help"})
				if (command.startsWith(input) && (!command.equals("hijack") || sender.hasPermission(Permissions.ADMIN) || sender.hasPermission(Permissions.PARTY_HIJACK))) matches.add(command);
			if (!matches.isEmpty() || input.isEmpty()) return matches;
			return playerMatches(sender, input);
		}
		String command = args[0].toLowerCase(java.util.Locale.ROOT);
		if (command.equals("setting") && args.length == 2) {
			for (String option : new String[] {"allinvite", "mute", "private", "help"})
				if (option.startsWith(input) && (!option.equals("private") || sender.hasPermission(Permissions.ADMIN) || sender.hasPermission(Permissions.PARTY_PRIVATE))) matches.add(option);
		} else if (args.length == 2 && (command.equals("invite") || command.equals("remove") || command.equals("kick")
				|| command.equals("promote") || command.equals("accept") || command.equals("hijack"))) {
			if (command.equals("hijack") && !sender.hasPermission(Permissions.ADMIN) && !sender.hasPermission(Permissions.PARTY_HIJACK)) return matches;
			return playerMatches(sender, input);
		}
		return matches;
	}

	private List<String> playerMatches(CommandSender sender, String input) {
		List<String> matches = new ArrayList<>();
		if (!(sender instanceof ProxiedPlayer) || input.isEmpty() || ((ProxiedPlayer) sender).getServer() == null) return matches;
		String server = ((ProxiedPlayer) sender).getServer().getInfo().getName();
		for (ProxiedPlayer player : plugin.getProxy().getPlayers())
			if (player.getServer() != null && player.getServer().getInfo().getName().equals(server)
					&& player.getName().toLowerCase(java.util.Locale.ROOT).startsWith(input)) matches.add(player.getName());
		return matches;
	}

	@Override
	public void execute(CommandSender sender, String[] args) {
		ThreadPool.run(() -> this.async(sender, args));
	}

	public void async(CommandSender sender, String[] args) {
		if (!(sender instanceof ProxiedPlayer)) {
			this.plugin.info(Messages.INGAME_ONLY);
			return;
		}

		ProxiedPlayer player = (ProxiedPlayer) sender;
		PartyUtils myParties = this.plugin.getPartyManager().getPlayer(player);
		LanguageUtils langUtils = this.plugin.getLanguageManager().getPlayer(player);

		if (args.length > 0) {
			if (args[0].equalsIgnoreCase("invite")) {
				if (args.length > 1) {
					this.invite(player, myParties, args[1]);
					return;
				}
				this.plugin.infoFramed(player, langUtils.getString("Invite.Usage"), MessageFrame.MessageId.PARTY_INVITE_USAGE);
				return;
			}

			if (args[0].equalsIgnoreCase("leave")) {
				try {
					if (myParties.isPartyOwner()) {
						this.plugin.infoFramed(player, langUtils.getString("Leave.NeedDisband"), MessageFrame.MessageId.PARTY_LEAVE_NEED_DISBAND);
						return;
					}
				} catch (Exception e) {
				}

				if (!myParties.isJoinedParty()) {
					this.plugin.infoFramed(player, langUtils.getString("No-Joined-The-Party"), MessageFrame.MessageId.NO_JOINED_PARTY);
					return;
				}

				PartyUtils partyLeader = myParties.leaveCurrentParty();
				LanguageUtils partyLeaderLangUtils = this.plugin.getLanguageManager().getPlayer(partyLeader.getUniqueId());

				this.plugin.infoFramed(player, langUtils.getString("Leave.You-Left"), MessageFrame.MessageId.PARTY_LEFT);

				if (myParties.getParties().size() > 0) {
					this.plugin.infoFramed(partyLeader.getPlayer(), partyLeaderLangUtils.format("Leave.Player-Left", "displayName", myParties.getDisplayName()),
							MessageFrame.MessageId.PARTY_PLAYER_LEFT);

					for (String partyPlayerUniqueId : myParties.getParties()) {
						ProxiedPlayer partyPlayer = this.plugin.getProxy().getPlayer(partyPlayerUniqueId);
						LanguageUtils partyPlayerLangUtils = this.plugin.getLanguageManager().getPlayer(partyPlayerUniqueId);
						this.plugin.infoFramed(partyPlayer, partyPlayerLangUtils.format("Leave.Player-Left", "displayName", myParties.getDisplayName()),
								MessageFrame.MessageId.PARTY_PLAYER_LEFT);
					}
				}
				return;
			}

			if (args[0].equalsIgnoreCase("list")) {
				if (!myParties.isJoinedParty()) {
					this.plugin.infoFramed(player, langUtils.getString("No-Joined-The-Party"), MessageFrame.MessageId.NO_JOINED_PARTY);
					return;
				}

				PartyUtils partyLeader;
				try {
					partyLeader = myParties.getPartyLeader();
				} catch (NotJoinedException e) {
					return;
				}

				List<String> parties = partyLeader.getParties();

				StringBuilder members = new StringBuilder();
				for (String partyPlayerUniqueId : parties) {
					PartyUtils member = this.plugin.getPartyManager().getPlayer(partyPlayerUniqueId);
					if (members.length() > 0) members.append("&7, ");
					members.append(member.getDisplayName()).append(member.getPlayer() != null ? " &a●" : " &c●");
				}

				this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_LIST);
				this.plugin.info(player, langUtils.format("List.Party-Header", "size", String.valueOf(parties.size() + 1)));
				this.plugin.info(player, langUtils.format("List.Party-Leader", "leader", partyLeader.getDisplayName() + (partyLeader.getPlayer() != null ? " &a●" : " &c●")));
				this.plugin.info(player, langUtils.format("List.Party-Members", "members", members.length() == 0 ? langUtils.getString("List.None") : members.toString()));
				this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_LIST);
				return;
			}

			if (args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("kick")) {
				if (args.length > 1) {
					if (!myParties.isJoinedParty()) {
						this.plugin.infoFramed(player, langUtils.getString("No-Joined-The-Party"), MessageFrame.MessageId.NO_JOINED_PARTY);
						return;
					}
					try {
						if (!myParties.isPartyOwner()) {
							this.plugin.info(player, langUtils.getString("Disband.Must-Leader"));
							return;
						}
					} catch (NotJoinedException e) {
						this.plugin.info(player, langUtils.formatException(e, "Exceptions.Not-Joined"));
						return;
					}

					UUID target = this.plugin.getPlayerManager().getPlayerUniqueId(args[1]);
					if (target == null) {
						this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
						this.plugin.info(player, langUtils.format("Cant-Find", "name", args[1]));
						this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
						return;
					}
					PartyUtils targetParties = this.plugin.getPartyManager().getPlayer(target);

					try {
						myParties.remove(target);
					} catch (NotJoinedException e) {
						this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
						this.plugin.info(player, langUtils.formatException(e, "Exceptions.Isnt-In-Your-Party", "targetDisplayName", targetParties.getDisplayName()));
						this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
						return;
					}

					this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
					this.plugin.info(player, langUtils.format("Remove-Party.Member-Removed", "targetDisplayName", targetParties.getDisplayName()));
					this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);

					if (myParties.getParties().size() > 0) {
						for (String partyPlayerUniqueId : myParties.getParties()) {
							ProxiedPlayer partyPlayer = this.plugin.getProxy().getPlayer(partyPlayerUniqueId);
							LanguageUtils targetLangUtils = this.plugin.getLanguageManager().getPlayer(partyPlayerUniqueId);

							this.plugin.infoFrame(partyPlayer, MessageFrame.MessageId.PARTY_BROADCAST);
							this.plugin.info(partyPlayer, targetLangUtils.format("Remove-Party.Member-Removed", "targetDisplayName", targetParties.getDisplayName()));
							this.plugin.infoFrame(partyPlayer, MessageFrame.MessageId.PARTY_BROADCAST);
						}
					}

					LanguageUtils targetLangUtils = this.plugin.getLanguageManager().getPlayer(target);

					this.plugin.infoFrame(target, MessageFrame.MessageId.PARTY_BROADCAST);
					this.plugin.info(target, targetLangUtils.format("Remove-Party.Me-Removed", "displayName", myParties.getDisplayName()));
					this.plugin.infoFrame(target, MessageFrame.MessageId.PARTY_BROADCAST);
					return;
				}
				this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
				this.plugin.info(player, langUtils.getString("Remove-Party.Usage"));
				this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
				return;
			}

			if (args[0].equalsIgnoreCase("promote")) {
				try {
					if (!myParties.isPartyOwner()) {
						this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
						this.plugin.info(player, langUtils.getString("Disband.Must-Leader"));
						this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
						return;
					}
				} catch (NotJoinedException e) {
					this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
					this.plugin.info(player, langUtils.formatException(e, "Exceptions.Not-Joined"));
					this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
					return;
				}

				if (args.length > 1) {
					UUID target = this.plugin.getPlayerManager().getPlayerUniqueId(args[1]);
					if (target == null) {
						this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
						this.plugin.info(player, langUtils.format("Cant-Find", "name", args[1]));
						this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
						return;
					}
					PartyUtils newPartyLeader = this.plugin.getPartyManager().getPlayer(target);
					UUID uniqueId = newPartyLeader.getUniqueId();

					try {
						myParties.promote(uniqueId);
					} catch (NotJoinedException e) {
						this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
						this.plugin.info(player, langUtils.formatException(e, "Exceptions.Isnt-In-Your-Party", "targetDisplayName", newPartyLeader.getDisplayName()));
						this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
						return;
					}

					String displayName = myParties.getDisplayName();
					myParties = this.plugin.getPartyManager().getPlayer(target);

					for (String partyPlayerUniqueId : myParties.getParties()) {
						PartyUtils targetPlayer = this.plugin.getPartyManager().getPlayer(partyPlayerUniqueId);
						LanguageUtils targetLangUtils = this.plugin.getLanguageManager().getPlayer(partyPlayerUniqueId);

						this.plugin.infoFrame(targetPlayer.getPlayer(), MessageFrame.MessageId.PARTY_BROADCAST);
						this.plugin.info(targetPlayer.getPlayer(), targetLangUtils.format("Promote.Promoted", "displayName", displayName, "targetDisplayName", newPartyLeader.getDisplayName()));
						this.plugin.infoFrame(targetPlayer.getPlayer(), MessageFrame.MessageId.PARTY_BROADCAST);
					}

					LanguageUtils targetLangUtils = this.plugin.getLanguageManager().getPlayer(newPartyLeader.getUniqueId());
					this.plugin.infoFrame(myParties.getPlayer(), MessageFrame.MessageId.PARTY_BROADCAST);
					this.plugin.info(myParties.getPlayer(), targetLangUtils.format("Promote.Promoted", "displayName", displayName, "targetDisplayName", newPartyLeader.getDisplayName()));
					this.plugin.infoFrame(myParties.getPlayer(), MessageFrame.MessageId.PARTY_BROADCAST);
					return;
				}

				this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
				this.plugin.info(player, langUtils.getString("Promote.Usage"));
				this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
				return;
			}

			if (args[0].equalsIgnoreCase("hijack")) {
				if (!player.hasPermission(Permissions.ADMIN) && !player.hasPermission(Permissions.PARTY_HIJACK)) {
					this.plugin.info(player, langUtils.getString(Messages.NO_PERMISSION));
					return;
				}

				if (args.length > 1) {
					UUID target = this.plugin.getPlayerManager().getPlayerUniqueId(args[1]);
					if (target == null) {
						this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
					this.plugin.info(player, langUtils.format("Cant-Find", "name", args[1]));
						this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
						return;
					}

					PartyUtils targetParty = this.plugin.getPartyManager().getPlayer(target);
					try {
						if (!targetParty.isPartyOwner()) {
							targetParty = targetParty.getPartyLeader();
						}
					} catch (Exception e) {
					}

					if (targetParty.getParties().size() == 0) {
						this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
						this.plugin.info(player, langUtils.getString("Hijack.PlayerNotJoined"));
						this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
						return;
					}

					try {
						targetParty.hijack(player);
					} catch (IllegalStateException e) {
						this.plugin.info(player, langUtils.getString("Hijack.Failed"));
						return;
					}

					for (String partyPlayerUniqueId : targetParty.getParties()) {
						PartyUtils targetPlayer = this.plugin.getPartyManager().getPlayer(partyPlayerUniqueId);
						LanguageUtils targetLangUtils = this.plugin.getLanguageManager().getPlayer(partyPlayerUniqueId);

						this.plugin.infoFrame(targetPlayer.getPlayer(), MessageFrame.MessageId.PARTY_BROADCAST);
						this.plugin.info(targetPlayer.getPlayer(), targetLangUtils.format("Hijack.Other", "displayName", myParties.getDisplayName()));
						this.plugin.infoFrame(targetPlayer.getPlayer(), MessageFrame.MessageId.PARTY_BROADCAST);
					}

					this.plugin.infoFrame(myParties.getPlayer(), MessageFrame.MessageId.PARTY_BROADCAST);
					this.plugin.info(myParties.getPlayer(), langUtils.getString("Hijack.You"));
					this.plugin.infoFrame(myParties.getPlayer(), MessageFrame.MessageId.PARTY_BROADCAST);

					return;
				}

				this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
				this.plugin.info(player, langUtils.getString("Hijack.Usage"));
				this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
				return;
			}

			if (args[0].equalsIgnoreCase("warp")) {
				if (!this.plugin.getBoolean("Plugin.Enable-Party-Warp")) {
					this.plugin.info(player, langUtils.getString("Warp.Disabled"));
					return;
				}
				try {
					if (!myParties.isPartyOwner()) {
						this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
						this.plugin.info(player, langUtils.getString("Disband.Must-Leader"));
						this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
						return;
					}
				} catch (NotJoinedException e) {
					this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
					this.plugin.info(player, langUtils.getString("No-Joined-The-Party"));
					this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
					return;
				}
				if (player.getServer() == null || player.getServer().getInfo() == null) {
					this.plugin.info(player, langUtils.getString("Warp.No-Server"));
					return;
				}
				String destination = player.getServer().getInfo().getName();
				if (destination == null || destination.isEmpty()) {
					this.plugin.info(player, langUtils.getString("Warp.No-Server"));
					return;
				}
				if (this.plugin.isPartyWarpBlocked(destination)) {
					this.plugin.info(player, langUtils.getString("Warp.Blocked"));
					return;
				}
				if (!this.plugin.getPartyWarpManager().confirmOrPrompt(player, myParties, langUtils)) return;

				this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
				this.plugin.info(player, langUtils.format("Warp.Summon", "displayName", myParties.getDisplayName()));
				this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);

				this.plugin.getPartyWarpManager().startWarp(player, myParties, true);
				return;
			}

			if (args[0].equalsIgnoreCase("accept")) {
				if (args.length > 1) {
					UUID target = this.plugin.getPlayerManager().getPlayerUniqueId(args[1]);
					if (target == null) {
						this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
						this.plugin.info(player, langUtils.format("Cant-Find", "name", args[1]));
						this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
						return;
					}
					PartyUtils targetParties = this.plugin.getPartyManager().getPlayer(target);
					try {
						targetParties.acceptRequest(player.getUniqueId());
					} catch (Exception e) {
						this.plugin.info(player, langUtils.formatException(e, "Exceptions.Havent-Received-Invite"));
						return;
					}

					this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
					this.plugin.info(player, langUtils.format("Accept-Party.You-Joined", "targetDisplayName", targetParties.getDisplayName()));
					this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);

					LanguageUtils targetLangUtils = this.plugin.getLanguageManager().getPlayer(targetParties.getUniqueId());

					this.plugin.infoFrame(targetParties.getPlayer(), MessageFrame.MessageId.PARTY_BROADCAST);
					this.plugin.info(targetParties.getPlayer(), targetLangUtils.format("Accept-Party.Joined", "displayName", myParties.getDisplayName()));
					this.plugin.infoFrame(targetParties.getPlayer(), MessageFrame.MessageId.PARTY_BROADCAST);

					for (String partyPlayerUniqueId : targetParties.getParties()) {
						PartyUtils targetPlayer = this.plugin.getPartyManager().getPlayer(partyPlayerUniqueId);
						targetLangUtils = this.plugin.getLanguageManager().getPlayer(targetPlayer.getUniqueId());

						this.plugin.infoFrame(targetPlayer.getPlayer(), MessageFrame.MessageId.PARTY_BROADCAST);
						this.plugin.info(targetPlayer.getPlayer(), targetLangUtils.format("Accept-Party.Joined", "displayName", myParties.getDisplayName()));
						this.plugin.infoFrame(targetPlayer.getPlayer(), MessageFrame.MessageId.PARTY_BROADCAST);
					}

					return;
				}
				this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
				this.plugin.info(player, langUtils.getString("Accept-Party.Usage"));
				this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
				return;
			}

			if (args[0].equalsIgnoreCase("setting")) {
				if (args.length == 1 || args[1].equalsIgnoreCase("help")) {
					for (String key : new String[] {"Command", "AllInvite", "Mute", "Private"})
						this.plugin.info(player, langUtils.getString("Help-Party-Setting." + key));
					return;
				}
				if (args.length != 2 || !(args[1].equalsIgnoreCase("allinvite")
						|| args[1].equalsIgnoreCase("mute") || args[1].equalsIgnoreCase("private"))) {
					this.plugin.info(player, langUtils.getString("Party-Setting.Usage"));
					return;
				}
				try {
					if (!myParties.isPartyOwner()) {
						this.plugin.info(player, langUtils.getString("Disband.Must-Leader"));
						return;
					}
				} catch (NotJoinedException e) {
					this.plugin.info(player, langUtils.formatException(e, "Exceptions.Not-Joined"));
					return;
				}
				if (args[1].equalsIgnoreCase("private") && !player.hasPermission(Permissions.PARTY_PRIVATE)
						&& !player.hasPermission(Permissions.ADMIN)) {
					this.plugin.info(player, langUtils.getString("Party-Private.No-Permission"));
					return;
				}
				String field = args[1].equalsIgnoreCase("allinvite") ? "AllInvite"
						: args[1].equalsIgnoreCase("mute") ? "Mute" : "Private";
				String key = "Parties." + myParties.getUniqueId() + "." + field;
				boolean enabled = !this.plugin.getBoolean(key);
				this.plugin.set(key, enabled);
				if (!this.plugin.isEnabledMySQL()) this.plugin.getDataManager().save();
				myParties.broadcast(langUtils.format("Party-" + field + "." + (enabled ? "Enabled" : "Disabled"),
					"displayName", myParties.getDisplayName()));
				if (field.equals("Mute") && enabled)
					myParties.broadcast(langUtils.getString("Party-Mute.Restricted-Chat-Info"));
				return;
			}

			if (args[0].equalsIgnoreCase("toggle")) {
				boolean bool = myParties.isEnabledReceiveRequest();
				if (bool) {
					myParties.setEnabledReceiveRequest(false);
					this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
					this.plugin.info(player, langUtils.getString("Toggle-Party.Disabled"));
					this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
					return;
				}
				myParties.setEnabledReceiveRequest(true);
				this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
				this.plugin.info(player, langUtils.getString("Toggle-Party.Enabled"));
				this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
				return;
			}

			if (args[0].equalsIgnoreCase("disband")) {
				try {
					if (!myParties.isPartyOwner()) {
						this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
						this.plugin.info(player, langUtils.getString("Disband.Must-Leader"));
						this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
						return;
					}
				} catch (NotJoinedException e) {
					this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
					this.plugin.info(player, langUtils.formatException(e, "Exceptions.Not-Joined"));
					this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
					return;
				}

				this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
				this.plugin.info(player, langUtils.format("Disband.Disbanded", "displayName", myParties.getDisplayName()));
				this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);

				for (String partyPlayerUniqueId : myParties.getParties()) {
					PartyUtils targetPlayer = this.plugin.getPartyManager().getPlayer(partyPlayerUniqueId);
					LanguageUtils targetLangUtils = this.plugin.getLanguageManager().getPlayer(targetPlayer.getUniqueId());

					this.plugin.infoFrame(targetPlayer.getUniqueId(), MessageFrame.MessageId.PARTY_BROADCAST);
					this.plugin.info(targetPlayer.getUniqueId(), targetLangUtils.format("Disband.Disbanded", "displayName", myParties.getDisplayName()));
					this.plugin.infoFrame(targetPlayer.getUniqueId(), MessageFrame.MessageId.PARTY_BROADCAST);

				}

				myParties.disband();
				return;
			}

			if (args[0].equalsIgnoreCase("kickoffline")) {
				if (!myParties.isJoinedParty()) {
					this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
					this.plugin.info(player, langUtils.getString("No-Joined-The-Party"));
					this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
					return;
				}

				PartyUtils partyLeader;
				try {
					partyLeader = myParties.getPartyLeader();
				} catch (NotJoinedException e) {
					e.printStackTrace();
					return;
				}
				if (!partyLeader.getUniqueId().equals(myParties.getUniqueId())) {
					return;
				}

				boolean found = false;

				for (String stringUniqueId : partyLeader.getParties()) {
					UUID uniqueId = UUID.fromString(stringUniqueId);
					ProxiedPlayer member = this.plugin.getProxy().getPlayer(uniqueId);
					if (member == null) {
						try {
							partyLeader.remove(uniqueId);

							String displayName = plugin.getFriendManager().getPlayer(uniqueId).getDisplayName();

							this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
							this.plugin.info(player, langUtils.format("Remove-Party.Member-Removed", "targetDisplayName", displayName));
							this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);

							for (String partyPlayerUniqueId : myParties.getParties()) {
								ProxiedPlayer partyPlayer = this.plugin.getProxy().getPlayer(partyPlayerUniqueId);
								LanguageUtils targetLangUtils = this.plugin.getLanguageManager().getPlayer(partyPlayerUniqueId);

								this.plugin.infoFrame(partyPlayer, MessageFrame.MessageId.PARTY_BROADCAST);
								this.plugin.info(partyPlayer, targetLangUtils.format("Remove-Party.Member-Removed", "targetDisplayName", displayName));
								this.plugin.infoFrame(partyPlayer, MessageFrame.MessageId.PARTY_BROADCAST);
							}
							found = true;
						} catch (NotJoinedException e) {
						}
					}
				}
				if (partyLeader.getParties().isEmpty()) {
					plugin.set("Parties." + myParties.getUniqueId().toString() + ".Currently-Joined-Party", "NONE");
					plugin.set("Parties." + myParties.getUniqueId().toString() + ".Party-List", new ArrayList<>());
					this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
					this.plugin.info(player, langUtils.format("Disband.Disbanded", "displayName", myParties.getDisplayName()));
					this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
				}
				if (!found) {
					this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
					this.plugin.info(player, langUtils.getString("KickOffline.AllOnline"));
					this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
				}
				return;
			}

			if (args[0].equalsIgnoreCase("help")) {
				this.printHelp(player);
				return;
			}

			if (args[0].equalsIgnoreCase("chat")) {
				if (args.length > 1) {
					String message = "";
					for (int i = 1; i < args.length; i++) {
						message += args[i] + " ";
					}
					message = message.trim();

					PartyUtils partyLeader;
					try {
						partyLeader = myParties.getPartyLeader();
					} catch (NotJoinedException e) {
						return;
					}

					try {
						partyLeader.partyChat(player, message);
					} catch (NotJoinedException e) {
						this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
						this.plugin.info(player, langUtils.getString("Chat.NeedInAParty"));
						this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
						return;
					}
					return;
				}
				this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
				this.plugin.info(player, langUtils.getString("Chat.ChatUsage"));
				this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
				return;
			}

			this.invite(player, myParties, args[0]);
			return;
		}
		this.printHelp(player);
	}

	private void printHelp(ProxiedPlayer player) {
		LanguageUtils langUtils = this.plugin.getLanguageManager().getPlayer(player);

		this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
		this.plugin.info(player, langUtils.getString("Help-Party.Command"));
		this.plugin.info(player, langUtils.getString("Help-Party.Help"));
		this.plugin.info(player, langUtils.getString("Help-Party.Chat"));
		this.plugin.info(player, langUtils.getString("Help-Party.Invite"));
		this.plugin.info(player, langUtils.getString("Help-Party.Leave"));
		this.plugin.info(player, langUtils.getString("Help-Party.List"));
		this.plugin.info(player, langUtils.getString("Help-Party.Remove"));
		this.plugin.info(player, langUtils.getString("Help-Party.Warp"));
		this.plugin.info(player, langUtils.getString("Help-Party.Accept"));
		this.plugin.info(player, langUtils.getString("Help-Party.Toggle"));
		this.plugin.info(player, langUtils.getString("Help-Party.Disband"));
		this.plugin.info(player, langUtils.getString("Help-Party.Promote"));
		this.plugin.info(player, langUtils.getString("Help-Party.KickOffline"));
		this.plugin.info(player, langUtils.getString("Help-Party.Setting"));
		if (player.hasPermission(Permissions.ADMIN) || player.hasPermission(Permissions.PARTY_HIJACK)) {
			this.plugin.info(player, langUtils.getString("Help-Party.Hijack"));
		}
		this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
	}

	private void invite(ProxiedPlayer player, PartyUtils myParties, String args) {
		LanguageUtils langUtils = this.plugin.getLanguageManager().getPlayer(player);
		PartyUtils leader = myParties;
		if (myParties.isJoinedParty()) {
			try { leader = myParties.getPartyLeader(); } catch (NotJoinedException ignored) { }
		}
		if (!leader.getUniqueId().equals(player.getUniqueId())
				&& !this.plugin.getBoolean("Parties." + leader.getUniqueId() + ".AllInvite")) {
			this.plugin.info(player, langUtils.getString("Party-AllInvite.No-Invite-Permission"));
			return;
		}
		final PartyUtils invitationParty = leader;

		UUID target = this.plugin.getPlayerManager().getPlayerUniqueId(args);
		if (target == null) {
			this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
			this.plugin.info(player, langUtils.format("Cant-Find", "name", args));
			this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
			return;
		}
		PartyUtils targetParties = this.plugin.getPartyManager().getPlayer(target);
		LanguageUtils targetLangUtils = this.plugin.getLanguageManager().getPlayer(target);

		if (targetParties.getPlayer() == null) {
			this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
			this.plugin.info(player, langUtils.format("Tell-Command.Offline", "targetDisplayName", targetParties.getDisplayName()));
			this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
			return;
		}

		try {
			invitationParty.addRequest(target);
		} catch (Exception e) {
			this.plugin.infoFramed(player, langUtils.formatException(e, "Exceptions.Havent-Received-Invite", "targetDisplayName", targetParties.getDisplayName()),
					MessageFrame.MessageId.PARTY_INVITE_ERROR);
			return;
		}

		this.plugin.infoFramed(player, langUtils.format("Invite.Request.Sent",
				"displayName", invitationParty.getDisplayName(),
				"targetDisplayName", targetParties.getDisplayName()), MessageFrame.MessageId.PARTY_INVITE_SENT);

		for (String partyPlayerUniqueId : invitationParty.getParties()) {
			UUID uniqueId = UUID.fromString(partyPlayerUniqueId);
			LanguageUtils pLangUtils = this.plugin.getLanguageManager().getPlayer(uniqueId);
			this.plugin.infoFramed(uniqueId, pLangUtils.format("Invite.Request.Party-Notice",
					"displayName", invitationParty.getDisplayName(),
					"targetDisplayName", targetParties.getDisplayName()), MessageFrame.MessageId.PARTY_INVITE_NOTICE);
		}

		String clickHere = targetLangUtils.getString("Invite.Request.Click-here.Here");
		String leaderName = this.plugin.getString("Parties." + invitationParty.getUniqueId() + ".Name");
		String clickToRun = targetLangUtils.format("Invite.Request.Click-here.Command", "name", leaderName);
		TextComponent invite = MessageBuilder.get(targetLangUtils.format("Invite.Request.Click-here.Receive",
				"displayName", invitationParty.getDisplayName()));
		TextComponent click = MessageBuilder.get(clickHere, "/party accept " + leaderName, ChatColor.GOLD, clickToRun, false);
		TextComponent accept = MessageBuilder.get(targetLangUtils.getString("Invite.Request.Click-here.Accept"),
				"/party accept " + leaderName, ChatColor.YELLOW, clickToRun, false);
		this.plugin.infoFramed(target, MessageBuilder.append(invite, click, accept), MessageFrame.MessageId.PARTY_INVITE_RECEIVED);
		try {
			this.plugin.scheduleExpiry("party:" + invitationParty.getUniqueId() + ":" + targetParties.getUniqueId(), () -> {

			try {
				invitationParty.removeRequest(target);
			} catch (NotInvitedException e) {
				return;
			}

			this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
			plugin.info(player, langUtils.format("Invite.Request.Expired.Your-Self", "targetDisplayName", targetParties.getDisplayName()));
			this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);

			if (invitationParty.getParties().size() == 0) {
				this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
				plugin.info(player, langUtils.getString("Invite.All-Left"));
				this.plugin.infoFrame(player, MessageFrame.MessageId.PARTY_BROADCAST);
			}

			this.plugin.infoFrame(target, MessageFrame.MessageId.PARTY_BROADCAST);
			plugin.info(target, targetLangUtils.format("Invite.Request.Expired.Target", "displayName", invitationParty.getDisplayName()));
			this.plugin.infoFrame(target, MessageFrame.MessageId.PARTY_BROADCAST);
			}, 1, TimeUnit.MINUTES);
		} catch (IllegalStateException rejected) {
			try { invitationParty.removeRequest(target); } catch (NotInvitedException ignored) { }
			throw rejected;
		}
	}

}
