package net.simplyrin.bungeefriends.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.common.base.Charsets;

import lombok.Getter;
import net.md_5.bungee.config.Configuration;
import net.simplyrin.bungeefriends.Main;
import net.simplyrin.bungeefriends.messages.MessageFrame;
import net.simplyrin.bungeefriends.tools.Config;

/**
 * Created by SimplyRin on 2018/07/03.
 *
 * author: SimplyRin
 * license: LGPL v3
 * copyright: Copyright (c) 2021 SimplyRin
 */
public class ConfigManager {

	private Main plugin;
	@Getter
	private Runnable runnable;
	@Getter
	private Configuration config;
	@Getter
	private Configuration hyphenConfig;
	@Getter
	private Configuration serverNameDisplayConfig;
	@Getter
	private Configuration partyCommandSenderConfig;
	@Getter
	private Configuration friendListConfig;

	public ConfigManager(Main plugin) {
		this.plugin = plugin;

		this.createConfig();
		this.save();
	}

	public void save() {
		File config = new File(this.plugin.getDataFolder(), "config.yml");
		Config.saveConfig(this.config, config);
		Config.saveConfig(this.hyphenConfig, new File(this.plugin.getDataFolder(), "hyphen.yml"));
		Config.saveConfig(this.serverNameDisplayConfig, new File(this.plugin.getDataFolder(), "server-name-display.yml"));
		Config.saveConfig(this.partyCommandSenderConfig, new File(this.plugin.getDataFolder(), "party-command-sender.yml"));
		Config.saveConfig(this.friendListConfig, new File(this.plugin.getDataFolder(), "friend-list.yml"));
	}

	public void reload() {
		if (this.plugin.getPartyWarpManager() != null) this.plugin.getPartyWarpManager().cancelAll();
		File config = new File(this.plugin.getDataFolder(), "config.yml");
		this.config = Config.getConfig(config, Charsets.UTF_8);
		this.hyphenConfig = loadOrCreateConfig(new File(this.plugin.getDataFolder(), "hyphen.yml"), "hyphen.yml");
		this.serverNameDisplayConfig = loadOrCreateConfig(new File(this.plugin.getDataFolder(), "server-name-display.yml"), "server-name-display.yml");
		this.partyCommandSenderConfig = loadOrCreateConfig(new File(this.plugin.getDataFolder(), "party-command-sender.yml"), "party-command-sender.yml");
		this.friendListConfig = loadOrCreateConfig(new File(this.plugin.getDataFolder(), "friend-list.yml"), "friend-list.yml");
		this.ensureFeatureDefaults();
		this.save();

		this.plugin.reloadPartyFeatures();
		this.plugin.reloadPartyCmdSender();
		this.plugin.getPriority().update();
	}

	public void createConfig() {
		File config = new File(this.plugin.getDataFolder(), "config.yml");
		this.config = loadOrCreateConfig(config);
		this.hyphenConfig = loadOrCreateConfig(new File(this.plugin.getDataFolder(), "hyphen.yml"), "hyphen.yml");
		this.serverNameDisplayConfig = loadOrCreateConfig(new File(this.plugin.getDataFolder(), "server-name-display.yml"), "server-name-display.yml");
		this.partyCommandSenderConfig = loadOrCreateConfig(new File(this.plugin.getDataFolder(), "party-command-sender.yml"), "party-command-sender.yml");
		this.friendListConfig = loadOrCreateConfig(new File(this.plugin.getDataFolder(), "friend-list.yml"), "friend-list.yml");
		this.ensureFeatureDefaults();
		this.save();
	}

	static Configuration loadOrCreateConfig(File file) {
		return loadOrCreateConfig(file, "config.yml");
	}

	static Configuration loadOrCreateConfig(File file, String resourceName) {
		if (!file.exists()) {
			try {
				Files.createDirectories(file.toPath().getParent());
				try (InputStream template = ConfigManager.class.getResourceAsStream("/" + resourceName)) {
					if (template == null) throw new IllegalStateException("Bundled " + resourceName + " is missing");
					Files.copy(template, file.toPath());
				}
			} catch (IOException e) {
				throw new IllegalStateException("Cannot create " + file, e);
			}
		}
		return Config.getConfig(file, Charsets.UTF_8);
	}

	private void ensureFeatureDefaults() {
		for (String invalid : applyFeatureDefaults(this.config))
			this.plugin.getLogger().warning("Invalid " + invalid + "; using default");
		for (String invalid : applyHyphenDefaults(this.hyphenConfig))
			this.plugin.getLogger().warning("Invalid " + invalid + "; using default");
		for (String invalid : applyServerNameDisplayDefaults(this.serverNameDisplayConfig))
			this.plugin.getLogger().warning("Invalid " + invalid + "; using default");
		for (String invalid : applyPartyCommandSenderDefaults(this.partyCommandSenderConfig))
			this.plugin.getLogger().warning("Invalid " + invalid + "; using default");
		for (String invalid : applyFriendListDefaults(this.friendListConfig))
			this.plugin.getLogger().warning("Invalid " + invalid + "; using default");
	}

	static List<String> applyFeatureDefaults(Configuration config) {
		defaultValue(config, "Plugin.Prefix", "&7[&cFriends&7] &r");
		defaultValue(config, "Plugin.Default-Language", "english");
		Map<String, Boolean> aliases = defaultAliases();
		defaultValue(config, "Plugin.Enable-Aliases", aliases);
		for (Map.Entry<String, Boolean> alias : aliases.entrySet())
			defaultValue(config, "Plugin.Enable-Aliases." + alias.getKey(), true);
		defaultValue(config, "Plugin.Enable-Server-Switch-Notifier", true);
		defaultValue(config, "Plugin.Enable-Party-Warp", true);
		defaultValue(config, "Plugin.Enable-LuckPerms-API", true);
		defaultValue(config, "Plugin.Enable-Tab-Complete", true);
		defaultValue(config, "Plugin.Enable-Bungee-Parties", false);
		defaultValue(config, "Plugin.Enable-Status", true);
		defaultValue(config, "Plugin.Enable-Legacy-Prefix-YML", false);
		defaultValue(config, "Plugin.Enable-Friend-Removal-Notifications", true);
		defaultValue(config, "Plugin.Party-Warp.Enable-Confirmation", false);
		defaultValue(config, "Plugin.Party-Warp.Enable-Server-Blocklist", true);
		defaultValue(config, "Plugin.Party.Max-Size", 10);
		defaultValue(config, "Plugin.Party.Disband-Delay-Ticks", 6000);
		defaultValue(config, "Plugin.Party.Offline-Member-Kick-Delay-Ticks", 6000);
		defaultValue(config, "Plugin.LuckPerms.Offline-Prefix-Cache-Ticks", 36000);
		defaultValue(config, "Plugin.LuckPerms.Offline-Prefix-Load-Timeout-Ticks", 40);
		defaultValue(config, "Plugin.Party-Warp.Confirm-Timeout-Ticks", 200);
		defaultValue(config, "Plugin.Party-Warp.Initial-Delay-Ticks", 10);
		defaultValue(config, "Plugin.Party-Warp.Member-Interval-Ticks", 2);
		defaultValue(config, "Plugin.Party-Warp.Game-Servers", new ArrayList<String>());
		defaultValue(config, "Plugin.Party-Warp.Server-Blocklist", new ArrayList<String>());
		defaultValue(config, "Plugin.Party-Warp.Lobby-Server-Name-Contains", "lobby");
		List<String> invalid = new ArrayList<>();
		validInt(config, "Plugin.Party.Disband-Delay-Ticks", 6000, 0, invalid);
		validInt(config, "Plugin.Party.Offline-Member-Kick-Delay-Ticks", 6000, 0, invalid);
		validInt(config, "Plugin.Party.Max-Size", 10, 2, invalid);
		validInt(config, "Plugin.LuckPerms.Offline-Prefix-Cache-Ticks", 36000, 0, invalid);
		validInt(config, "Plugin.LuckPerms.Offline-Prefix-Load-Timeout-Ticks", 40, 1, invalid);
		validInt(config, "Plugin.Party-Warp.Confirm-Timeout-Ticks", 200, 1, 6000, invalid);
		validInt(config, "Plugin.Party-Warp.Initial-Delay-Ticks", 10, 0, 600, invalid);
		validInt(config, "Plugin.Party-Warp.Member-Interval-Ticks", 2, 1, 600, invalid);
		return invalid;
	}

	static List<String> applyHyphenDefaults(Configuration config) {
		defaultValue(config, "Enable", true);
		defaultValue(config, "Categories.Enable-Command-Results", true);
		defaultValue(config, "Categories.Enable-Lists", true);
		defaultValue(config, "Categories.Enable-Party-Notifications", true);
		defaultValue(config, "Categories.Enable-Warp-Notifications", true);
		defaultValue(config, "Messages", new LinkedHashMap<String, Boolean>());
		List<String> invalid = new ArrayList<>();
		Configuration messages = config.getSection("Messages");
		if (messages != null) for (String messageId : messages.getKeys()) {
			try { MessageFrame.MessageId.valueOf(messageId); }
			catch (IllegalArgumentException e) { invalid.add("Messages." + messageId + " (unknown message id)"); }
		}
		return invalid;
	}

	static List<String> applyServerNameDisplayDefaults(Configuration config) {
		defaultValue(config, "Rules", defaultServerNameRules());
		List<String> invalid = new ArrayList<>();
		validateServerNameRules(config, invalid);
		return invalid;
	}

	static List<String> applyPartyCommandSenderDefaults(Configuration config) {
		defaultValue(config, "Enable", true);
		defaultValue(config, "Mappings", defaultPartyCommandMappings());
		List<String> invalid = new ArrayList<>();
		List<?> mappings = config.getList("Mappings");
		for (int index = 0; index < mappings.size(); index++) {
			Object value = mappings.get(index);
			if (!(value instanceof Map)) {
				invalid.add("Mappings[" + index + "] (expected a mapping)");
				continue;
			}
			Map<?, ?> mapping = (Map<?, ?>) value;
			Object leader = mapping.get("Leader-Command");
			Object member = mapping.get("Member-Command");
			if (leader == null || String.valueOf(leader).trim().isEmpty() || member == null
					|| String.valueOf(member).trim().isEmpty())
				invalid.add("Mappings[" + index + "] (leader or member command is empty)");
		}
		return invalid;
	}

	static List<String> applyFriendListDefaults(Configuration config) {
		defaultValue(config, "Display-Only-Prefix-Color", false);
		defaultValue(config, "Time-Format", "yyyy-MM-dd HH:mm:ss");
		defaultValue(config, "Sort", defaultFriendListSort());
		List<String> invalid = new ArrayList<>();
		List<?> sort = config.getList("Sort");
		for (int index = 0; index < sort.size(); index++) {
			Object value = sort.get(index);
			if (!(value instanceof Map)) {
				invalid.add("Sort[" + index + "] (expected a mapping)");
				continue;
			}
			Map<?, ?> item = (Map<?, ?>) value;
			Object prefix = item.get("Prefix");
			Object priority = item.get("Priority");
			try {
				int parsed = Integer.parseInt(String.valueOf(priority));
				if (prefix == null || String.valueOf(prefix).trim().isEmpty() || parsed < 0)
					throw new IllegalArgumentException();
			} catch (Exception e) {
				invalid.add("Sort[" + index + "] (prefix or priority is invalid)");
			}
		}
		return invalid;
	}

	private static Map<String, Boolean> defaultAliases() {
		Map<String, Boolean> aliases = new LinkedHashMap<>();
		for (String alias : new String[] {"/msg", "/tell", "/message", "/f", "/r", "/reply", "/fl", "/block", "/ignore",
				"/pc", "/ac", "/achat", "/pl", "/w", "/whisper"}) aliases.put(alias, true);
		return aliases;
	}

	private static List<Map<String, Object>> defaultServerNameRules() {
		List<Map<String, Object>> rules = new ArrayList<>();
		rules.add(serverNameRule("PREFIX", "lobby-", "Lobby"));
		rules.add(serverNameRule("CONTAINS", "game", "Game"));
		rules.add(serverNameRule("EXACT_IGNORE_CASE", "sw-1", "SkyWars"));
		return rules;
	}

	private static Map<String, Object> serverNameRule(String mode, String pattern, String displayName) {
		Map<String, Object> rule = new LinkedHashMap<>();
		Map<String, String> match = new LinkedHashMap<>();
		match.put("Mode", mode);
		match.put("Value", pattern);
		rule.put("Match", match);
		rule.put("Display-Name", displayName);
		return rule;
	}

	private static List<Map<String, String>> defaultPartyCommandMappings() {
		List<Map<String, String>> mappings = new ArrayList<>();
		mappings.add(partyCommandMapping("/join", "/join $args"));
		mappings.add(partyCommandMapping("/sw join", "/sw join $args"));
		return mappings;
	}

	private static List<Map<String, Object>> defaultFriendListSort() {
		List<Map<String, Object>> sort = new ArrayList<>();
		sort.add(friendListSortItem("[ADMIN]", 1));
		sort.add(friendListSortItem("[MOD]", 2));
		return sort;
	}

	private static Map<String, Object> friendListSortItem(String prefix, int priority) {
		Map<String, Object> item = new LinkedHashMap<>();
		item.put("Prefix", prefix);
		item.put("Priority", priority);
		return item;
	}

	private static Map<String, String> partyCommandMapping(String leader, String member) {
		Map<String, String> mapping = new LinkedHashMap<>();
		mapping.put("Leader-Command", leader);
		mapping.put("Member-Command", member);
		return mapping;
	}

	private static void validateServerNameRules(Configuration config, List<String> invalid) {
		List<?> rules = config.getList("Rules");
		for (int index = 0; index < rules.size(); index++) {
			Object value = rules.get(index);
			if (!(value instanceof Map)) {
				invalid.add("Rules[" + index + "] (expected a rule mapping)");
				continue;
			}
			Map<?, ?> rule = (Map<?, ?>) value;
			Object matchValue = rule.get("Match");
			Object displayName = rule.get("Display-Name");
			if (!(matchValue instanceof Map) || displayName == null) {
				invalid.add("Rules[" + index + "] (match or display name is missing)");
				continue;
			}
			Map<?, ?> match = (Map<?, ?>) matchValue;
			Object mode = match.get("Mode");
			Object pattern = match.get("Value");
			if (mode == null || !isServerMatchMode(String.valueOf(mode)) || pattern == null
					|| String.valueOf(pattern).isEmpty() || displayName == null || String.valueOf(displayName).isEmpty()) {
				invalid.add("Rules[" + index + "] (invalid mode, pattern, or display name)");
			}
		}
	}

	private static boolean isServerMatchMode(String mode) {
		return "PREFIX".equals(mode) || "CONTAINS".equals(mode) || "EXACT".equals(mode) || "EXACT_IGNORE_CASE".equals(mode);
	}

	private static void defaultValue(Configuration config, String key, Object value) {
		if (!config.contains(key)) config.set(key, value);
	}

	private static void validInt(Configuration config, String key, int fallback, int minimum, List<String> invalid) {
		validInt(config, key, fallback, minimum, Integer.MAX_VALUE, invalid);
	}

	private static void validInt(Configuration config, String key, int fallback, int minimum, int maximum, List<String> invalid) {
		try {
			Object raw = config.get(key);
			int value = Integer.parseInt(String.valueOf(raw));
			if (value >= minimum && value <= maximum) {
				config.set(key, value);
				return;
			}
		} catch (NumberFormatException ignored) { }
		invalid.add(key);
		config.set(key, fallback);
	}

}
