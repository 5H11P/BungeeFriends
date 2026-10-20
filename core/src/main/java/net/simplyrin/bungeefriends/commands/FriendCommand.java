package net.simplyrin.bungeefriends.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import lombok.Getter;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
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
	@Getter
	private List<String> availableLanguages = Collections.unmodifiableList(Arrays.asList("English", "Chinese"));

	public FriendCommand(Main plugin, String command) {
		super(command, null);
		this.plugin = plugin;
	}

	@Override
	public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
		List<String> list = new ArrayList<>();
		if (this.plugin.getBoolean("Plugin.Disable-TabComplete") || args == null || args.length == 0) return list;
		String input = args[args.length - 1] == null ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
		if (args.length == 1) {
			String[] commands = {"add", "remove", "removeall", "accept", "deny", "requests", "note", "list", "lang", "language", "ignore", "toggle", "help"};
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
						this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
						this.plugin.info(player, langUtils.getString("Cant-Find").replace("%name", args[1]));
						this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
						return;
					}
					FriendUtils targetFriends = this.plugin.getFriendManager().getPlayer(target);
					LanguageUtils targetLangUtils = this.plugin.getLanguageManager().getPlayer(target);

					try {
						myFriends.remove(target);
					} catch (NotAddedException e) {
						this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
						this.plugin.info(player, langUtils.getString("Exceptions.IsntOnYourFriends").replace("%targetDisplayName", targetFriends.getDisplayName()));
						this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
						return;
					} catch (SelfException e) {
						this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
						this.plugin.info(player, langUtils.getString("Exceptions.CantRemoveYourself"));
						this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
						return;
					}
					this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
					this.plugin.info(player, langUtils.getString("Remove.YourSelf").replace("%targetDisplayName", targetFriends.getDisplayName()));
					this.plugin.info(player, langUtils.getString(Messages.HYPHEN));

					if (this.plugin.getBoolean("Plugin.Notify-Removed-Friend")) {
						this.plugin.info(target, targetLangUtils.getString(Messages.HYPHEN));
						this.plugin.info(target, targetLangUtils.getString("Remove.Target").replace("%displayName", myFriends.getDisplayName()));
						this.plugin.info(target, targetLangUtils.getString(Messages.HYPHEN));
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
								this.plugin.info(player, langUtils.getString("Remove.All.Failed").replace("%uuid", target.toString()));
							}
						}

						this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
						this.plugin.info(player, langUtils.getString("Remove.All.Done"));
						this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
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
						this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
						this.plugin.info(player, langUtils.getString("Cant-Find").replace("%name", args[1]));
						this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
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
						this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
						this.plugin.info(player, langUtils.getString("Exceptions.NoInvited").replace("%name", targetFriends.getName()));
						this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
						return;
					}
					try {
						myFriends.add(target);
						targetFriends.removeRequest(player);
					} catch (AlreadyAddedException | FailedAddingException | NotAddedException e) {
						this.plugin.info(player, langUtils.getString("Exceptions.AlreadyFriend"));
						return;
					}

					this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
					this.plugin.info(player, langUtils.getString("Accept.YourSelf").replace("%targetDisplayName", targetFriends.getDisplayName()));
					this.plugin.info(player, langUtils.getString(Messages.HYPHEN));

					this.plugin.info(target, targetLangUtils.getString(Messages.HYPHEN));
					this.plugin.info(target, targetLangUtils.getString("Accept.Target").replace("%displayName", myFriends.getDisplayName()));
					this.plugin.info(target, targetLangUtils.getString(Messages.HYPHEN));

					return;
				}
				this.plugin.info(player, langUtils.getString("Accept.Usage"));
				return;
			}

			if (args[0].equalsIgnoreCase("deny")) {
				if (args.length > 1) {
					UUID target = this.plugin.getPlayerManager().getPlayerUniqueId(args[1]);
					if (target == null) {
						this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
						this.plugin.info(player, langUtils.getString("Cant-Find").replace("%name", args[1]));
						this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
						return;
					}
					FriendUtils targetFriends = this.plugin.getFriendManager().getPlayer(target);

					try {
						targetFriends.removeRequest(player);
					} catch (NotAddedException e) {
						this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
						this.plugin.info(player, langUtils.getString("Exceptions.HasntFriend").replace("%name", targetFriends.getName()));
						this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
						return;
					}

					this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
					this.plugin.info(player, langUtils.getString("Deny.Declined").replace("%targetDisplayName", targetFriends.getDisplayName()));
					this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
					return;
				}
				this.plugin.info(player, langUtils.getString("Deny.Usage"));
				return;
			}

			if (args[0].equalsIgnoreCase("requests")) {
				List<UUID> requesters = this.plugin.getPendingFriendRequesters(player.getUniqueId());
				this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
				if (requesters.isEmpty()) this.plugin.info(player, langUtils.getString("Requests.Empty"));
				for (UUID requester : requesters) {
					FriendUtils friend = this.plugin.getFriendManager().getPlayer(requester);
					String time = this.plugin.getFriendRequestCreatedAt(requester, player.getUniqueId());
					this.plugin.info(player, langUtils.getString("Requests.Entry")
							.replace("%name", friend.getDisplayNameFor(player.getUniqueId()))
							.replace("%time", time == null || time.isEmpty() ? "?" : time));
				}
				this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
				return;
			}

			if (args[0].equalsIgnoreCase("note")) {
				if (args.length < 2) {
					this.plugin.info(player, langUtils.getString("Note.Usage"));
					return;
				}
				if (args[1].equalsIgnoreCase("resetall")) {
					if (args.length == 3 && args[2].equalsIgnoreCase("confirm")) {
						this.plugin.clearAllFriendCustomPrefixes(player.getUniqueId());
						this.plugin.info(player, langUtils.getString("Note.Cleared"));
					} else this.plugin.info(player, langUtils.getString("Note.Confirm"));
					return;
				}
				UUID target = this.plugin.getPlayerManager().getPlayerUniqueId(args[1]);
				if (target == null || !myFriends.isFriend(target)) {
					this.plugin.info(player, langUtils.getString("Note.MustBeFriends"));
					return;
				}
				if (args.length == 2) {
					String note = this.plugin.getFriendCustomPrefix(player.getUniqueId(), target);
					this.plugin.info(player, langUtils.getString("Note.Current").replace("%prefix", note == null || note.isEmpty() ? "-" : note));
				} else if (args.length == 3 && args[2].equalsIgnoreCase("reset")) {
					this.plugin.clearFriendCustomPrefix(player.getUniqueId(), target);
					this.plugin.info(player, langUtils.getString("Note.Cleared"));
				} else {
					String note = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
					if (note.length() > 255) {
						this.plugin.info(player, langUtils.getString("Note.TooLong"));
						return;
					}
					this.plugin.setFriendCustomPrefix(player.getUniqueId(), target, note + " ");
					this.plugin.info(player, langUtils.getString("Note.Updated"));
				}
				return;
			}

			if (args[0].equalsIgnoreCase("list")) {
				List<String> list = myFriends.getFriends();

				if (list.size() == 0) {
					this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
					this.plugin.info(player, langUtils.getString("List.DontHave.One"));
					this.plugin.info(player, langUtils.getString("List.DontHave.Two"));
					this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
					return;
				}

				List<TextComponent> online = new ArrayList<>();
				List<TextComponent> offline = new ArrayList<>();

				boolean colorOnly = this.plugin.getBoolean("Plugin.FriendList-NoPrefix");

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
							String displayName = colorOnly ? targetFriends.getPrefixColor() + targetFriends.getName() : targetFriends.getDisplayNameFor(player.getUniqueId());

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

					String displayName = colorOnly ? targetFriends.getPrefixColor() + targetFriends.getName() : targetFriends.getDisplayNameFor(player.getUniqueId());

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

				this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
				player.sendMessage(PaginationMessage.build(langUtils, langUtils.getString("List.Page"), "/friend list ", page, divide.size()));
				for (TextComponent message : divide.get(page - 1)) player.sendMessage(message);

				this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
				return;
			}

			if (args[0].equalsIgnoreCase("lang") || args[0].equalsIgnoreCase("language")) {
				if (args.length > 1 && args[1].equalsIgnoreCase("update")) {
					this.plugin.info(player, langUtils.getString("Lang.Updater.Disabled"));
					return;
				}
				if (args.length > 1 && (args[1].equalsIgnoreCase("english") || args[1].equalsIgnoreCase("chinese"))) {
					langUtils.setLanguage(args[1].toLowerCase(java.util.Locale.ROOT));
					this.plugin.info(player, langUtils.getString("Lang.Update").replace("%lang", langUtils.getLanguage()));
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
								this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
								this.plugin.info(player, langUtils.getString("Cant-Find").replace("%name", args[2]));
								this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
								return;
							}
							FriendUtils targetFriends = this.plugin.getFriendManager().getPlayer(target);

							try {
								myFriends.addIgnore(target);
							} catch (AlreadyAddedException e) {
								this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
								this.plugin.info(player, langUtils.getString("Ignore.AlreadyAdded").replace("%targetDisplayName", targetFriends.getDisplayName()));
								this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
								return;
							}

							this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
							this.plugin.info(player, langUtils.getString("Ignore.Added").replace("%targetDisplayName", targetFriends.getDisplayName()));
							this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
							return;
						}

						this.plugin.info(player, langUtils.getString("Ignore.Usage.Add"));
						return;
					}

					if (args[1].equalsIgnoreCase("remove")) {
						if (args.length > 2) {
							UUID target = this.plugin.getPlayerManager().getPlayerUniqueId(args[2]);
							if (target == null) {
								this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
								this.plugin.info(player, langUtils.getString("Cant-Find").replace("%name", args[2]));
								this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
								return;
							}
							FriendUtils targetFriends = this.plugin.getFriendManager().getPlayer(target);

							try {
								myFriends.removeIgnore(target);
							} catch (NotAddedException e) {
								this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
								this.plugin.info(player, langUtils.getString("Ignore.NotAdded").replace("%targetDisplayName", targetFriends.getDisplayName()));
								this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
								return;
							}

							this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
							this.plugin.info(player, langUtils.getString("Ignore.Removed").replace("%targetDisplayName", targetFriends.getDisplayName()));
							this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
							return;
						}

						this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
						this.plugin.info(player, langUtils.getString("Ignore.Usage.Remove"));
						this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
						return;
					}

					if (args[1].equalsIgnoreCase("list")) {
						this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
						List<String> ignoreList = myFriends.getIgnoreList();
						if (ignoreList.size() == 0) {
							this.plugin.info(player, langUtils.getString("Ignore.Havent.One"));
							this.plugin.info(player, langUtils.getString("Ignore.Havent.Two"));
							this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
							return;
						}
						for (String targetUniqueId : myFriends.getIgnoreList()) {
							FriendUtils targetFriends = this.plugin.getFriendManager().getPlayer(UUID.fromString(targetUniqueId));
							this.plugin.info(player, "&e- " + targetFriends.getDisplayName());
						}
						this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
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
						this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
						this.plugin.info(player, langUtils.getString("Cant-Find").replace("%name", args[1]));
						this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
						return;
					}

					FriendUtils targetFriends = this.plugin.getFriendManager().getPlayer(target);
					LanguageUtils targetLangUtils = this.plugin.getLanguageManager().getPlayer(target);

					try {
						myFriends.add(target);
					} catch (AlreadyAddedException e) {
						this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
						this.plugin.info(player, langUtils.getString("Exceptions.AlreadyFriend"));
						this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
						return;
					} catch (FailedAddingException e) {
						this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
						this.plugin.info(player, langUtils.getString("Exceptions.CantAddYourSelf"));
						this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
						return;
					}

					this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
					this.plugin.info(player, langUtils.getString("Force-Add.YourSelf")
							.replace("%targetDisplayName", targetFriends.getDisplayName())
							.replace("%targetDisplayName%", targetFriends.getDisplayName()));
					this.plugin.info(player, langUtils.getString(Messages.HYPHEN));

					this.plugin.info(target, targetLangUtils.getString(Messages.HYPHEN));
					this.plugin.info(target, targetLangUtils.getString("Force-Add.Target")
							.replace("%displayName", myFriends.getDisplayName())
							.replace("%displayName%", myFriends.getDisplayName()));
					this.plugin.info(target, targetLangUtils.getString(Messages.HYPHEN));
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
						this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
						this.plugin.info(player, langUtils.getString("Cant-Find").replace("%name", args[1]));
						this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
						return;
					}

					FriendUtils targetFriends = this.plugin.getFriendManager().getPlayer(target);

					if (args.length > 2) {
						String prefix = "";
						for (int i = 2; i < args.length; i++) {
							prefix += args[i] + " ";
						}

						if (prefix.trim().length() <= 3) {
							prefix = prefix.trim();
						}
						String displayName = targetFriends.getDisplayName();
						targetFriends.setPrefix(prefix);
						this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
						this.plugin.info(player, langUtils.getString("Prefix.To")
								.replace("%targetDisplayName", displayName)
								.replace("%prefix", ChatColor.translateAlternateColorCodes('&', prefix.trim())));
						this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
						return;
					}

					this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
					this.plugin.info(player, langUtils.getString("Prefix.Current")
							.replace("%targetDisplayName", targetFriends.getDisplayName())
							.replace("%prefix", targetFriends.getPrefix().trim()));
					this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
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
		this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
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
		this.plugin.info(player, langUtils.getString("Help.Note"));
		this.plugin.info(player, langUtils.getString("Help.Ignore"));
		this.plugin.info(player, langUtils.getString("Help.Toggle"));
		if (player.hasPermission(Permissions.ADMIN)) {
			this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
			this.plugin.info(player, langUtils.getString("Help.Force-Add"));
			this.plugin.info(player, langUtils.getString("Help.Prefix"));
			this.plugin.info(player, langUtils.getString("Help.Reload"));
		} else if (player.hasPermission(Permissions.FORCE_ADD)) {
			this.plugin.info(player, langUtils.getString("Help.Force-Add"));
		}
		this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
	}

	private TextComponent friendListLine(ProxiedPlayer viewer, ProxiedPlayer target, LanguageUtils lang, String displayName) {
		return this.friendListLine(viewer, target, target.getUniqueId(), lang, displayName);
	}

	private TextComponent friendListLine(ProxiedPlayer viewer, ProxiedPlayer target, UUID targetId, LanguageUtils lang, String displayName) {
		boolean visible = target != null && target.getServer() != null && target.getServer().getInfo() != null;
		String server = visible ? this.plugin.getServerName(target.getServer().getInfo().getName()) : lang.getString("List.Unknown");
		String status = lang.getString("List.Unknown");
		String line = lang.getString("List.Offline");
		if (visible) {
			net.simplyrin.bungeefriends.utils.StatusManager.Status state = this.plugin.getStatusManager().getStatus(targetId);
			String key = state == net.simplyrin.bungeefriends.utils.StatusManager.Status.ONLINE ? "List.Online" : "List.Status." + state.getKey();
			line = lang.getString(key);
			status = lang.getString("Status.ChatBar." + state.getKey()).replace("%targetDisplayName", "").trim();
		}
		String lastOnline = visible ? lang.getString("List.Now") : this.formatTime(this.plugin.getString("Player." + targetId + ".LastOnline"), lang);
		String lastOnlineAgo = visible ? lang.getString("List.Now") : this.timeAgo(this.plugin.getString("Player." + targetId + ".LastOnline"), lang);
		String hover = lang.getString(visible ? "List.Hover-Online" : "List.Hover-Offline")
				.replace("%targetDisplayName", displayName).replace("%targetUUID", targetId.toString())
				.replace("%status", status).replace("%server", server)
				.replace("%friendSince", this.formatTime(this.plugin.getFriendSince(viewer.getUniqueId(), targetId), lang))
				.replace("%lastOnlineAgo", lastOnlineAgo).replace("%lastOnline", lastOnline);
		return MessageBuilder.get(line.replace("%targetDisplayName", displayName).replace("%server", server), null, null, hover, false);
	}

	private String formatTime(String raw, LanguageUtils lang) {
		try {
			long timestamp = Long.parseLong(raw);
			if (timestamp <= 0) return lang.getString("List.Unknown");
			String pattern = this.plugin.getConfigManager().getConfig().getString("Plugin.FriendList-Time.Format");
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
			return lang.getString("List.TimeAgo." + unit).replace("%time%", String.valueOf(amount));
		} catch (NumberFormatException invalid) { return lang.getString("List.Unknown"); }
	}

	public void add(ProxiedPlayer player, FriendUtils myFriends, LanguageUtils langUtils, String name) {
		UUID target = this.plugin.getPlayerManager().getPlayerUniqueId(name);
		if (target == null) {
			this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
			this.plugin.info(player, langUtils.getString("Cant-Find").replace("%name", name));
			this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
			return;
		}
		FriendUtils targetFriends = this.plugin.getFriendManager().getPlayer(target);
		LanguageUtils targetLangUtils = this.plugin.getLanguageManager().getPlayer(target);

		try {
			myFriends.addRequest(target);
		} catch (FailedAddingException e) {
			this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
			this.plugin.info(player, langUtils.getString("Exceptions.AlreadySent"));
			this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
			return;
		} catch (AlreadyAddedException e) {
			this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
			this.plugin.info(player, langUtils.getString("Exceptions.AlreadyFriend"));
			this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
			return;
		} catch (SelfException e) {
			this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
			this.plugin.info(player, langUtils.getString("Exceptions.CantAddYourSelf"));
			this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
			return;
		} catch (IgnoredException e) {
			this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
			this.plugin.info(player, langUtils.getString("Exceptions.Ignored"));
			this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
			return;
		} catch (FriendSlotLimitException e) {
			this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
			this.plugin.info(player, langUtils.getString("Exceptions.SlotLimitReached"));
			this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
			return;
		} catch (RequestDenyException e) {
			this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
			this.plugin.info(player, langUtils.getString("Exceptions.RequestDeny"));
			this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
			return;
		}

		this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
		this.plugin.info(player, langUtils.getString("Add.Sent").replace("%targetDisplayName", targetFriends.getDisplayName()));
		this.plugin.info(player, langUtils.getString("Add.5-Minutes"));
		this.plugin.info(player, langUtils.getString(Messages.HYPHEN));

		TextComponent prefix = MessageBuilder.get(this.plugin.getPrefix());
		TextComponent grayHyphen = MessageBuilder.get("&r &8- &r", null, ChatColor.DARK_GRAY, null, false);

		TextComponent accept = MessageBuilder.get(targetLangUtils.getString("Add.Accept.Prefix"), "/friend accept " + myFriends.getName(), ChatColor.GREEN, targetLangUtils.getString("Add.Accept.Message"), true);
		TextComponent deny = MessageBuilder.get(targetLangUtils.getString("Add.Deny.Prefix"), "/friend deny " + myFriends.getName(), ChatColor.GREEN, targetLangUtils.getString("Add.Deny.Message"), true);
		TextComponent ignore = MessageBuilder.get(targetLangUtils.getString("Add.Ignore.Prefix"), "/friend ignore add " + myFriends.getName(), ChatColor.GREEN, targetLangUtils.getString("Add.Ignore.Message"), true);

		this.plugin.info(target, targetLangUtils.getString(Messages.HYPHEN));
		this.plugin.info(target, targetLangUtils.getString("Add.Request.Received").replace("%displayName", myFriends.getDisplayName()));
		if (targetFriends.getPlayer() != null) {
			targetFriends.getPlayer().sendMessage(prefix, accept, grayHyphen, deny, grayHyphen, ignore);
		}
		this.plugin.info(target, targetLangUtils.getString(Messages.HYPHEN));

		try {
			this.plugin.scheduleExpiry("friend:" + myFriends.getUniqueId() + ":" + targetFriends.getUniqueId(), new Runnable() {
				@Override
				public void run() {
					try {
						myFriends.removeRequest(target);
					} catch (NotAddedException e) {
						return;
					}
					plugin.info(player, langUtils.getString(Messages.HYPHEN));
					plugin.info(player, langUtils.getString("Add.Expired.YourSelf").replace("%targetDisplayName", targetFriends.getDisplayName()));
					plugin.info(player, langUtils.getString(Messages.HYPHEN));
					plugin.info(target, targetLangUtils.getString(Messages.HYPHEN));
					plugin.info(target, targetLangUtils.getString("Add.Expired.Target").replace("%displayName", myFriends.getDisplayName()));
					plugin.info(target, targetLangUtils.getString(Messages.HYPHEN));
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
