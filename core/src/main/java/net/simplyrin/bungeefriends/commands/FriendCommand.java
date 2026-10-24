package net.simplyrin.bungeefriends.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.TabExecutor;
import net.simplyrin.bungeefriends.Main;
import net.simplyrin.bungeefriends.exceptions.AlreadyAddedException;
import net.simplyrin.bungeefriends.exceptions.FailedAddingException;
import net.simplyrin.bungeefriends.exceptions.FriendSlotLimitException;
import net.simplyrin.bungeefriends.exceptions.IgnoredException;
import net.simplyrin.bungeefriends.exceptions.NotAddedException;
import net.simplyrin.bungeefriends.exceptions.RequestDenyException;
import net.simplyrin.bungeefriends.exceptions.SelfException;
import net.simplyrin.bungeefriends.messages.MessageFrame;
import net.simplyrin.bungeefriends.messages.Messages;
import net.simplyrin.bungeefriends.messages.Permissions;
import net.simplyrin.bungeefriends.tools.Priority;
import net.simplyrin.bungeefriends.tools.ThreadPool;
import net.simplyrin.bungeefriends.utils.FriendManager.FriendUtils;
import net.simplyrin.bungeefriends.utils.LanguageManager.LanguageUtils;
import net.simplyrin.bungeefriends.utils.MessageBuilder;
import net.simplyrin.bungeefriends.utils.PaginationMessage;

/**
 * Created by SimplyRin on 2018/07/03.
 *
 * author: SimplyRin
 * license: LGPL v3
 * copyright: Copyright (c) 2021 SimplyRin
 */
public class FriendCommand extends Command implements TabExecutor {

	private Main plugin;

	public FriendCommand(Main plugin, String command) {
		super(command, null);
		this.plugin = plugin;
	}

	@Override
	public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
		List<String> list = new ArrayList<>();
		if (!this.plugin.getBoolean("Plugin.Enable-Tab-Complete") || args == null || args.length == 0) return list;
		String input = args[args.length - 1] == null ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
		if (args.length == 1) {
			String[] commands = {"add", "remove", "removeall", "accept", "deny", "requests", "note", "nickname", "notifications", "best", "list", "lang", "language", "ignore", "toggle", "help"};
			for (String command : commands) addIfMatches(list, command, input);
			if (sender.hasPermission(Permissions.FORCE_ADD) || sender.hasPermission(Permissions.ADMIN)) addIfMatches(list, "force-add", input);
			if (sender.hasPermission(Permissions.ADMIN)) {
				addIfMatches(list, "prefix", input);
				addIfMatches(list, "reload", input);
			}
			if (!list.isEmpty() || input.isEmpty()) return list;
			return playerMatches(sender, input);
		}
		String command = args[0].toLowerCase(Locale.ROOT);
		if ((command.equals("lang") || command.equals("language")) && args.length == 2) {
			addIfMatches(list, "english", input);
			addIfMatches(list, "chinese", input);
		} else if (command.equals("reload") && args.length == 2 && sender.hasPermission(Permissions.ADMIN)) {
			for (String option : new String[] {"all", "config", "lang"}) addIfMatches(list, option, input);
		} else if (command.equals("removeall") && args.length == 2) {
			addIfMatches(list, "confirm", input);
		} else if (command.equals("ignore") && args.length == 2) {
			for (String option : new String[] {"add", "remove", "list"}) addIfMatches(list, option, input);
		} else if (command.equals("ignore") && args.length == 3
				&& (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove"))) {
			return playerMatches(sender, input);
		} else if (command.equals("note") && args.length == 2) {
			return playerMatches(sender, input);
		} else if ((command.equals("nickname") || command.equals("best")) && args.length == 2) {
			return playerMatches(sender, input);
		} else if (command.equals("note") && args.length == 3) {
			addIfMatches(list, "reset", input);
			addIfMatches(list, "confirm", input);
		} else if (args.length == 2 && (command.equals("add") || command.equals("remove") || command.equals("accept")
				|| command.equals("deny") || command.equals("force-add") || command.equals("prefix"))) {
			return playerMatches(sender, input);
		}
		return list;
	}

	private void addIfMatches(List<String> list, String value, String input) {
		if (value.startsWith(input)) list.add(value);
	}

	private List<String> playerMatches(CommandSender sender, String input) {
		List<String> matches = new ArrayList<>();
		if (!(sender instanceof ProxiedPlayer) || input.isEmpty()) return matches;
		ProxiedPlayer source = (ProxiedPlayer) sender;
		if (source.getServer() == null) return matches;
		String server = source.getServer().getInfo().getName();
		for (ProxiedPlayer player : this.plugin.getProxy().getPlayers()) {
			if (player.getServer() != null && player.getServer().getInfo().getName().equals(server)
					&& player.getName().toLowerCase(Locale.ROOT).startsWith(input)) matches.add(player.getName());
		}
		return matches;
	}

	@Override
	public void execute(CommandSender sender, String[] args) {
		ThreadPool.run(() -> this.async(sender, args));
	}

	public void async(CommandSender sender, String[] args) {
		if (!(sender instanceof ProxiedPlayer)) {
			this.plugin.getFriendConsoleCommand().execute(sender, args);
			return;
		}

		ProxiedPlayer player = (ProxiedPlayer) sender;
		FriendUtils myFriends = this.plugin.getFriendManager().getPlayer(player);
		LanguageUtils langUtils = this.plugin.getLanguageManager().getPlayer(player);

		if (args.length > 0) {
			if (args[0].equalsIgnoreCase("add")) {
				if (args.length > 1) {
					this.add(player, myFriends, langUtils, args[1]);
					return;
				}
				this.plugin.info(player, langUtils.getString("Add.Usage"));
				return;
			}

			if (args[0].equalsIgnoreCase("remove")) {
				if (args.length > 1) {
					UUID target = this.plugin.getPlayerManager().getPlayerUniqueId(args[1]);
					if (target == null) {
						this.plugin.infoFramed(player, langUtils.format("Cant-Find", "name", args[1]), MessageFrame.MessageId.PLAYER_NOT_FOUND);
						return;
					}
					FriendUtils targetFriends = this.plugin.getFriendManager().getPlayer(target);
					LanguageUtils targetLangUtils = this.plugin.getLanguageManager().getPlayer(target);

					try {
						myFriends.remove(target);
					} catch (NotAddedException e) {
						this.plugin.infoFramed(player, langUtils.format("Exceptions.IsntOnYourFriends", "targetDisplayName", targetFriends.getDisplayName()),
								MessageFrame.MessageId.FRIEND_REMOVE_NOT_FRIEND);
						return;
					} catch (SelfException e) {
						this.plugin.infoFramed(player, langUtils.getString("Exceptions.Cant-Remove-Yourself"), MessageFrame.MessageId.FRIEND_REMOVE_SELF);
						return;
					}
					this.plugin.infoFramed(player, langUtils.format("Remove.YourSelf", "targetDisplayName", targetFriends.getDisplayName()),
							MessageFrame.MessageId.FRIEND_REMOVE_DONE);

					if (this.plugin.getBoolean("Plugin.Enable-Friend-Removal-Notifications")) {
						this.plugin.infoFramed(target, targetLangUtils.format("Remove.Target", "displayName", myFriends.getDisplayName()),
								MessageFrame.MessageId.FRIEND_REMOVE_TARGET);
					}
					return;
				}
				this.plugin.info(player, langUtils.getString("Remove.Usage"));
				return;
			}

			if (args[0].equalsIgnoreCase("removeall")) {
				if (args.length > 1) {
					if (args[1].equalsIgnoreCase("confirm")) {
						for (String uuid : myFriends.getFriends()) {
							UUID target = UUID.fromString(uuid);

							try {
								myFriends.remove(target);
							} catch (Exception e) {
								this.plugin.info(player, langUtils.format("Remove.All.Failed", "uuid", target.toString()));
							}
						}

						this.plugin.infoFramed(player, langUtils.getString("Remove.All.Done"), MessageFrame.MessageId.FRIEND_REMOVE_ALL_DONE);
						return;
					}
				}
				this.plugin.info(player, langUtils.getString("Remove.All.Confirm"));
				return;
			}

			if (args[0].equalsIgnoreCase("accept")) {
				if (args.length > 1) {
					UUID target = this.plugin.getPlayerManager().getPlayerUniqueId(args[1]);
					if (target == null) {
						this.plugin.infoFramed(player, langUtils.format("Cant-Find", "name", args[1]), MessageFrame.MessageId.PLAYER_NOT_FOUND);
						return;
					}
					FriendUtils targetFriends = this.plugin.getFriendManager().getPlayer(target);
					LanguageUtils targetLangUtils = this.plugin.getLanguageManager().getPlayer(target);

					String sent = this.plugin.getFriendRequestCreatedAt(target, player.getUniqueId());
					long sentAt;
					try { sentAt = Long.parseLong(sent); } catch (NumberFormatException e) { sentAt = 0; }
					if (!targetFriends.getRequests().contains(player.getUniqueId().toString())
							|| sentAt <= 0 || System.currentTimeMillis() - sentAt > TimeUnit.MINUTES.toMillis(5)) {
						if (targetFriends.getRequests().contains(player.getUniqueId().toString())) {
							try { targetFriends.removeRequest(player); } catch (NotAddedException ignored) { }
						}
						this.plugin.infoFramed(player, langUtils.format("Exceptions.NoInvited", "name", targetFriends.getName()),
								MessageFrame.MessageId.FRIEND_ACCEPT_NO_REQUEST);
						return;
					}
					try {
						myFriends.add(target);
						targetFriends.removeRequest(player);
					} catch (AlreadyAddedException | FailedAddingException | NotAddedException e) {
						this.plugin.info(player, langUtils.getString("Exceptions.AlreadyFriend"));
						return;
					}

					this.plugin.infoFramed(player, langUtils.format("Accept.YourSelf", "targetDisplayName", targetFriends.getDisplayName()),
							MessageFrame.MessageId.FRIEND_ACCEPTED);

					this.plugin.infoFramed(target, targetLangUtils.format("Accept.Target", "displayName", myFriends.getDisplayName()),
							MessageFrame.MessageId.FRIEND_ACCEPT_NOTICE);

					return;
				}
				this.plugin.info(player, langUtils.getString("Accept.Usage"));
				return;
			}

			if (args[0].equalsIgnoreCase("deny")) {
				if (args.length > 1) {
					UUID target = this.plugin.getPlayerManager().getPlayerUniqueId(args[1]);
					if (target == null) {
						this.plugin.infoFramed(player, langUtils.format("Cant-Find", "name", args[1]), MessageFrame.MessageId.PLAYER_NOT_FOUND);
						return;
					}
					FriendUtils targetFriends = this.plugin.getFriendManager().getPlayer(target);

					try {
						targetFriends.removeRequest(player);
					} catch (NotAddedException e) {
						this.plugin.infoFramed(player, langUtils.format("Exceptions.HasntFriend", "name", targetFriends.getName()),
								MessageFrame.MessageId.FRIEND_DENY_NOT_FRIEND);
						return;
					}

					this.plugin.infoFramed(player, langUtils.format("Deny.Declined", "targetDisplayName", targetFriends.getDisplayName()),
							MessageFrame.MessageId.FRIEND_DENIED);
					return;
				}
				this.plugin.info(player, langUtils.getString("Deny.Usage"));
				return;
			}

			if (args[0].equalsIgnoreCase("requests")) {
				List<UUID> requesters = this.plugin.getPendingFriendRequesters(player.getUniqueId());
				this.plugin.infoFrame(player, MessageFrame.MessageId.FRIEND_REQUESTS_LIST);
				if (requesters.isEmpty()) this.plugin.info(player, langUtils.getString("Requests.Empty"));
				for (UUID requester : requesters) {
					FriendUtils friend = this.plugin.getFriendManager().getPlayer(requester);
					String time = this.plugin.getFriendRequestCreatedAt(requester, player.getUniqueId());
					this.plugin.info(player, langUtils.format("Requests.Entry",
							"name", friend.getDisplayNameFor(player.getUniqueId()),
							"time", time == null || time.isEmpty() ? "?" : time));
				}
				this.plugin.infoFrame(player, MessageFrame.MessageId.FRIEND_REQUESTS_LIST);
				return;
			}

			if (args[0].equalsIgnoreCase("note")) {
				if (args.length < 2) {
					this.plugin.info(player, langUtils.getString("Note.Description.Usage"));
					return;
				}
				if (args[1].equalsIgnoreCase("resetall")) {
					if (args.length == 3 && args[2].equalsIgnoreCase("confirm")) {
						this.plugin.clearAllFriendCustomPrefixes(player.getUniqueId());
						this.plugin.info(player, langUtils.getString("Note.Description.Cleared"));
					} else this.plugin.info(player, langUtils.getString("Note.Description.Confirm"));
					return;
				}
				UUID target = this.plugin.getPlayerManager().getPlayerUniqueId(args[1]);
				if (target == null || !myFriends.isFriend(target)) {
					this.plugin.info(player, langUtils.getString("Note.Description.MustBeFriends"));
					return;
				}
				if (args.length == 2) {
					String note = this.plugin.getFriendCustomPrefix(player.getUniqueId(), target);
					String normalizedNote = this.normalizeNote(note);
					this.plugin.info(player, langUtils.format("Note.Description.Current",
							"note", normalizedNote.isEmpty() ? "-" : normalizedNote));
				} else if (args.length == 3 && args[2].equalsIgnoreCase("reset")) {
					this.plugin.clearFriendCustomPrefix(player.getUniqueId(), target);
					this.plugin.info(player, langUtils.getString("Note.Description.Cleared"));
				} else {
					String note = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
					int maxLength = this.plugin.getConfigManager().getConfig().getInt("Plugin.Friend.Max-Note-Length", 100);
					if (note.length() > maxLength) {
						this.plugin.info(player, langUtils.format("Note.Description.TooLong", "maxLength", maxLength));
						return;
					}
					this.plugin.setFriendCustomPrefix(player.getUniqueId(), target, note);
					this.plugin.info(player, langUtils.getString("Note.Description.Updated"));
				}
				return;
			}

			if (args[0].equalsIgnoreCase("nickname")) {
				if (args.length < 2) {
					this.plugin.info(player, langUtils.getString("Nickname.Usage"));
					return;
				}
				UUID target = this.plugin.getPlayerManager().getPlayerUniqueId(args[1]);
				if (target == null) {
					this.plugin.infoFramed(player, langUtils.format("Cant-Find", "name", args[1]), MessageFrame.MessageId.PLAYER_NOT_FOUND);
					return;
				}
				FriendUtils targetFriends = this.plugin.getFriendManager().getPlayer(target);
				if (!myFriends.isFriend(target)) {
					this.plugin.info(player, langUtils.getString("Nickname.MustBeFriends"));
					return;
				}
				String nickname = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)).trim() : "";
				int maxLength = this.plugin.getConfigManager().getConfig().getInt("Plugin.Friend.Max-Nickname-Length", 16);
				if (nickname.length() > maxLength || nickname.indexOf('\n') >= 0 || nickname.indexOf('\r') >= 0
						|| nickname.chars().anyMatch(Character::isISOControl)) {
					this.plugin.info(player, langUtils.format("Nickname.TooLong", "maxLength", maxLength));
					return;
				}
				String original = targetFriends.getName();
				boolean clear = nickname.isEmpty() || (original != null && nickname.equalsIgnoreCase(original));
				if (clear) {
					this.plugin.clearFriendNickname(player.getUniqueId(), target);
					this.plugin.infoFramed(player, langUtils.format("Nickname.Cleared", "targetDisplayName", targetFriends.getDisplayName()), MessageFrame.MessageId.FRIEND_NICKNAME_CLEARED);
				} else {
					this.plugin.setFriendNickname(player.getUniqueId(), target, nickname);
					this.plugin.infoFramed(player, langUtils.format("Nickname.Set", "targetDisplayName", targetFriends.getDisplayName(), "nickname", nickname), MessageFrame.MessageId.FRIEND_NICKNAME_SET);
				}
				return;
			}

			if (args[0].equalsIgnoreCase("best")) {
				if (args.length < 2) {
					this.plugin.info(player, langUtils.getString("Best.Usage"));
					return;
				}
				UUID target = this.plugin.getPlayerManager().getPlayerUniqueId(args[1]);
				if (target == null) {
					this.plugin.infoFramed(player, langUtils.format("Cant-Find", "name", args[1]), MessageFrame.MessageId.PLAYER_NOT_FOUND);
					return;
				}
				FriendUtils targetFriends = this.plugin.getFriendManager().getPlayer(target);
				if (!myFriends.isFriend(target)) {
					this.plugin.info(player, langUtils.getString("Best.MustBeFriends"));
					return;
				}
				boolean best = !this.plugin.isBestFriend(player.getUniqueId(), target);
				this.plugin.setBestFriend(player.getUniqueId(), target, best);
				this.plugin.infoFramed(player, langUtils.format(best ? "Best.Enabled" : "Best.Disabled",
						"targetDisplayName", targetFriends.getDisplayName()), best ? MessageFrame.MessageId.FRIEND_BEST_ENABLED : MessageFrame.MessageId.FRIEND_BEST_DISABLED);
				return;
			}

			if (args[0].equalsIgnoreCase("notifications")) {
				boolean enabled = !this.plugin.areFriendNotificationsEnabled(player.getUniqueId());
				this.plugin.setFriendNotificationsEnabled(player.getUniqueId(), enabled);
				this.plugin.infoFramed(player, langUtils.getString("Notifications." + (enabled ? "Enabled" : "Disabled")),
						enabled ? MessageFrame.MessageId.FRIEND_NOTIFICATIONS_ENABLED : MessageFrame.MessageId.FRIEND_NOTIFICATIONS_DISABLED);
				return;
			}

			if (args[0].equalsIgnoreCase("list")) {
				this.renderFriendList(player, myFriends, langUtils, args);
				return;
			}

			if (args[0].equalsIgnoreCase("legacy-list")) {
				List<String> list = myFriends.getFriends();

				if (list.size() == 0) {
					this.plugin.infoFramed(player, langUtils.getString("List.Empty"), MessageFrame.MessageId.FRIEND_LIST_EMPTY);
					return;
				}

				List<TextComponent> online = new ArrayList<>();
				List<TextComponent> offline = new ArrayList<>();

				boolean colorOnly = this.plugin.getConfigManager().getFriendListConfig().getBoolean("Display-Only-Prefix-Color", false);

				Priority priority = this.plugin.getPriority();
				List<Priority.Item> items = priority.getPriorities();

				// 追加した UUID
				List<String> added = new ArrayList<>();

				for (Priority.Item item : items) {
					for (String uuid : list) {
						ProxiedPlayer target = this.plugin.getProxy().getPlayer(UUID.fromString(uuid));
						FriendUtils targetFriends = this.plugin.getFriendManager().getPlayer(UUID.fromString(uuid));

						String prefix = ChatColor.translateAlternateColorCodes('&', targetFriends.getPrefix());
						prefix = ChatColor.stripColor(prefix).trim();

						if (item.getNoColorPrefix().equals(prefix)) {
							String displayName = colorOnly ? targetFriends.getPrefixColor() + targetFriends.getName() : targetFriends.getDisplayName();

							added.add(uuid);

							if (target != null && (!this.plugin.getBoolean("Plugin.Enable-Status") || this.plugin.getStatusManager().getStatus(target.getUniqueId()) != net.simplyrin.bungeefriends.utils.StatusManager.Status.APPEAR_OFFLINE)) {
								online.add(this.friendListLine(player, target, langUtils, displayName));
							} else {
								offline.add(this.friendListLine(player, null, UUID.fromString(uuid), langUtils, displayName));
							}
						}
					}
				}

				List<String> needAdd = new ArrayList<>();
				needAdd.addAll(list);
				needAdd.removeAll(added);

				for (String uuid : needAdd) {
					ProxiedPlayer target = this.plugin.getProxy().getPlayer(UUID.fromString(uuid));
					FriendUtils targetFriends = this.plugin.getFriendManager().getPlayer(UUID.fromString(uuid));

					String prefix = ChatColor.translateAlternateColorCodes('&', targetFriends.getPrefix());
					prefix = ChatColor.stripColor(prefix);

					String displayName = colorOnly ? targetFriends.getPrefixColor() + targetFriends.getName() : targetFriends.getDisplayName();

					added.add(uuid);

					if (target != null && (!this.plugin.getBoolean("Plugin.Enable-Status") || this.plugin.getStatusManager().getStatus(target.getUniqueId()) != net.simplyrin.bungeefriends.utils.StatusManager.Status.APPEAR_OFFLINE)) {
						online.add(this.friendListLine(player, target, langUtils, displayName));
					} else {
						offline.add(this.friendListLine(player, null, UUID.fromString(uuid), langUtils, displayName));
					}
				}

				List<TextComponent> all = new ArrayList<>();
				all.addAll(online);
				all.addAll(offline);

				int page = 1;
				if (args.length > 1) {
					try {
						page = Integer.valueOf(args[1]).intValue();
						if (page < 1) {
							page = 1;
						}
					} catch (Exception e) {
					}
				}

				List<List<TextComponent>> divide = this.divide(all, 7);
				page = Math.min(page, divide.size());

				this.plugin.infoFrame(player, MessageFrame.MessageId.FRIEND_LIST_PAGE);
				for (TextComponent line : MessageBuilder.splitLines(
						PaginationMessage.build(langUtils, langUtils.getString("List.Page"), "/friend list ", page, divide.size()))) {
					player.sendMessage(line);
				}
				for (TextComponent message : divide.get(page - 1)) {
					for (TextComponent line : MessageBuilder.splitLines(message)) player.sendMessage(line);
				}

				this.plugin.infoFrame(player, MessageFrame.MessageId.FRIEND_LIST_PAGE);
				return;
			}

			if (args[0].equalsIgnoreCase("lang") || args[0].equalsIgnoreCase("language")) {
				if (args.length > 1 && (args[1].equalsIgnoreCase("english") || args[1].equalsIgnoreCase("chinese"))) {
					langUtils.setLanguage(args[1].toLowerCase(java.util.Locale.ROOT));
					this.plugin.info(player, langUtils.format("Lang.Update", "lang", langUtils.getLanguage()));
					return;
				}
				this.plugin.info(player, langUtils.getString("Lang.Usage"));
				this.plugin.info(player, langUtils.getString("Lang.Available") + " <English, Chinese>");
				return;
			}

			if (args[0].equalsIgnoreCase("ignore")) {
				if (args.length > 1) {
					if (args[1].equalsIgnoreCase("add")) {
						if (args.length > 2) {
							UUID target = this.plugin.getPlayerManager().getPlayerUniqueId(args[2]);
							if (target == null) {
								this.plugin.infoFramed(player, langUtils.format("Cant-Find", "name", args[2]), MessageFrame.MessageId.PLAYER_NOT_FOUND);
								return;
							}
							FriendUtils targetFriends = this.plugin.getFriendManager().getPlayer(target);

							try {
								myFriends.addIgnore(target);
							} catch (AlreadyAddedException e) {
				this.plugin.infoFramed(player, langUtils.format("Ignore.AlreadyAdded", "targetDisplayName", targetFriends.getDisplayName()),
						MessageFrame.MessageId.IGNORE_ALREADY_ADDED);
								return;
							}

			this.plugin.infoFramed(player, langUtils.format("Ignore.Added", "targetDisplayName", targetFriends.getDisplayName()),
					MessageFrame.MessageId.IGNORE_ADDED);
							return;
						}

						this.plugin.info(player, langUtils.getString("Ignore.Usage.Add"));
						return;
					}

					if (args[1].equalsIgnoreCase("remove")) {
						if (args.length > 2) {
							UUID target = this.plugin.getPlayerManager().getPlayerUniqueId(args[2]);
							if (target == null) {
								this.plugin.infoFramed(player, langUtils.format("Cant-Find", "name", args[2]), MessageFrame.MessageId.PLAYER_NOT_FOUND);
								return;
							}
							FriendUtils targetFriends = this.plugin.getFriendManager().getPlayer(target);

							try {
								myFriends.removeIgnore(target);
							} catch (NotAddedException e) {
				this.plugin.infoFramed(player, langUtils.format("Ignore.NotAdded", "targetDisplayName", targetFriends.getDisplayName()),
						MessageFrame.MessageId.IGNORE_NOT_ADDED);
								return;
							}

			this.plugin.infoFramed(player, langUtils.format("Ignore.Removed", "targetDisplayName", targetFriends.getDisplayName()),
					MessageFrame.MessageId.IGNORE_NOT_ADDED);
							return;
						}

						this.plugin.infoFrame(player, MessageFrame.MessageId.IGNORE_REMOVE_USAGE);
						this.plugin.info(player, langUtils.getString("Ignore.Usage.Remove"));
						this.plugin.infoFrame(player, MessageFrame.MessageId.IGNORE_REMOVE_USAGE);
						return;
					}

					if (args[1].equalsIgnoreCase("list")) {
						List<String> ignoreList = myFriends.getIgnoreList();
						if (ignoreList.size() == 0) {
							this.plugin.infoFramed(player, langUtils.getString("Ignore.Empty"), MessageFrame.MessageId.IGNORE_LIST_EMPTY);
							return;
						}
						this.plugin.infoFrame(player, MessageFrame.MessageId.IGNORE_LIST);
						for (String targetUniqueId : myFriends.getIgnoreList()) {
							FriendUtils targetFriends = this.plugin.getFriendManager().getPlayer(UUID.fromString(targetUniqueId));
							this.plugin.info(player, "&e- " + targetFriends.getDisplayName());
						}
						this.plugin.infoFrame(player, MessageFrame.MessageId.IGNORE_LIST);
						return;
					}
				}
				this.plugin.info(player, langUtils.getString("Ignore.Usage.Main"));
				return;
			}

			if (args[0].equalsIgnoreCase("force-add")) {
				if (!player.hasPermission(Permissions.ADMIN) && !player.hasPermission(Permissions.FORCE_ADD)) {
					this.plugin.info(player, langUtils.getString(Messages.NO_PERMISSION));
					return;
				}

				if (args.length > 1) {
					UUID target = this.plugin.getPlayerManager().getPlayerUniqueId(args[1]);
					if (target == null) {
						this.plugin.infoFramed(player, langUtils.format("Cant-Find", "name", args[1]), MessageFrame.MessageId.PLAYER_NOT_FOUND);
						return;
					}

					FriendUtils targetFriends = this.plugin.getFriendManager().getPlayer(target);
					LanguageUtils targetLangUtils = this.plugin.getLanguageManager().getPlayer(target);

					try {
						myFriends.add(target);
					} catch (AlreadyAddedException e) {
						this.plugin.infoFramed(player, langUtils.getString("Exceptions.AlreadyFriend"), MessageFrame.MessageId.FRIEND_ADD_ALREADY_FRIEND);
						return;
					} catch (FailedAddingException e) {
						this.plugin.infoFramed(player, langUtils.getString("Exceptions.Cant-Add-Yourself"), MessageFrame.MessageId.FRIEND_ADD_SELF);
						return;
					}

					this.plugin.infoFramed(player, langUtils.format("Force-Add.YourSelf",
							"targetDisplayName", targetFriends.getDisplayName()), MessageFrame.MessageId.FRIEND_FORCE_ADD_SELF);

					this.plugin.infoFramed(target, targetLangUtils.format("Force-Add.Target",
							"displayName", myFriends.getDisplayName()), MessageFrame.MessageId.FRIEND_FORCE_ADD_TARGET);
					return;
				}

				this.plugin.info(player, langUtils.getString("Force-Add.Usage"));
				return;
			}

			if (args[0].equalsIgnoreCase("reload")) {
				if (!player.hasPermission(Permissions.ADMIN)) {
					this.plugin.info(player, langUtils.getString(Messages.NO_PERMISSION));
					return;
				}

				if (args.length > 1) {
					if (args[1].equalsIgnoreCase("all")) {
						this.plugin.getConfigManager().reload();
						this.plugin.info(player, langUtils.getString("Reload.Config"));

						this.plugin.reloadLanguageManager();
						this.plugin.info(player, langUtils.getString("Reload.Lang"));
						return;
					}
					if (args[1].equalsIgnoreCase("config")) {
						this.plugin.getConfigManager().reload();
						this.plugin.info(player, langUtils.getString("Reload.Config"));
						return;
					}
					if (args[1].equalsIgnoreCase("lang")) {
						this.plugin.reloadLanguageManager();
						this.plugin.info(player, langUtils.getString("Reload.Lang"));
						return;
					}
				}

				this.plugin.info(player, langUtils.getString("Reload.Usage"));
				return;
			}

			if (args[0].equalsIgnoreCase("prefix")) {
				if (!player.hasPermission(Permissions.ADMIN)) {
					this.plugin.info(player, langUtils.getString(Messages.NO_PERMISSION));
					return;
				}

				if (args.length > 1) {
					UUID target = this.plugin.getPlayerManager().getPlayerUniqueId(args[1]);
					if (target == null) {
						this.plugin.infoFramed(player, langUtils.format("Cant-Find", "name", args[1]), MessageFrame.MessageId.PLAYER_NOT_FOUND);
						return;
					}

					FriendUtils targetFriends = this.plugin.getFriendManager().getPlayer(target);

					if (args.length > 2) {
						String prefix = "";
						for (int i = 2; i < args.length; i++) {
							prefix += args[i] + " ";
						}
						int maxLength = this.plugin.getConfigManager().getConfig().getInt("Plugin.Friend.Max-Prefix-Length", 16);
						if (prefix.trim().length() > maxLength) {
							this.plugin.info(player, langUtils.format("Prefix.TooLong", "maxLength", maxLength));
							return;
						}

						if (prefix.trim().length() <= 3) {
							prefix = prefix.trim();
						}
						String displayName = targetFriends.getDisplayName();
						targetFriends.setPrefix(prefix);
						this.plugin.infoFramed(player, langUtils.format("Prefix.To",
								"targetDisplayName", displayName,
								"prefix", ChatColor.translateAlternateColorCodes('&', prefix.trim())), MessageFrame.MessageId.FRIEND_PREFIX_SET);
						return;
					}

					this.plugin.infoFramed(player, langUtils.format("Prefix.Current",
							"targetDisplayName", targetFriends.getDisplayName(),
							"prefix", targetFriends.getPrefix().trim()), MessageFrame.MessageId.FRIEND_PREFIX_CURRENT);
					return;
				}

				this.plugin.info(player, langUtils.getString("Prefix.Usage"));
				return;
			}

			if (args[0].equalsIgnoreCase("toggle")) {
				boolean bool = this.plugin.getBoolean("Player." + myFriends.getUniqueId().toString() + ".Toggle");
				this.plugin.set("Player." + myFriends.getUniqueId().toString() + ".Toggle", !bool);
				this.plugin.info(player, langUtils.getString("Toggle." + (bool ? "Disabled" : "Enabled")));
				return;
			}

			if (args[0].equalsIgnoreCase("help")) {
				this.printHelp(player, langUtils);
				return;
			}

			this.add(player, myFriends, langUtils, args[0]);
			return;
		}
		this.printHelp(player, langUtils);
	}

	public void printHelp(ProxiedPlayer player, LanguageUtils langUtils) {
		this.plugin.infoFrame(player, MessageFrame.MessageId.FRIEND_HELP);
		this.plugin.info(player, langUtils.getString("Help.Command"));
		this.plugin.info(player, langUtils.getString("Help.Help"));
		this.plugin.info(player, langUtils.getString("Help.Lang"));
		this.plugin.info(player, langUtils.getString("Help.Add"));
		this.plugin.info(player, langUtils.getString("Help.Remove"));
		this.plugin.info(player, langUtils.getString("Help.RemoveAll"));
		this.plugin.info(player, langUtils.getString("Help.Accept"));
		this.plugin.info(player, langUtils.getString("Help.Deny"));
		this.plugin.info(player, langUtils.getString("Help.List"));
		this.plugin.info(player, langUtils.getString("Help.Requests"));
		this.plugin.info(player, langUtils.getString("Help.Note-Description"));
		this.plugin.info(player, langUtils.getString("Help.Nickname"));
		this.plugin.info(player, langUtils.getString("Help.Best"));
		this.plugin.info(player, langUtils.getString("Help.Notifications"));
		this.plugin.info(player, langUtils.getString("Help.Ignore"));
		this.plugin.info(player, langUtils.getString("Help.Toggle"));
		if (player.hasPermission(Permissions.ADMIN)) {
			this.plugin.infoFrame(player, MessageFrame.MessageId.FRIEND_HELP);
			this.plugin.info(player, langUtils.getString("Help.Force-Add"));
			this.plugin.info(player, langUtils.getString("Help.Prefix"));
			this.plugin.info(player, langUtils.getString("Help.Reload"));
		} else if (player.hasPermission(Permissions.FORCE_ADD)) {
			this.plugin.info(player, langUtils.getString("Help.Force-Add"));
		}
		this.plugin.infoFrame(player, MessageFrame.MessageId.FRIEND_HELP);
	}

	private void renderFriendList(ProxiedPlayer player, FriendUtils myFriends, LanguageUtils langUtils, String[] args) {
		List<String> friends = myFriends.getFriends();
		if (friends.isEmpty()) {
			this.plugin.infoFramed(player, langUtils.getString("List.Empty"), MessageFrame.MessageId.FRIEND_LIST_EMPTY);
			return;
		}
		boolean colorOnly = this.plugin.getConfigManager().getFriendListConfig().getBoolean("Display-Only-Prefix-Color", false);
		List<FriendListEntry> entries = new ArrayList<>();
		for (int index = 0; index < friends.size(); index++) {
			UUID targetId = UUID.fromString(friends.get(index));
			FriendUtils targetFriends = this.plugin.getFriendManager().getPlayer(targetId);
			ProxiedPlayer target = this.plugin.getProxy().getPlayer(targetId);
			boolean online = target != null && (!this.plugin.getBoolean("Plugin.Enable-Status")
					|| this.plugin.getStatusManager().getStatus(targetId) != net.simplyrin.bungeefriends.utils.StatusManager.Status.APPEAR_OFFLINE);
			String nickname = this.plugin.getFriendNickname(player.getUniqueId(), targetId);
			boolean nicked = nickname != null && !nickname.trim().isEmpty();
			String prefix = colorOnly ? targetFriends.getPrefixColor() : targetFriends.getPrefix();
			String originalName = targetFriends.getName();
			String name = nicked ? nickname.trim() + "*" : originalName;
			entries.add(new FriendListEntry(targetId, target, prefix, name, originalName, online,
					this.priorityRank(targetFriends), this.plugin.isBestFriend(player.getUniqueId(), targetId), index));
		}
		Collections.sort(entries, Comparator.comparing(FriendListEntry::isBest).reversed()
				.thenComparing(Comparator.comparing(FriendListEntry::isOnline).reversed())
				.thenComparingInt(FriendListEntry::getPriority)
				.thenComparingInt(FriendListEntry::getIndex));
		List<TextComponent> all = new ArrayList<>();
		for (FriendListEntry entry : entries) all.add(this.friendListLine(player, entry, langUtils));
		int page = 1;
		if (args.length > 1) {
			try { page = Math.max(1, Integer.parseInt(args[1])); }
			catch (NumberFormatException ignored) { }
		}
		List<List<TextComponent>> divide = this.divide(all, 7);
		page = Math.min(page, divide.size());
		this.plugin.infoFrame(player, MessageFrame.MessageId.FRIEND_LIST_PAGE);
		for (TextComponent line : MessageBuilder.splitLines(
				PaginationMessage.build(langUtils, langUtils.getString("List.Page"), "/friend list ", page, divide.size()))) player.sendMessage(line);
		for (TextComponent message : divide.get(page - 1))
			for (TextComponent line : MessageBuilder.splitLines(message)) player.sendMessage(line);
		this.plugin.infoFrame(player, MessageFrame.MessageId.FRIEND_LIST_PAGE);
	}

	private int priorityRank(FriendUtils friend) {
		String prefix = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', friend.getPrefix())).trim();
		List<Priority.Item> priorities = this.plugin.getPriority().getPriorities();
		for (int index = 0; index < priorities.size(); index++)
			if (priorities.get(index).getNoColorPrefix().equals(prefix)) return index;
		return priorities.size();
	}

	private TextComponent friendListLine(ProxiedPlayer viewer, FriendListEntry entry, LanguageUtils lang) {
		boolean visible = entry.online && entry.target != null && entry.target.getServer() != null && entry.target.getServer().getInfo() != null;
		String server = visible ? this.plugin.getServerName(entry.target.getServer().getInfo().getName()) : lang.getString("List.Unknown");
		String status = lang.getString("List.Unknown");
		String lineKey = "List.Offline";
		if (visible) {
			net.simplyrin.bungeefriends.utils.StatusManager.Status state = this.plugin.getStatusManager().getStatus(entry.id);
			lineKey = state == net.simplyrin.bungeefriends.utils.StatusManager.Status.ONLINE ? "List.Online" : "List.Status." + state.getKey();
			status = lang.format("Status.ChatBar." + state.getKey(), "targetDisplayName", "").trim();
		}
		String lastOnline = visible ? lang.getString("List.Now") : this.formatTime(this.plugin.getString("Player." + entry.id + ".LastOnline"), lang);
		String lastOnlineAgo = visible ? lang.getString("List.Now") : this.timeAgo(this.plugin.getString("Player." + entry.id + ".LastOnline"), lang);
		String displayName = entry.prefix + entry.name;
		String note = this.normalizeNote(this.plugin.getFriendCustomPrefix(viewer.getUniqueId(), entry.id));
		String hoverKey = visible ? (note.isEmpty() ? "List.Hover-Online" : "List.Hover-Online-With-Note")
				: (note.isEmpty() ? "List.Hover-Offline" : "List.Hover-Offline-With-Note");
		String hover = lang.format(hoverKey, "targetDisplayName", displayName, "targetName", entry.originalName, "note", note,
				"targetUUID", entry.id.toString(), "status", status, "server", server,
				"friendSince", this.formatTime(this.plugin.getFriendSince(viewer.getUniqueId(), entry.id), lang),
				"lastOnlineAgo", lastOnlineAgo, "lastOnline", lastOnline);
		String marker = "\u0000";
		String line = lang.format(lineKey, "targetDisplayName", marker, "server", server);
		int markerIndex = line.indexOf(marker);
		TextComponent result = MessageBuilder.get(line.substring(0, markerIndex));
		result.addExtra(friendListDisplayName(entry.prefix, entry.name, entry.best, hover));
		result.addExtra(MessageBuilder.get(line.substring(markerIndex + marker.length())));
		return result;
	}

	static TextComponent friendListDisplayName(String prefixText, String nameText, boolean best, String hover) {
		TextComponent result = new TextComponent();
		String translatedPrefix = ChatColor.translateAlternateColorCodes('&', prefixText);
		for (BaseComponent part : TextComponent.fromLegacyText(translatedPrefix)) result.addExtra(part);
		// Components are siblings, so the IGN must receive the formatting active at the end of the prefix.
		BaseComponent[] prefixAndMarker = TextComponent.fromLegacyText(translatedPrefix + "X");
		TextComponent name = MessageBuilder.get(nameText);
		name.copyFormatting(prefixAndMarker[prefixAndMarker.length - 1]);
		name.setBold(best);
		if (hover != null) name.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
				new net.md_5.bungee.api.chat.ComponentBuilder(ChatColor.translateAlternateColorCodes('&', hover)).create()));
		result.addExtra(name);
		return result;
	}

	private static final class FriendListEntry {
		private final UUID id;
		private final ProxiedPlayer target;
		private final String prefix;
		private final String name;
		private final String originalName;
		private final boolean online;
		private final int priority;
		private final boolean best;
		private final int index;
		FriendListEntry(UUID id, ProxiedPlayer target, String prefix, String name, String originalName, boolean online,
				int priority, boolean best, int index) {
			this.id = id; this.target = target; this.prefix = prefix; this.name = name; this.originalName = originalName;
			this.online = online; this.priority = priority; this.best = best; this.index = index;
		}
		boolean isOnline() { return online; }
		boolean isBest() { return best; }
		int getPriority() { return priority; }
		int getIndex() { return index; }
	}

	private TextComponent friendListLine(ProxiedPlayer viewer, ProxiedPlayer target, LanguageUtils lang, String displayName) {
		return this.friendListLine(viewer, target, target.getUniqueId(), lang, displayName);
	}

	private TextComponent friendListLine(ProxiedPlayer viewer, ProxiedPlayer target, UUID targetId, LanguageUtils lang, String displayName) {
		boolean visible = target != null && target.getServer() != null && target.getServer().getInfo() != null;
		String server = visible ? this.plugin.getServerName(target.getServer().getInfo().getName()) : lang.getString("List.Unknown");
		String status = lang.getString("List.Unknown");
		String lineKey = "List.Offline";
		if (visible) {
			net.simplyrin.bungeefriends.utils.StatusManager.Status state = this.plugin.getStatusManager().getStatus(targetId);
			String key = state == net.simplyrin.bungeefriends.utils.StatusManager.Status.ONLINE ? "List.Online" : "List.Status." + state.getKey();
			lineKey = key;
			status = lang.format("Status.ChatBar." + state.getKey(), "targetDisplayName", "").trim();
		}
		String lastOnline = visible ? lang.getString("List.Now") : this.formatTime(this.plugin.getString("Player." + targetId + ".LastOnline"), lang);
		String lastOnlineAgo = visible ? lang.getString("List.Now") : this.timeAgo(this.plugin.getString("Player." + targetId + ".LastOnline"), lang);
		String note = this.normalizeNote(this.plugin.getFriendCustomPrefix(viewer.getUniqueId(), targetId));
		String hoverKey;
		if (visible) hoverKey = note.isEmpty() ? "List.Hover-Online" : "List.Hover-Online-With-Note";
		else hoverKey = note.isEmpty() ? "List.Hover-Offline" : "List.Hover-Offline-With-Note";
		String hover = lang.format(hoverKey,
				"targetDisplayName", displayName,
				"targetName", this.plugin.getFriendManager().getPlayer(targetId).getName(),
				"note", note,
				"targetUUID", targetId.toString(),
				"status", status,
				"server", server,
				"friendSince", this.formatTime(this.plugin.getFriendSince(viewer.getUniqueId(), targetId), lang),
				"lastOnlineAgo", lastOnlineAgo,
				"lastOnline", lastOnline);
		return MessageBuilder.get(lang.format(lineKey, "targetDisplayName", displayName, "server", server), null, null, hover, false);
	}

	private String normalizeNote(String note) {
		return note == null ? "" : note.trim();
	}

	private String formatTime(String raw, LanguageUtils lang) {
		try {
			long timestamp = Long.parseLong(raw);
			if (timestamp <= 0) return lang.getString("List.Unknown");
			String pattern = this.plugin.getConfigManager().getFriendListConfig().getString("Time-Format");
			if (pattern == null || pattern.isEmpty()) pattern = "yyyy-MM-dd HH:mm:ss";
			try { return new SimpleDateFormat(pattern, Locale.ROOT).format(new Date(timestamp)); }
			catch (IllegalArgumentException invalid) { return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date(timestamp)); }
		} catch (NumberFormatException invalid) { return lang.getString("List.Unknown"); }
	}

	private String timeAgo(String raw, LanguageUtils lang) {
		try {
			long timestamp = Long.parseLong(raw);
			if (timestamp <= 0) return lang.getString("List.Unknown");
			long seconds = Math.max(0, (System.currentTimeMillis() - timestamp) / 1000);
			String unit = "Seconds";
			long amount = seconds;
			if (seconds >= 31536000) { unit = "Years"; amount = seconds / 31536000; }
			else if (seconds >= 2592000) { unit = "Months"; amount = seconds / 2592000; }
			else if (seconds >= 86400) { unit = "Days"; amount = seconds / 86400; }
			else if (seconds >= 3600) { unit = "Hours"; amount = seconds / 3600; }
			else if (seconds >= 60) { unit = "Minutes"; amount = seconds / 60; }
			return lang.format("List.TimeAgo." + unit, "time", String.valueOf(amount));
		} catch (NumberFormatException invalid) { return lang.getString("List.Unknown"); }
	}

	public void add(ProxiedPlayer player, FriendUtils myFriends, LanguageUtils langUtils, String name) {
		UUID target = this.plugin.getPlayerManager().getPlayerUniqueId(name);
		if (target == null) {
			this.plugin.infoFramed(player, langUtils.format("Cant-Find", "name", name), MessageFrame.MessageId.PLAYER_NOT_FOUND);
			return;
		}
		FriendUtils targetFriends = this.plugin.getFriendManager().getPlayer(target);
		LanguageUtils targetLangUtils = this.plugin.getLanguageManager().getPlayer(target);

		try {
			myFriends.addRequest(target);
		} catch (FailedAddingException e) {
			this.plugin.infoFramed(player, langUtils.getString("Exceptions.AlreadySent"), MessageFrame.MessageId.FRIEND_ADD_ALREADY_SENT);
			return;
		} catch (AlreadyAddedException e) {
			this.plugin.infoFramed(player, langUtils.getString("Exceptions.AlreadyFriend"), MessageFrame.MessageId.FRIEND_ADD_ALREADY_FRIEND);
			return;
		} catch (SelfException e) {
			this.plugin.infoFramed(player, langUtils.getString("Exceptions.Cant-Add-Yourself"), MessageFrame.MessageId.FRIEND_ADD_SELF);
			return;
		} catch (IgnoredException e) {
			this.plugin.infoFramed(player, langUtils.getString("Exceptions.Ignored"), MessageFrame.MessageId.FRIEND_ADD_IGNORED);
			return;
		} catch (FriendSlotLimitException e) {
			this.plugin.infoFramed(player, langUtils.getString("Exceptions.SlotLimitReached"), MessageFrame.MessageId.FRIEND_SLOT_LIMIT);
			return;
		} catch (RequestDenyException e) {
			this.plugin.infoFramed(player, langUtils.getString("Exceptions.RequestDeny"), MessageFrame.MessageId.FRIEND_REQUEST_DENIED);
			return;
		}

		this.plugin.infoFramed(player, langUtils.format("Add.Sent", "targetDisplayName", targetFriends.getDisplayName()),
				MessageFrame.MessageId.FRIEND_REQUEST_SENT);

		TextComponent grayHyphen = MessageBuilder.get("&r &8- &r", null, ChatColor.DARK_GRAY, null, false);

		TextComponent accept = MessageBuilder.get(targetLangUtils.getString("Add.Accept.Prefix"), "/friend accept " + myFriends.getName(), ChatColor.GREEN, targetLangUtils.getString("Add.Accept.Message"), true);
		TextComponent deny = MessageBuilder.get(targetLangUtils.getString("Add.Deny.Prefix"), "/friend deny " + myFriends.getName(), ChatColor.GREEN, targetLangUtils.getString("Add.Deny.Message"), true);
		TextComponent ignore = MessageBuilder.get(targetLangUtils.getString("Add.Ignore.Prefix"), "/friend ignore add " + myFriends.getName(), ChatColor.GREEN, targetLangUtils.getString("Add.Ignore.Message"), true);

		TextComponent received = MessageBuilder.get(targetLangUtils.format("Add.Request.Received", "displayName", myFriends.getDisplayName()));
		this.plugin.infoFramed(target, MessageBuilder.append(received, accept, grayHyphen, deny, grayHyphen, ignore),
				MessageFrame.MessageId.FRIEND_REQUEST_RECEIVED);

		try {
			this.plugin.scheduleExpiry("friend:" + myFriends.getUniqueId() + ":" + targetFriends.getUniqueId(), new Runnable() {
				@Override
				public void run() {
					try {
						myFriends.removeRequest(target);
					} catch (NotAddedException e) {
						return;
					}
					plugin.infoFramed(player, langUtils.format("Add.Expired.YourSelf", "targetDisplayName", targetFriends.getDisplayName()),
							MessageFrame.MessageId.FRIEND_REQUEST_EXPIRED_SELF);
					plugin.infoFramed(target, targetLangUtils.format("Add.Expired.Target", "displayName", myFriends.getDisplayName()),
							MessageFrame.MessageId.FRIEND_REQUEST_EXPIRED_TARGET);
				}
			}, 5, TimeUnit.MINUTES);
		} catch (IllegalStateException rejected) {
			try { myFriends.removeRequest(target); } catch (NotAddedException ignored) { }
			throw rejected;
		}
	}

	/**
	 * @author seijikohara
	 * @url https://qiita.com/seijikohara/items/ae3c428d7a7f6f013c0a
	 */
	public <T> List<List<T>> divide(List<T> original, int size) {
		if (original == null || original.isEmpty() || size <= 0) {
			return Collections.emptyList();
		}

		try {
			int block = original.size() / size + (original.size() % size > 0 ? 1 : 0);

			return IntStream.range(0, block).boxed().map(i -> {
				int start = i * size;
				int end = Math.min(start + size, original.size());
				return original.subList(start, end);
			}).collect(Collectors.toList());
		} catch (Exception e) {
			return null;
		}
	}

}
