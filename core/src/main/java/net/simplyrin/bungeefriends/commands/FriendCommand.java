package net.simplyrin.bungeefriends.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

	private Map<String, String> previousCommand = new HashMap<>();
	private Map<String, Integer> previousLength = new HashMap<>();

	@Override
	public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
		List<String> list = new ArrayList<>();
		if (this.plugin.getBoolean("Plugin.Disable-TabComplete")) {
			return list;
		}

		String name = args[args.length - 1];
		// System.out.println(args.length + ": " + name);
		if (args.length == 1) {
			list.addAll(Arrays.asList("add", "remove", "accept", "deny", "requests", "note", "lang", "language", "toggle", "help"));
			if (sender.hasPermission(Permissions.ADMIN)) {
				list.addAll(Arrays.asList("force-add", "prefix", "reload"));
			} else if (sender.hasPermission(Permissions.FORCE_ADD)) {
				list.add("force-add");
			}
		}

		if (args.length >= 2) {
			if (this.previousLength != null
					&& this.previousLength.get(sender.getName()) != null
					&& this.previousLength.get(sender.getName()) == 1
					&& this.previousCommand != null
					&& this.previousCommand.get(sender.getName()) != null
					&& (this.previousCommand.get(sender.getName()).equalsIgnoreCase("lang")
							|| this.previousCommand.get(sender.getName()).equalsIgnoreCase("language"))) {
				list.addAll(Arrays.asList("english", "chinese"));
			}
		}

		if (name != null) {
			for (ProxiedPlayer player : this.plugin.getProxy().getPlayers()) {
				if (player.getName().toLowerCase().startsWith(name.toLowerCase())) {
					list.add(player.getName());
				}
			}
		}

		if (args.length < 2) {
			this.previousLength.put(sender.getName(), args.length);
			this.previousCommand.put(sender.getName(), name);
		}
		return list;
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

					this.plugin.info(target, targetLangUtils.getString(Messages.HYPHEN));
					this.plugin.info(target, targetLangUtils.getString("Remove.Target").replace("%displayName", myFriends.getDisplayName()));
					this.plugin.info(target, targetLangUtils.getString(Messages.HYPHEN));
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

				List<String> online = new ArrayList<>();
				List<String> offline = new ArrayList<>();

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
								online.add(this.friendListLine(target, langUtils, displayName));
							} else {
								offline.add(langUtils.getString("List.Offline").replace("%targetDisplayName", displayName));
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
						online.add(this.friendListLine(target, langUtils, displayName));
					} else {
						offline.add(langUtils.getString("List.Offline").replace("%targetDisplayName", displayName));
					}
				}

				List<String> all = new ArrayList<>();
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

				if (all.size() <= 7) {
					this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
					this.plugin.info(player, "               " + langUtils.getString("List.Page").replace("%%currentPage%%", String.valueOf(page == 0 ? 1 : page)).replace("%%maxPage%%", "1"));

					for (String message : all) {
						this.plugin.info(player, message);
					}

					this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
					return;
				}

				List<List<String>> divide = this.divide(all, 7);
				page = Math.min(page, divide.size());

				this.plugin.info(player, langUtils.getString(Messages.HYPHEN));
				this.plugin.info(player, "               " + langUtils.getString("List.Page").replace("%%currentPage%%", String.valueOf(page == 0 ? 1 : page)).replace("%%maxPage%%", String.valueOf(divide.size())));

				try {
					List<String> pages = divide.get(page - 1);
					if (pages != null && pages.size() >= 1) {
						for (String message : pages) {
							this.plugin.info(player, message);
						}
					}
				} catch (Exception e) {
				}

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

	private String friendListLine(ProxiedPlayer target, LanguageUtils lang, String displayName) {
		if (target.getServer() == null || target.getServer().getInfo() == null)
			return lang.getString("List.Offline").replace("%targetDisplayName", displayName);
		String server = this.plugin.getServerName(target.getServer().getInfo().getName());
		net.simplyrin.bungeefriends.utils.StatusManager.Status status =
				this.plugin.getStatusManager().getStatus(target.getUniqueId());
		String key = status == net.simplyrin.bungeefriends.utils.StatusManager.Status.ONLINE
				? "List.Online" : "List.Status." + status.getKey();
		return lang.getString(key).replace("%targetDisplayName", displayName).replace("%server", server);
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
