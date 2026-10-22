package net.simplyrin.bungeefriends;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.bstats.bungeecord.Metrics;

import lombok.Getter;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.scheduler.ScheduledTask;
import net.simplyrin.bungeefriends.commands.ChatCommand;
import net.simplyrin.bungeefriends.commands.BlockCommand;
import net.simplyrin.bungeefriends.commands.AllChatCommand;
import net.simplyrin.bungeefriends.commands.PartyListCommand;
import net.simplyrin.bungeefriends.commands.FriendCommand;
import net.simplyrin.bungeefriends.commands.FriendConsoleCommand;
import net.simplyrin.bungeefriends.commands.PartyChatCommand;
import net.simplyrin.bungeefriends.commands.PartyCommand;
import net.simplyrin.bungeefriends.commands.ReplyCommand;
import net.simplyrin.bungeefriends.commands.StatusCommand;
import net.simplyrin.bungeefriends.commands.TellCommand;
import net.simplyrin.bungeefriends.commands.alias.FLCommand;
import net.simplyrin.bungeefriends.listeners.EventListener;
import net.simplyrin.bungeefriends.listeners.parties.PartyListener;
import net.simplyrin.bungeefriends.messages.MessageFrame;
import net.simplyrin.bungeefriends.messages.Messages;
import net.simplyrin.bungeefriends.tools.Priority;
import net.simplyrin.bungeefriends.tools.ThreadPool;
import net.simplyrin.bungeefriends.utils.ConfigManager;
import net.simplyrin.bungeefriends.utils.DataManager;
import net.simplyrin.bungeefriends.utils.FriendManager;
import net.simplyrin.bungeefriends.utils.LanguageManager;
import net.simplyrin.bungeefriends.utils.MessageBuilder;
import net.simplyrin.bungeefriends.utils.MySQLManager;
import net.simplyrin.bungeefriends.utils.PartyManager;
import net.simplyrin.bungeefriends.utils.PartyWarpManager;
import net.simplyrin.bungeefriends.utils.PlayerManager;
import net.simplyrin.bungeefriends.utils.StatusManager;

/**
 * Created by SimplyRin on 2018/07/03.
 *
 * author: SimplyRin
 * license: LGPL v3
 * copyright: Copyright (c) 2021 SimplyRin
 */
@Getter
public class Main extends Plugin {

	private ConfigManager configManager;
	private DataManager dataManager;
	private PlayerManager playerManager;
	private FriendManager friendManager;
	private PartyManager partyManager;
	private PartyWarpManager partyWarpManager;
	private StatusManager statusManager;
	private LanguageManager languageManager;
	private MySQLManager mySQLManager;

	private Priority priority;
	private Map<UUID, UUID> replyTargetMap;
	private final Map<String, ScheduledTask> expiryTasks = new HashMap<>();

	private boolean isEnabledMySQL;

	private FriendCommand friendCommand;
	private FriendCommand friendFCommand;
	private FriendConsoleCommand friendConsoleCommand;
	private ReplyCommand replyCommand;
	private ReplyCommand replyRCommand;
	private FLCommand flCommand;
	private PartyCommand partyCommand;
	private ChatCommand chatCommand;
	private PartyChatCommand partyChatCommand;
	private PartyChatCommand partyPChatCommand;
	private PartyChatCommand partyChatAliasCommand;
	private StatusCommand statusCommand;
	private BlockCommand blockCommand;
	private BlockCommand ignoreCommand;
	private AllChatCommand allChatCommand;
	private AllChatCommand acCommand;
	private PartyListCommand partyListCommand;

	private TellCommand tellCommand;
	private TellCommand tellMsgCommand;
	private TellCommand tellMessageCommand;
	private TellCommand tellWCommand;
	private TellCommand tellWhisperCommand;

	private EventListener eventListener;
	private PartyListener partyListener;

	private LuckPerms luckPermsApi;

	private UUID consoleUniqueId = UUID.fromString("f2304371-010c-41fd-9548-40b34325b263");

	@Override
	public void onEnable() {
		ThreadPool.start();
		this.configManager = new ConfigManager(this);
		this.dataManager = new DataManager(this);
		this.playerManager = new PlayerManager(this);
		this.friendManager = new FriendManager(this);
		this.statusManager = new StatusManager(this);
		this.partyWarpManager = new PartyWarpManager(this);

		this.mySQLManager = new MySQLManager(this);

		this.priority = new Priority(this);
		this.priority.update();

		this.reloadLanguageManager();
		try {
			MessageFrame.writeDocumentation(new File(this.getDataFolder(), "Hyphen-Guide.md"));
		} catch (IOException e) {
			this.getLogger().warning("Could not generate Hyphen-Guide.md: " + e.getMessage());
		}
		this.reloadPartyCmdSender();

		this.friendCommand = new FriendCommand(this, "friend");
		this.getProxy().getPluginManager().registerCommand(this, this.friendCommand);
		this.friendConsoleCommand = new FriendConsoleCommand(this, "friendconsole");
		this.getProxy().getPluginManager().registerCommand(this, this.friendConsoleCommand);

		this.statusCommand = new StatusCommand(this);
		this.getProxy().getPluginManager().registerCommand(this, this.statusCommand);

		this.reloadPartyFeatures();

		this.eventListener = new EventListener(this);
		this.getProxy().getPluginManager().registerListener(this, this.eventListener);

		this.replyTargetMap = new ConcurrentHashMap<>();
		this.isEnabledMySQL = this.mySQLManager.getConfig().getBoolean("Enable");

		if (this.getProxy().getPluginManager().getPlugin("LuckPerms") != null
				&& this.configManager.getConfig().getBoolean("Plugin.Enable-LuckPerms-API")) {
			try {
				this.luckPermsApi = LuckPermsProvider.get();
				this.info("&aLoaded LuckPerms API.");
			} catch (Exception e) {
				this.info("&cFailed loading LuckPerms API.");
			}
		}

		this.info("&b&m--------------------------------");
		this.info("&bBungeeFriends has been loaded.");
		String prefix = "";
		if (this.getDescription().getVersion().toLowerCase().contains("beta")) {
			prefix += "Beta ";
		}
		this.info("&b&m--------------------------------");

		BungeeFriends.getAPI().setInstance(this);

		new Metrics(this);
	}

	@Override
	public void onDisable() {
		if (this.statusCommand != null) this.statusCommand.close();
		if (this.partyWarpManager != null) this.partyWarpManager.close();
		synchronized (this.expiryTasks) {
			for (ScheduledTask task : this.expiryTasks.values()) task.cancel();
			this.expiryTasks.clear();
		}
		this.getProxy().getScheduler().cancel(this);
		boolean drained = ThreadPool.shutdown();
		this.dataManager.save();
		this.playerManager.save();

		if (this.isEnabledMySQL) {
			if (drained) this.mySQLManager.close();
			else this.getLogger().severe("Async tasks did not finish during shutdown; MySQL connection remains open to avoid losing in-flight writes");
		}
	}

	public void scheduleExpiry(String key, Runnable action, long delay, TimeUnit unit) {
		synchronized (this.expiryTasks) {
			ScheduledTask old = this.expiryTasks.remove(key);
			if (old != null) old.cancel();
			if (this.expiryTasks.size() >= 4096) throw new IllegalStateException("Too many pending expiry tasks");
			final ScheduledTask[] scheduled = new ScheduledTask[1];
			scheduled[0] = this.getProxy().getScheduler().schedule(this, () -> {
				synchronized (this.expiryTasks) {
					if (!this.expiryTasks.remove(key, scheduled[0])) return;
				}
				action.run();
			}, delay, unit);
			this.expiryTasks.put(key, scheduled[0]);
		}
	}

	public void cancelExpiry(String key) {
		synchronized (this.expiryTasks) {
			ScheduledTask task = this.expiryTasks.remove(key);
			if (task != null) task.cancel();
		}
	}

	public void reloadCommandAliases() {
		if (this.allChatCommand != null) this.getProxy().getPluginManager().unregisterCommand(this.allChatCommand);
		if (this.acCommand != null) this.getProxy().getPluginManager().unregisterCommand(this.acCommand);
		if (this.partyListCommand != null) this.getProxy().getPluginManager().unregisterCommand(this.partyListCommand);
		this.allChatCommand = null;
		this.acCommand = null;
		this.partyListCommand = null;
		if (this.partyChatCommand != null) this.getProxy().getPluginManager().unregisterCommand(this.partyChatCommand);
		if (this.partyPChatCommand != null) this.getProxy().getPluginManager().unregisterCommand(this.partyPChatCommand);
		if (this.partyChatAliasCommand != null) this.getProxy().getPluginManager().unregisterCommand(this.partyChatAliasCommand);
		this.partyChatCommand = null;
		this.partyPChatCommand = null;
		this.partyChatAliasCommand = null;
		if (this.configManager.getConfig().getBoolean("Plugin.Enable-Bungee-Parties")) {
			this.partyPChatCommand = new PartyChatCommand(this, "pchat");
			this.partyChatAliasCommand = new PartyChatCommand(this, "partychat");
			this.getProxy().getPluginManager().registerCommand(this, this.partyPChatCommand);
			this.getProxy().getPluginManager().registerCommand(this, this.partyChatAliasCommand);
			if (this.configManager.getConfig().getBoolean("Plugin.Enable-Aliases./achat")) {
				this.allChatCommand = new AllChatCommand(this, "achat");
				this.getProxy().getPluginManager().registerCommand(this, this.allChatCommand);
			}
			if (this.configManager.getConfig().getBoolean("Plugin.Enable-Aliases./ac")) {
				this.acCommand = new AllChatCommand(this, "ac");
				this.getProxy().getPluginManager().registerCommand(this, this.acCommand);
			}
			if (this.configManager.getConfig().getBoolean("Plugin.Enable-Aliases./pl")) {
				this.partyListCommand = new PartyListCommand(this);
				this.getProxy().getPluginManager().registerCommand(this, this.partyListCommand);
			}
		}
		if (this.blockCommand != null) this.getProxy().getPluginManager().unregisterCommand(this.blockCommand);
		if (this.ignoreCommand != null) this.getProxy().getPluginManager().unregisterCommand(this.ignoreCommand);
		this.blockCommand = null;
		this.ignoreCommand = null;
		if (this.configManager.getConfig().getBoolean("Plugin.Enable-Aliases./block")) {
			this.blockCommand = new BlockCommand(this, "block");
			this.getProxy().getPluginManager().registerCommand(this, this.blockCommand);
		}
		if (this.configManager.getConfig().getBoolean("Plugin.Enable-Aliases./ignore")) {
			this.ignoreCommand = new BlockCommand(this, "ignore");
			this.getProxy().getPluginManager().registerCommand(this, this.ignoreCommand);
		}
		if (this.configManager.getConfig().getBoolean("Plugin.Enable-Aliases./msg")) {
			this.tellMsgCommand = new TellCommand(this, "msg");
			this.getProxy().getPluginManager().registerCommand(this, this.tellMsgCommand);
		} else if(this.tellMsgCommand != null) {
			this.getProxy().getPluginManager().unregisterCommand(this.tellMsgCommand);
		}

		if (this.configManager.getConfig().getBoolean("Plugin.Enable-Aliases./tell")) {
			this.tellCommand = new TellCommand(this, "tell");
			this.getProxy().getPluginManager().registerCommand(this, this.tellCommand);
		} else if(this.tellCommand != null) {
			this.getProxy().getPluginManager().unregisterCommand(this.tellCommand);
		}

		if (this.configManager.getConfig().getBoolean("Plugin.Enable-Aliases./w")) {
			this.tellWCommand = new TellCommand(this, "w");
			this.getProxy().getPluginManager().registerCommand(this, this.tellWCommand);
		} else if(this.tellWCommand != null) {
			this.getProxy().getPluginManager().unregisterCommand(this.tellWCommand);
		}

		if (this.configManager.getConfig().getBoolean("Plugin.Enable-Aliases./whisper")) {
			this.tellWhisperCommand = new TellCommand(this, "whisper");
			this.getProxy().getPluginManager().registerCommand(this, this.tellWhisperCommand);
		} else if(this.tellWhisperCommand != null) {
			this.getProxy().getPluginManager().unregisterCommand(this.tellWhisperCommand);
		}

		if (this.configManager.getConfig().getBoolean("Plugin.Enable-Aliases./message")) {
			this.tellMessageCommand = new TellCommand(this, "message");
			this.getProxy().getPluginManager().registerCommand(this, this.tellMessageCommand);
		} else if(this.tellMessageCommand != null) {
			this.getProxy().getPluginManager().unregisterCommand(this.tellMessageCommand);
		}

		if (this.configManager.getConfig().getBoolean("Plugin.Enable-Aliases./f")) {
			this.friendFCommand = new FriendCommand(this, "f");
			this.getProxy().getPluginManager().registerCommand(this, this.friendFCommand);
		} else if(this.friendFCommand != null) {
			this.getProxy().getPluginManager().unregisterCommand(this.friendFCommand);
		}

		if (this.configManager.getConfig().getBoolean("Plugin.Enable-Aliases./r")) {
			this.replyRCommand = new ReplyCommand(this, "r");
			this.getProxy().getPluginManager().registerCommand(this, this.replyRCommand);
		} else if(this.replyRCommand != null) {
			this.getProxy().getPluginManager().unregisterCommand(this.replyRCommand);
		}

		if (this.configManager.getConfig().getBoolean("Plugin.Enable-Aliases./reply")) {
			this.replyCommand = new ReplyCommand(this, "reply");
			this.getProxy().getPluginManager().registerCommand(this, this.replyCommand);
		} else if(this.replyCommand != null) {
			this.getProxy().getPluginManager().unregisterCommand(this.replyCommand);
		}

		if (this.configManager.getConfig().getBoolean("Plugin.Enable-Aliases./fl")) {
			this.flCommand = new FLCommand(this);
			this.getProxy().getPluginManager().registerCommand(this, this.flCommand);
		} else if(this.flCommand != null) {
			this.getProxy().getPluginManager().unregisterCommand(this.flCommand);
		}

		if (this.configManager.getConfig().getBoolean("Plugin.Enable-Bungee-Parties")) {
			if (this.configManager.getConfig().getBoolean("Plugin.Enable-Aliases./pc")) {
				this.partyChatCommand = new PartyChatCommand(this, "pc");
				this.getProxy().getPluginManager().registerCommand(this, this.partyChatCommand);
			} else if(this.partyChatCommand != null) {
				this.getProxy().getPluginManager().unregisterCommand(this.partyChatCommand);
			}
		}
	}

	public void reloadLanguageManager() {
		this.languageManager = new LanguageManager(this);
	}

	public void reloadPartyFeatures() {
		if (!this.configManager.getConfig().getBoolean("Plugin.Enable-Bungee-Parties")) {
			if (this.partyCommand != null) this.getProxy().getPluginManager().unregisterCommand(this.partyCommand);
			if (this.chatCommand != null) this.getProxy().getPluginManager().unregisterCommand(this.chatCommand);
			if (this.partyListener != null) this.getProxy().getPluginManager().unregisterListener(this.partyListener);
			this.partyCommand = null;
			this.chatCommand = null;
			this.partyListener = null;
			this.partyManager = null;
			this.reloadCommandAliases();
			return;
		}
		if (this.partyManager == null) this.partyManager = new PartyManager(this);
		if (this.partyCommand == null) {
			this.partyCommand = new PartyCommand(this);
			this.getProxy().getPluginManager().registerCommand(this, this.partyCommand);
		}
		if (this.chatCommand == null) {
			this.chatCommand = new ChatCommand(this, "chat");
			this.getProxy().getPluginManager().registerCommand(this, this.chatCommand);
		}
		if (this.partyListener == null) {
			this.partyListener = new PartyListener(this);
			this.getProxy().getPluginManager().registerListener(this, this.partyListener);
		}
		this.reloadCommandAliases();
	}

	private volatile Map<String, String> partyCmdSenderMap = new ConcurrentHashMap<>();

	public void reloadPartyCmdSender() {
		Map<String, String> mappings = new ConcurrentHashMap<>();

		Configuration senderConfig = this.getConfigManager().getPartyCommandSenderConfig();
		for (Object value : senderConfig.getList("Mappings")) {
			if (!(value instanceof Map)) continue;
			Map<?, ?> mapping = (Map<?, ?>) value;
			Object rawLeader = mapping.get("Leader-Command");
			Object rawMember = mapping.get("Member-Command");
			if (rawLeader == null || rawMember == null) continue;
			String leader = String.valueOf(rawLeader);
			String member = String.valueOf(rawMember);

			if (!leader.equals("") && !member.equals("")) {
				mappings.put(leader, member);
			}
		}
		this.partyCmdSenderMap = mappings;
	}

	public String getServerName(String serverName) {
		if (serverName == null) return null;
		List<?> rules = this.getConfigManager().getServerNameDisplayConfig().getList("Rules");
		for (Object value : rules) {
			if (!(value instanceof Map)) continue;
			Map<?, ?> rule = (Map<?, ?>) value;
			Object rawMatch = rule.get("Match");
			Object rawDisplayName = rule.get("Display-Name");
			if (!(rawMatch instanceof Map) || rawDisplayName == null) continue;
			Map<?, ?> match = (Map<?, ?>) rawMatch;
			Object rawMode = match.get("Mode");
			Object rawPattern = match.get("Value");
			if (rawMode == null || rawPattern == null) continue;
			String mode = String.valueOf(rawMode);
			String pattern = String.valueOf(rawPattern);
			String displayName = String.valueOf(rawDisplayName);
			if (pattern.isEmpty() || displayName.isEmpty()) continue;
			if (matchesServerName(serverName, pattern, mode)) return displayName;
		}
		return serverName;
	}

	static boolean matchesServerName(String serverName, String pattern, String mode) {
		switch (mode) {
		case "PREFIX": return serverName.startsWith(pattern);
		case "CONTAINS": return serverName.contains(pattern);
		case "EXACT": return serverName.equals(pattern);
		case "EXACT_IGNORE_CASE": return serverName.equalsIgnoreCase(pattern);
		default: return false;
		}
	}

	public boolean isPartyWarpBlocked(String serverName) {
		if (serverName == null || !this.getBoolean("Plugin.Party-Warp.Enable-Server-Blocklist")) return false;
		for (String blocked : this.getStringList("Plugin.Party-Warp.Server-Blocklist")) {
			if (serverName.equalsIgnoreCase(blocked.trim())) return true;
		}
		return false;
	}

	public String getFriendCustomPrefix(UUID viewer, UUID target) {
		if (this.isEnabledMySQL) return this.mySQLManager.getEditor().getFriendPrefix(viewer, target);
		synchronized (this.dataManager) { return this.dataManager.getConfig().getString("Player." + viewer + ".FriendPrefix." + target); }
	}

	public void setFriendCustomPrefix(UUID viewer, UUID target, String prefix) {
		if (this.isEnabledMySQL) this.mySQLManager.getEditor().setFriendPrefix(viewer, target, prefix);
		else {
			synchronized (this.dataManager) {
			this.dataManager.getConfig().set("Player." + viewer + ".FriendPrefix." + target, prefix);
			this.dataManager.save();
			}
		}
	}

	public void clearFriendCustomPrefix(UUID viewer, UUID target) {
		if (this.isEnabledMySQL) this.mySQLManager.getEditor().clearFriendPrefix(viewer, target);
		else {
			synchronized (this.dataManager) {
			this.dataManager.getConfig().set("Player." + viewer + ".FriendPrefix." + target, null);
			this.dataManager.save();
			}
		}
	}

	public void clearAllFriendCustomPrefixes(UUID viewer) {
		if (this.isEnabledMySQL) this.mySQLManager.getEditor().clearAllFriendPrefixes(viewer);
		else {
			synchronized (this.dataManager) {
			this.dataManager.getConfig().set("Player." + viewer + ".FriendPrefix", null);
			this.dataManager.save();
			}
		}
	}

	public void changeFriendRequest(UUID requester, UUID target, boolean add) {
		long now = System.currentTimeMillis();
		if (this.isEnabledMySQL) {
			this.mySQLManager.getEditor().changeFriendRequest(requester, target, add, now);
		} else {
			synchronized (this.dataManager) {
			String path = "Player." + requester + ".";
			List<String> requests = new java.util.ArrayList<>(this.getStringList(path + "Requests"));
			if (add && !requests.contains(target.toString())) requests.add(target.toString());
			if (!add) requests.remove(target.toString());
			this.set(path + "Requests", requests);
			this.dataManager.getConfig().set(path + "RequestSentAt." + target,
					add ? String.valueOf(now) : null);
			this.dataManager.save();
			}
		}
	}

	public String getFriendRequestCreatedAt(UUID requester, UUID target) {
		if (this.isEnabledMySQL) return this.mySQLManager.getEditor().getFriendRequestCreatedAt(requester, target);
		synchronized (this.dataManager) { return this.dataManager.getConfig().getString("Player." + requester + ".RequestSentAt." + target); }
	}

	public String getFriendSince(UUID viewer, UUID target) {
		if (this.isEnabledMySQL) return this.mySQLManager.getEditor().getFriendCreatedAt(viewer, target);
		synchronized (this.dataManager) { return this.dataManager.getConfig().getString("Player." + viewer + ".FriendSince." + target); }
	}

	public List<UUID> getPendingFriendRequesters(UUID target) {
		List<UUID> result = new java.util.ArrayList<>();
		if (this.isEnabledMySQL) result.addAll(this.mySQLManager.getEditor().getFriendRequesters(target));
		else {
			java.util.List<String> requesters;
			synchronized (this.dataManager) {
				requesters = this.dataManager.getConfig().contains("Player")
						? new java.util.ArrayList<>(this.dataManager.getConfig().getSection("Player").getKeys())
						: java.util.Collections.emptyList();
			}
			for (String requester : requesters) {
				try {
					UUID id = UUID.fromString(requester);
					if (this.getStringList("Player." + id + ".Requests").contains(target.toString())) result.add(id);
				} catch (IllegalArgumentException ignored) { }
			}
		}
		result.removeIf(requester -> {
			String sent = this.getFriendRequestCreatedAt(requester, target);
			long at;
			try { at = Long.parseLong(sent); } catch (NumberFormatException e) { at = 0; }
			if (at > 0 && System.currentTimeMillis() - at <= TimeUnit.MINUTES.toMillis(5)) return false;
			this.changeFriendRequest(requester, target, false);
			this.cancelExpiry("friend:" + requester + ":" + target);
			return true;
		});
		return result;
	}

	public void changePartyRequest(UUID owner, UUID target, boolean add) {
		if (this.isEnabledMySQL) this.mySQLManager.getEditor().changePartyRequest(owner, target, add);
		else {
			synchronized (this.dataManager) {
			String path = "Parties." + owner + ".";
			List<String> requests = new java.util.ArrayList<>(this.getStringList(path + "Requests"));
			if (add && !requests.contains(target.toString())) requests.add(target.toString());
			if (!add) requests.remove(target.toString());
			this.set(path + "Requests", requests);
			this.dataManager.getConfig().set(path + "RequestSentAt." + target,
					add ? String.valueOf(System.currentTimeMillis()) : null);
			this.dataManager.save();
			}
		}
	}

	public String getPartyRequestCreatedAt(UUID owner, UUID target) {
		if (this.isEnabledMySQL) return this.mySQLManager.getEditor().getPartyRequestCreatedAt(owner, target);
		synchronized (this.dataManager) { return this.dataManager.getConfig().getString("Parties." + owner + ".RequestSentAt." + target); }
	}

	public String getPrefix() {
		return this.configManager.getConfig().getString("Plugin.Prefix");
	}

	public String getString(String key) {
		if (this.isEnabledMySQL && (key.startsWith("Player.") || key.startsWith("Parties.") || key.startsWith("Name.") || key.startsWith("UUID."))) {
			if (this.getMySQLManager().isDebugMode()) {
				System.out.println("Checking key: " + key);
			}
			return this.getMySQLManager().getEditor().get(key);
		}
		if (key.startsWith("Player.") || key.startsWith("Parties.")) {
			synchronized (this.dataManager) { return this.dataManager.getConfig().getString(key); }
		}
		if (key.startsWith("Name.") || key.startsWith("UUID.")) {
			return this.getPlayerManager().getConfig().getString(key);
		}
		return this.getConfigManager().getConfig().getString(key);
	}

	public List<String> getStringList(String key) {
		if (this.isEnabledMySQL && (key.startsWith("Player.") || key.startsWith("Parties."))) {
			if (this.getMySQLManager().isDebugMode()) {
				System.out.println("Checking key: " + key);
			}
			return this.getMySQLManager().getEditor().getList(key);
		}
		if (key.startsWith("Player.") || key.startsWith("Parties.")) {
			synchronized (this.dataManager) { return this.dataManager.getConfig().getStringList(key); }
		}
		if (key.startsWith("Name.") || key.startsWith("UUID.")) {
			return this.getPlayerManager().getConfig().getStringList(key);
		}
		return this.getConfigManager().getConfig().getStringList(key);
	}

	public boolean getBoolean(String key) {
		if (this.isEnabledMySQL && (key.startsWith("Player.") || key.startsWith("Parties."))) {
			if (this.getMySQLManager().isDebugMode()) {
				System.out.println("Checking key: " + key);
			}
			return Boolean.valueOf(this.getMySQLManager().getEditor().get(key));
			// return Boolean.valueOf(this.sqlCache(key));
		}
		if (key.startsWith("Player.") || key.startsWith("Parties.")) {
			synchronized (this.dataManager) { return this.dataManager.getConfig().getBoolean(key); }
		}
		if (key.startsWith("Name.") || key.startsWith("UUID.")) {
			return Boolean.valueOf(this.getPlayerManager().getConfig().getBoolean(key));
		}
		return Boolean.valueOf(this.getConfigManager().getConfig().getBoolean(key));
	}

	public void set(String key, List<String> list) {
		if (this.isEnabledMySQL && (key.startsWith("Player.") || key.startsWith("Parties."))) {
			if (this.getMySQLManager().isDebugMode()) {
				System.out.println("Setting key: " + key);
			}
			this.getMySQLManager().getEditor().set(key, list);
		} else if (key.startsWith("Player.") || key.startsWith("Parties.")) {
			synchronized (this.dataManager) { this.dataManager.getConfig().set(key, list); }
		} else if (key.startsWith("Name.") || key.startsWith("UUID.")) {
			this.getPlayerManager().getConfig().set(key, list);
		} else {
			this.getConfigManager().getConfig().set(key, list);
		}
	}

	public void set(String key, String value) {
		if (this.isEnabledMySQL && (key.startsWith("Player.") || key.startsWith("Parties."))) {
			if (this.getMySQLManager().isDebugMode()) {
				System.out.println("Setting key: " + key);
			}
			this.getMySQLManager().getEditor().set(key, value);
		}
		else if (this.isEnabledMySQL && (key.startsWith("Name.") || key.startsWith("UUID."))) {
			// Both lookup keys are derived from Player.<uuid>.Name.
		}
		else if (key.startsWith("Player.") || key.startsWith("Parties.")) {
			synchronized (this.dataManager) { this.dataManager.getConfig().set(key, value); }
		}
		else if (key.startsWith("Name.") || key.startsWith("UUID.")) {
			this.getPlayerManager().getConfig().set(key, value);
		}
		else {
			this.getConfigManager().getConfig().set(key, value);
		}
	}

	public void set(String key, boolean value) {
		if (this.isEnabledMySQL && (key.startsWith("Player.") || key.startsWith("Parties."))) {
			if (this.getMySQLManager().isDebugMode()) {
				System.out.println("Setting key: " + key);
			}
			this.getMySQLManager().getEditor().set(key, String.valueOf(value));
		} else if (key.startsWith("Player.") || key.startsWith("Parties.")) {
			synchronized (this.dataManager) { this.dataManager.getConfig().set(key, value); }
		} else if (key.startsWith("Name.") || key.startsWith("UUID.")) {
			this.getPlayerManager().getConfig().set(key, value);
		} else {
			this.getConfigManager().getConfig().set(key, value);
		}
	}

	@SuppressWarnings("deprecation")
	public void info(String args) {
		for (String line : MessageBuilder.splitLines(args)) {
			this.getProxy().getConsole().sendMessage(ChatColor.translateAlternateColorCodes('&', "&7[&cBungeeFriends&7] &r" + line));
		}
	}

	public void info(String player, String args) {
		if (args == null || args.equals("")) {
			return;
		}
		this.info(this.getProxy().getPlayer(player), args);
	}

	@SuppressWarnings("deprecation")
	public void info(ProxiedPlayer player, String args) {
		if (player == null || args == null || args.equals("")) {
			return;
		}
		for (String line : MessageBuilder.splitLines(args)) {
			player.sendMessage(ChatColor.translateAlternateColorCodes('&', this.getPrefix() + line));
		}
	}

	@SuppressWarnings("deprecation")
	public void info(UUID uuid, String args) {
		if (uuid == null || args == null || args.equals("")) {
			return;
		}
		this.info(this.getProxy().getPlayer(uuid), args);
	}

	public void infoFramed(ProxiedPlayer player, String message, MessageFrame.MessageId messageId) {
		if (player == null || message == null || message.isEmpty() || messageId == null) return;
		this.infoFrame(player, messageId);
		this.info(player, message);
		this.infoFrame(player, messageId);
	}

	public void infoFrame(ProxiedPlayer player, MessageFrame.MessageId messageId) {
		if (!this.shouldFrame(player, messageId)) return;
		this.info(player, this.getLanguageManager().getPlayer(player).getString(Messages.HYPHEN));
	}

	public void infoFrame(UUID uuid, MessageFrame.MessageId messageId) {
		if (uuid == null) return;
		this.infoFrame(this.getProxy().getPlayer(uuid), messageId);
	}

	public void infoFrameConsole(MessageFrame.MessageId messageId) {
		if (!this.shouldFrame(messageId)) return;
		LanguageManager.LanguageUtils language = this.getLanguageManager().getPlayer(this.getConsoleUniqueId());
		this.info(language.getString(Messages.HYPHEN));
	}

	public void infoFramedConsole(String message, MessageFrame.MessageId messageId) {
		if (message == null || message.isEmpty()) return;
		this.infoFrameConsole(messageId);
		this.info(message);
		this.infoFrameConsole(messageId);
	}

	private boolean shouldFrame(ProxiedPlayer player, MessageFrame.MessageId messageId) {
		return player != null && this.shouldFrame(messageId);
	}

	private boolean shouldFrame(MessageFrame.MessageId messageId) {
		Configuration hyphen = this.getConfigManager().getHyphenConfig();
		if (messageId == null || !hyphen.getBoolean("Enable", true)) return false;
		String categoryPath = "Categories.Enable-" + messageId.getCategory().configKey();
		boolean categoryEnabled = hyphen.getBoolean(categoryPath, true);
		String overridePath = "Messages." + messageId.name();
		Boolean override = hyphen.contains(overridePath) ? Boolean.valueOf(hyphen.getBoolean(overridePath)) : null;
		return MessageFrame.shouldFrame(false, categoryEnabled, override);
 	}

	public void infoFramed(UUID uuid, String message, MessageFrame.MessageId messageId) {
		if (uuid == null) return;
		this.infoFramed(this.getProxy().getPlayer(uuid), message, messageId);
	}

	public void infoFramed(UUID uuid, TextComponent message, MessageFrame.MessageId messageId) {
		if (uuid == null) return;
		this.infoFramed(this.getProxy().getPlayer(uuid), message, messageId);
	}

	public void infoFramed(ProxiedPlayer player, TextComponent message, MessageFrame.MessageId messageId) {
		if (player == null || message == null || message.toPlainText().isEmpty() || messageId == null) return;
		this.infoFrame(player, messageId);
		this.info(player, message);
		this.infoFrame(player, messageId);
	}

	public void info(ProxiedPlayer player, TextComponent args) {
		if (player == null || args == null || args.toPlainText().equals("")) {
			return;
		}
		for (TextComponent line : MessageBuilder.splitLines(args)) {
			player.sendMessage(MessageBuilder.get(this.getPrefix()), line);
		}
	}

}
