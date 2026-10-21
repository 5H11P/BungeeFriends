package net.simplyrin.bungeefriends.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.config.Configuration;
import net.simplyrin.bungeefriends.Main;
import net.simplyrin.bungeefriends.exceptions.AlreadyAddedException;
import net.simplyrin.bungeefriends.exceptions.FailedAddingException;
import net.simplyrin.bungeefriends.exceptions.FriendSlotLimitException;
import net.simplyrin.bungeefriends.exceptions.IgnoredException;
import net.simplyrin.bungeefriends.exceptions.NotAddedException;
import net.simplyrin.bungeefriends.exceptions.RequestDenyException;
import net.simplyrin.bungeefriends.exceptions.SelfException;
import net.simplyrin.bungeefriends.messages.Permissions;
import net.simplyrin.bungeefriends.tools.ThreadPool;

/**
 * Created by SimplyRin on 2018/07/03.
 *
 * author: SimplyRin
 * license: LGPL v3
 * copyright: Copyright (c) 2021 SimplyRin
 */
public class FriendManager {

	private Main plugin;
	private final ConcurrentHashMap<UUID, PrefixCacheEntry> offlinePrefixCache = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<UUID, Boolean> offlinePrefixRefresh = new ConcurrentHashMap<>();

	private static final class PrefixCacheEntry {
		final String prefix;
		final long expiresAt;
		PrefixCacheEntry(String prefix, long expiresAt) {
			this.prefix = prefix;
			this.expiresAt = expiresAt;
		}
	}

	public FriendManager(Main plugin) {
		this.plugin = plugin;
	}

	public FriendUtils getPlayer(ProxiedPlayer player) {
		return new FriendUtils(player.getUniqueId());
	}

	public FriendUtils getPlayer(String uuid) {
		return new FriendUtils(UUID.fromString(uuid));
	}

	public FriendUtils getPlayer(UUID uniqueId) {
		return new FriendUtils(uniqueId);
	}

	public class FriendUtils {

		private UUID uuid;

		public FriendUtils(UUID uuid) {
			this.uuid = uuid;

			ProxiedPlayer player = plugin.getProxy().getPlayer(this.uuid);
			if (player != null) {
				plugin.set("Name." + player.getName().toLowerCase(), player.getUniqueId().toString());
				plugin.set("UUID." + player.getUniqueId().toString(), player.getName().toLowerCase());

				// plugin.getPlayerManager().getConfig().set("Local." + this.uuid.toString() + ".OfflinePlayer", player);
			}

			Object object = plugin.getString("Player." + this.uuid.toString() + ".Name");
			if ((object == null || object.equals("")) && player != null) {
				plugin.info("Creating data for player " + player.getName() + "...");

				plugin.set("Name." + player.getName().toLowerCase(), player.getUniqueId().toString());
				plugin.set("UUID." + player.getUniqueId().toString(), player.getName().toLowerCase());

				plugin.set("Player." + this.uuid.toString() + ".Name", player.getName());
				plugin.set("Player." + this.uuid.toString() + ".Language", plugin.getConfigManager().getConfig().getString("Plugin.Default-Language"));
				plugin.set("Player." + this.uuid.toString() + ".Prefix", "&7");
				plugin.set("Player." + this.uuid.toString() + ".Toggle", true);
				plugin.set("Player." + this.uuid.toString() + ".Friends", "[]");
			}
		}

		public ProxiedPlayer getPlayer() {
			return plugin.getProxy().getPlayer(this.uuid);
		}

		public String getDisplayName() {
			return this.getPrefix() + this.getName();
		}

		public String getDisplayNameFor(UUID viewer) {
			String personal = plugin.getFriendCustomPrefix(viewer, this.uuid);
			return (personal == null || personal.isEmpty() ? this.getPrefix() : personal) + this.getName();
		}

		public String getPrefixColor() {
			String prefix = this.getPrefix();
			if (prefix.length() >= 2) {
				prefix = prefix.substring(0, 2);
			}
			return prefix;
		}

		public String getName() {
			String value = plugin.getString("Player." + this.uuid.toString() + ".Name");
			if (value == null || value.trim().length() == 0) {
				value = plugin.getString("UUID." + this.uuid.toString());
			}
			return value;
		}

		public boolean isEnabledReceiveRequest() {
			return plugin.getBoolean("Player." + this.uuid.toString() + ".Toggle");
		}

		public FriendUtils setPrefix(String prefix) {
			plugin.set("Player." + this.uuid.toString() + ".Prefix", prefix);
			return this;
		}

		public String getPrefix() {
			ProxiedPlayer player = this.getPlayer();

			LuckPerms api = plugin.getLuckPermsApi();
			if (api != null) {
				String prefix = null;
				if (player == null) {
					long now = System.currentTimeMillis();
					PrefixCacheEntry cached = offlinePrefixCache.get(this.uuid);
					if (cached != null && cached.expiresAt > now) prefix = cached.prefix;
					else {
						prefix = plugin.getString("Player." + this.uuid + ".Prefix");
						final UUID targetId = this.uuid;
						final int cacheTicks = plugin.getConfigManager().getConfig().getInt("Plugin.LuckPerms.Offline-Prefix-Cache-Ticks", 36000);
						final int timeoutTicks = plugin.getConfigManager().getConfig().getInt("Plugin.LuckPerms.Offline-Prefix-Load-Timeout-Ticks", 40);
						if (offlinePrefixRefresh.putIfAbsent(targetId, Boolean.TRUE) == null) {
							try {
								ThreadPool.run(() -> {
									try {
										String loaded = api.getUserManager().loadUser(targetId).get(TickDuration.toMillis(timeoutTicks), TimeUnit.MILLISECONDS)
												.getCachedData().getMetaData().getPrefix();
										if (loaded != null) {
											plugin.set("Player." + targetId + ".Prefix", loaded);
										if (cacheTicks > 0) offlinePrefixCache.put(targetId,
												new PrefixCacheEntry(loaded, System.currentTimeMillis() + TickDuration.toMillis(cacheTicks)));
										}
									} catch (Exception ignored) {
									} finally {
										offlinePrefixRefresh.remove(targetId);
									}
								});
							} catch (RuntimeException e) {
								offlinePrefixRefresh.remove(targetId);
							}
						}
					}
				} else {
					CachedMetaData meta = api.getPlayerAdapter(ProxiedPlayer.class).getMetaData(player);
					prefix = meta.getPrefix();
				}

				if (prefix != null) {
					plugin.set("Player." + this.uuid.toString() + ".Prefix", prefix);
					return this.buildPrefix(prefix);
				}
			}

			if (player != null) {
				Configuration section = null;
				try {
					section = plugin.getPrefixManager().getConfig().getSection("List");
				} catch (Exception e) {
				}
				if (section != null) {
					Collection<String> collection = section.getKeys();
					for (String list : collection) {
						String prefix = plugin.getPrefixManager().getConfig().getString("List." + list + ".Prefix");
						String permission = plugin.getPrefixManager().getConfig().getString("List." + list + ".Permission");

						if (player.hasPermission(permission) ) {
							plugin.set("Player." + this.uuid.toString() + ".Prefix", prefix);
							return this.buildPrefix(prefix);
						}
					}
				}
			}

			String prefix = plugin.getString("Player." + this.uuid.toString() + ".Prefix");
			return this.buildPrefix(prefix);
		}

		/**
		 * "&c" なにもない場合は
		 */
		private String buildPrefix(String prefix) {
			String strip = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', prefix));
			if (strip.length() == 0) {
				prefix = prefix.trim();
			} else if (!strip.endsWith(" ")) { // "&c[ADMIN] " や "&c[ADMIN] &r" trim
				prefix = prefix.trim() + " ";
			}
			return prefix;
		}

		public UUID getUniqueId() {
			return this.uuid;
		}

		public List<String> getRequests() {
			return plugin.getStringList("Player." + this.uuid.toString() + ".Requests");
		}

		public boolean isFriend(UUID targetUniqueId) {
			List<String> list = this.getFriends();
			return list.contains(targetUniqueId.toString());
		}

		public FriendUtils addRequest(ProxiedPlayer player) throws AlreadyAddedException, FailedAddingException, SelfException, IgnoredException, FriendSlotLimitException, RequestDenyException {
			return this.addRequest(player.getUniqueId());
		}

		public FriendUtils addRequest(UUID uuid) throws AlreadyAddedException, FailedAddingException, SelfException, IgnoredException, FriendSlotLimitException, RequestDenyException {
			if (this.uuid.toString().equals(uuid.toString())) {
				throw new SelfException();
			}

			List<String> list = this.getFriends();
			if (list.contains(uuid.toString())) {
				throw new AlreadyAddedException();
			}

			ProxiedPlayer player = this.getPlayer();
			if (player != null) {
				if (this.getPlayer().hasPermission("friends.limit." + this.getFriends().size())
						&& !this.getPlayer().hasPermission(Permissions.ADMIN)
						&& !this.getPlayer().hasPermission(Permissions.LIMIT_BYPASS)) {
					throw new FriendSlotLimitException();
				}
			}

			FriendUtils targetFriendUtils = FriendManager.this.getPlayer(uuid);
			List<String> ignoreList = targetFriendUtils.getIgnoreList();
			if (ignoreList.contains(this.uuid.toString())) {
				throw new IgnoredException();
			}

			if (!targetFriendUtils.isEnabledReceiveRequest()) {
				throw new RequestDenyException();
			}

			List<String> requests = plugin.getStringList("Player." + this.uuid.toString() + ".Requests");
			if (requests.contains(uuid.toString())) {
				throw new FailedAddingException();
			}
			plugin.changeFriendRequest(this.uuid, uuid, true);
			return this;
		}

		public FriendUtils removeRequest(ProxiedPlayer player) throws NotAddedException {
			return this.removeRequest(player.getUniqueId());
		}

		public FriendUtils removeRequest(UUID uuid) throws NotAddedException {
			List<String> requests = plugin.getStringList("Player." + this.uuid.toString() + ".Requests");
			if (!requests.contains(uuid.toString())) {
				throw new NotAddedException();
			}
			plugin.changeFriendRequest(this.uuid, uuid, false);
			plugin.cancelExpiry("friend:" + this.uuid + ":" + uuid);
			return this;
		}

		public List<String> getFriends() {
			return plugin.getStringList("Player." + this.uuid.toString() + ".Friends");
		}

		public List<UUID> getFriendsUniqueId() {
			List<UUID> list = new ArrayList<>();
			for (String uniqueId : this.getFriends()) {
				list.add(UUID.fromString(uniqueId));
			}
			return list;
		}

		public FriendUtils add(ProxiedPlayer player) throws AlreadyAddedException, FailedAddingException {
			return this.add(player.getUniqueId());
		}

		public FriendUtils add(UUID uuid) throws AlreadyAddedException, FailedAddingException {
			synchronized (FriendManager.this) {
			if (this.uuid.toString().equals(uuid.toString())) {
				throw new FailedAddingException();
			}

			List<String> list = this.getFriends();
			if (list.contains(uuid.toString())) {
				throw new AlreadyAddedException();
			}
			FriendUtils targetFriends = plugin.getFriendManager().getPlayer(uuid);
			List<String> targetList = targetFriends.getFriends();
			if (targetList.contains(this.uuid.toString())) {
				throw new AlreadyAddedException();
			}
			list.add(uuid.toString());
			targetList.add(this.uuid.toString());
			if (plugin.isEnabledMySQL()) plugin.getMySQLManager().getEditor().changeFriendship(this.uuid, uuid, true);
			else {
				synchronized (plugin.getDataManager()) {
				plugin.set("Player." + this.uuid.toString() + ".Friends", list);
				plugin.set("Player." + uuid.toString() + ".Friends", targetList);
				String since = String.valueOf(System.currentTimeMillis());
				plugin.set("Player." + this.uuid + ".FriendSince." + uuid, since);
				plugin.set("Player." + uuid + ".FriendSince." + this.uuid, since);
				plugin.getDataManager().save();
				}
			}
			return this;
			}
		}

		public FriendUtils remove(ProxiedPlayer player) throws NotAddedException, SelfException {
			return this.remove(player.getUniqueId());
		}

		public FriendUtils remove(UUID uuid) throws NotAddedException, SelfException {
			synchronized (FriendManager.this) {
			if (this.uuid.toString().equals(uuid.toString())) {
				throw new SelfException();
			}

			List<String> list = this.getFriends();
			if (!list.contains(uuid.toString())) {
				throw new NotAddedException();
			}
			FriendUtils targetFriends = plugin.getFriendManager().getPlayer(uuid);
			List<String> targetList = targetFriends.getFriends();
			if (!targetList.contains(this.uuid.toString())) {
				throw new NotAddedException();
			}
			list.remove(uuid.toString());
			targetList.remove(this.uuid.toString());
			if (plugin.isEnabledMySQL()) plugin.getMySQLManager().getEditor().changeFriendship(this.uuid, uuid, false);
			else {
				synchronized (plugin.getDataManager()) {
				plugin.set("Player." + this.uuid.toString() + ".Friends", list);
				plugin.set("Player." + uuid.toString() + ".Friends", targetList);
					plugin.getDataManager().getConfig().set("Player." + this.uuid + ".FriendSince." + uuid, null);
					plugin.getDataManager().getConfig().set("Player." + uuid + ".FriendSince." + this.uuid, null);
					plugin.getDataManager().save();
				}
			}
			return this;
			}
		}

		public List<String> getIgnoreList() {
			return plugin.getStringList("Player." + this.uuid.toString() + ".IgnoreList");
		}

		public FriendUtils addIgnore(ProxiedPlayer player) throws AlreadyAddedException {
			return this.addIgnore(player.getUniqueId());
		}

		public FriendUtils addIgnore(UUID uuid) throws AlreadyAddedException {
			synchronized (plugin.getDataManager()) {
				if (this.uuid.equals(uuid)) throw new IllegalArgumentException("A player cannot ignore themselves");
				List<String> ignoreList = plugin.getStringList("Player." + this.uuid.toString() + ".IgnoreList");
				if (ignoreList.contains(uuid.toString())) throw new AlreadyAddedException();
				ignoreList.add(uuid.toString());
				plugin.set("Player." + this.uuid.toString() + ".IgnoreList", ignoreList);
				if (!plugin.isEnabledMySQL()) plugin.getDataManager().save();
			}
			return this;
		}

		public FriendUtils removeIgnore(ProxiedPlayer player) throws NotAddedException {
			return this.removeIgnore(player.getUniqueId());
		}

		public FriendUtils removeIgnore(UUID uuid) throws NotAddedException {
			synchronized (plugin.getDataManager()) {
				List<String> ignoreList = plugin.getStringList("Player." + this.uuid.toString() + ".IgnoreList");
				if (!ignoreList.contains(uuid.toString())) throw new NotAddedException();
				ignoreList.remove(uuid.toString());
				plugin.set("Player." + this.uuid.toString() + ".IgnoreList", ignoreList);
				if (!plugin.isEnabledMySQL()) plugin.getDataManager().save();
			}
			return this;
		}

	}

}
