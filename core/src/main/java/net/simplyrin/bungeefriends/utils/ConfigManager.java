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

	public ConfigManager(Main plugin) {
		this.plugin = plugin;

		this.createConfig();
		this.save();
	}

	public void save() {
		File config = new File(this.plugin.getDataFolder(), "config.yml");
		Config.saveConfig(this.config, config);
	}

	public void reload() {
		if (this.plugin.getPartyWarpManager() != null) this.plugin.getPartyWarpManager().cancelAll();
		File config = new File(this.plugin.getDataFolder(), "config.yml");
		this.config = Config.getConfig(config, Charsets.UTF_8);
		this.ensureFeatureDefaults();
		this.save();

		this.plugin.reloadPartyFeatures();
		this.plugin.reloadPartyCmdSender();
		this.plugin.getPriority().update();
	}

	public void createConfig() {
		File config = new File(this.plugin.getDataFolder(), "config.yml");
		this.config = loadOrCreateConfig(config);
		this.ensureFeatureDefaults();
		this.save();
	}

	static Configuration loadOrCreateConfig(File file) {
		if (!file.exists()) {
			try {
				Files.createDirectories(file.toPath().getParent());
				try (InputStream template = ConfigManager.class.getResourceAsStream("/config.yml")) {
					if (template == null) throw new IllegalStateException("Bundled config.yml is missing");
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
		defaultValue(config, "Plugin.Enable-Hyphen", true);
		defaultValue(config, "Plugin.Enable-Tab-Complete", true);
		defaultValue(config, "Plugin.Enable-Party-Command-Sender", true);
		defaultValue(config, "Plugin.Enable-Bungee-Parties", false);
		defaultValue(config, "Plugin.Enable-Status", true);
		defaultValue(config, "Plugin.Enable-Legacy-Prefix-YML", false);
		defaultValue(config, "Plugin.Enable-Friend-Removal-Notifications", true);
		defaultValue(config, "Plugin.Friend-List.Enable-Color-Only-Prefix", false);
		defaultValue(config, "Plugin.Party-Warp.Enable-Confirmation", false);
		defaultValue(config, "Plugin.Party-Warp.Enable-Server-Blocklist", true);
		defaultValue(config, "Plugin.Hyphen.Categories.Enable-Command-Results", true);
		defaultValue(config, "Plugin.Hyphen.Categories.Enable-Lists", true);
		defaultValue(config, "Plugin.Hyphen.Categories.Enable-Party-Notifications", true);
		defaultValue(config, "Plugin.Hyphen.Categories.Enable-Warp-Notifications", true);
		defaultValue(config, "Plugin.Party.Max-Size", 10);
		defaultValue(config, "Plugin.Party.Disband-Delay-Ticks", 6000);
		defaultValue(config, "Plugin.Party.Offline-Member-Kick-Delay-Ticks", 6000);
		defaultValue(config, "Plugin.Friend-List.Time-Format", "yyyy-MM-dd HH:mm:ss");
		defaultValue(config, "Plugin.LuckPerms.Offline-Prefix-Cache-Ticks", 36000);
		defaultValue(config, "Plugin.LuckPerms.Offline-Prefix-Load-Timeout-Ticks", 40);
		defaultValue(config, "Plugin.Party-Warp.Confirm-Timeout-Ticks", 200);
		defaultValue(config, "Plugin.Party-Warp.Initial-Delay-Ticks", 10);
		defaultValue(config, "Plugin.Party-Warp.Member-Interval-Ticks", 2);
		defaultValue(config, "Plugin.Party-Warp.Game-Servers", new ArrayList<String>());
		defaultValue(config, "Plugin.Party-Warp.Server-Blocklist", new ArrayList<String>());
		defaultValue(config, "Plugin.Party-Warp.Lobby-Server-Name-Contains", "lobby");
		defaultValue(config, "Plugin.Server-Name-Display.Rules", defaultServerNameRules());
		defaultValue(config, "Plugin.Bungee-Parties.Party-Command-Sender.1.Leader", "/join");
		defaultValue(config, "Plugin.Bungee-Parties.Party-Command-Sender.1.Member", "/join $args");
		defaultValue(config, "Plugin.Bungee-Parties.Party-Command-Sender.2.Leader", "/sw join");
		defaultValue(config, "Plugin.Bungee-Parties.Party-Command-Sender.2.Member", "/sw join $args");
		defaultValue(config, "Plugin.Friend-List.Sort.1.No-Color-Prefix", "[ADMIN]");
		defaultValue(config, "Plugin.Friend-List.Sort.1.Priority", 1);
		defaultValue(config, "Plugin.Friend-List.Sort.2.No-Color-Prefix", "[MOD]");
		defaultValue(config, "Plugin.Friend-List.Sort.2.Priority", 2);
		List<String> invalid = new ArrayList<>();
		Configuration messageOverrides = config.getSection("Plugin.Hyphen.Messages");
		if (messageOverrides != null) {
			for (String messageId : messageOverrides.getKeys()) {
				try {
					MessageFrame.MessageId.valueOf(messageId);
				} catch (IllegalArgumentException e) {
					invalid.add("Plugin.Hyphen.Messages." + messageId + " (unknown message id)");
				}
			}
		}
		validInt(config, "Plugin.Party.Disband-Delay-Ticks", 6000, 0, invalid);
		validInt(config, "Plugin.Party.Offline-Member-Kick-Delay-Ticks", 6000, 0, invalid);
		validInt(config, "Plugin.Party.Max-Size", 10, 2, invalid);
		validInt(config, "Plugin.LuckPerms.Offline-Prefix-Cache-Ticks", 36000, 0, invalid);
		validInt(config, "Plugin.LuckPerms.Offline-Prefix-Load-Timeout-Ticks", 40, 1, invalid);
		validInt(config, "Plugin.Party-Warp.Confirm-Timeout-Ticks", 200, 1, 6000, invalid);
		validInt(config, "Plugin.Party-Warp.Initial-Delay-Ticks", 10, 0, 600, invalid);
		validInt(config, "Plugin.Party-Warp.Member-Interval-Ticks", 2, 1, 600, invalid);
		validateServerNameRules(config, invalid);
		return invalid;
	}

	private static Map<String, Boolean> defaultAliases() {
		Map<String, Boolean> aliases = new LinkedHashMap<>();
		for (String alias : new String[] {"/msg", "/tell", "/message", "/f", "/r", "/reply", "/fl", "/block", "/ignore",
				"/pc", "/ac", "/achat", "/pl", "/w", "/whisper"}) aliases.put(alias, true);
		return aliases;
	}

	private static List<Map<String, String>> defaultServerNameRules() {
		List<Map<String, String>> rules = new ArrayList<>();
		rules.add(serverNameRule("PREFIX", "lobby-", "Lobby"));
		rules.add(serverNameRule("CONTAINS", "game", "Game"));
		rules.add(serverNameRule("EXACT_IGNORE_CASE", "sw-1", "SkyWars"));
		return rules;
	}

	private static Map<String, String> serverNameRule(String mode, String pattern, String displayName) {
		Map<String, String> rule = new LinkedHashMap<>();
		rule.put("Match-Mode", mode);
		rule.put("Pattern", pattern);
		rule.put("Display-Name", displayName);
		return rule;
	}

	private static void validateServerNameRules(Configuration config, List<String> invalid) {
		List<?> rules = config.getList("Plugin.Server-Name-Display.Rules");
		for (int index = 0; index < rules.size(); index++) {
			Object value = rules.get(index);
			if (!(value instanceof Map)) {
				invalid.add("Plugin.Server-Name-Display.Rules[" + index + "] (expected a rule mapping)");
				continue;
			}
			Map<?, ?> rule = (Map<?, ?>) value;
			Object mode = rule.get("Match-Mode");
			Object pattern = rule.get("Pattern");
			Object displayName = rule.get("Display-Name");
			if (mode == null || !isServerMatchMode(String.valueOf(mode)) || pattern == null
					|| String.valueOf(pattern).isEmpty() || displayName == null || String.valueOf(displayName).isEmpty()) {
				invalid.add("Plugin.Server-Name-Display.Rules[" + index + "] (invalid mode, pattern, or display name)");
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
