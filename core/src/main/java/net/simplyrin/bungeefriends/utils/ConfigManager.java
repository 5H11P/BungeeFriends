package net.simplyrin.bungeefriends.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import com.google.common.base.Charsets;

import lombok.Getter;
import net.md_5.bungee.config.Configuration;
import net.simplyrin.bungeefriends.Main;
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
		migrateBungeeParties(this.config);
		this.ensureP4Defaults();
		this.ensureFeatureDefaults();
		this.save();

		this.plugin.reloadPartyFeatures();
		this.plugin.reloadPartyCmdSender();
		this.plugin.getPriority().update();
	}

	public void createConfig() {
		File config = new File(this.plugin.getDataFolder(), "config.yml");
		this.config = loadOrCreateConfig(config);
		migrateBungeeParties(this.config);

		this.config.set("Plugin.Disable-Alias", null);

		this.resetValue("Plugin.Disable-Aliases./msg");
		this.resetValue("Plugin.Disable-Aliases./tell");
		this.resetValue("Plugin.Disable-Aliases./message");
		this.resetValue("Plugin.Disable-Aliases./f");
		this.resetValue("Plugin.Disable-Aliases./r");
		this.resetValue("Plugin.Disable-Aliases./reply");
		this.resetValue("Plugin.Disable-Aliases./fl");
		this.resetValue("Plugin.Disable-Aliases./block");
		this.resetValue("Plugin.Disable-Aliases./ignore");
		this.resetValue("Plugin.Disable-Aliases./pc");
		this.resetValue("Plugin.Disable-Aliases./ac");
		this.resetValue("Plugin.Disable-Aliases./achat");
		this.resetValue("Plugin.Disable-Aliases./pl");
		this.resetValue("Plugin.Disable-Aliases./w");
		this.resetValue("Plugin.Disable-Aliases./whisper");
		this.resetValue("Plugin.Disable-Lang-Command");
		this.resetValue("Plugin.Disable-ServerSwitchNotifer");
		this.resetValue("Plugin.Disable-PartyWarp");
		this.resetValue("Plugin.Disable-LuckPerms-API");
		this.resetValue("Plugin.Disable-Hyphen");
		this.resetValue("Plugin.Disable-TabComplete");
		this.resetValue("Plugin.Disable-PartyCmdSender");
		this.resetValue("Plugin.Enable-BungeeParties");
		this.ensureP4Defaults();
		this.ensureFeatureDefaults();
		this.resetValue("Plugin.Enable-Deprecated-PrefixYml");
		this.resetValue("Plugin.FriendList-NoPrefix");

		this.resetStringValue("Plugin.Default-Language", "english");
		this.resetStringValue("Plugin.Bypass-Lobby-Name-Contains", "lobby");

		this.resetStringValue("Plugin.ServerName.1.Type", "startsWith");
		this.resetStringValue("Plugin.ServerName.1.ServerName", "lobby-");
		this.resetStringValue("Plugin.ServerName.1.ReplaceName", "Lobby");

		this.resetStringValue("Plugin.ServerName.2.Type", "contains");
		this.resetStringValue("Plugin.ServerName.2.ServerName", "game");
		this.resetStringValue("Plugin.ServerName.2.ReplaceName", "Game");

		this.resetStringValue("Plugin.ServerName.3.Type", "equalsIgnoreCase");
		this.resetStringValue("Plugin.ServerName.3.ServerName", "sw-1");
		this.resetStringValue("Plugin.ServerName.3.ReplaceName", "SkyWars");

		this.resetStringValue("Plugin.BungeeParties.PartyCmdSender.1.Leader", "/join");
		this.resetStringValue("Plugin.BungeeParties.PartyCmdSender.1.Member", "/join $args");

		this.resetStringValue("Plugin.BungeeParties.PartyCmdSender.2.Leader", "/sw join");
		this.resetStringValue("Plugin.BungeeParties.PartyCmdSender.2.Member", "/sw join $args");

		this.resetStringValue("Plugin.FriendList-Sort.1.NoColorPrefix", "[ADMIN]");
		this.resetStringValue("Plugin.FriendList-Sort.1.Priority", 1);

		this.resetStringValue("Plugin.FriendList-Sort.2.NoColorPrefix", "[MOD]");
		this.resetStringValue("Plugin.FriendList-Sort.2.Priority", 2);

		this.config.set("Plugin.Disable-Party-ServerSwitchNotifer", null);
		this.config.set("Plugin.Disable-Lang-Command", null);
		this.config.set("Plugin.Use-LuckPerms-API", null);

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

	static void migrateBungeeParties(Configuration config) {
		if (config.contains("BungeeParties.Enable") && !config.contains("Plugin.Enable-BungeeParties")) {
			config.set("Plugin.Enable-BungeeParties", config.getBoolean("BungeeParties.Enable"));
		}
		config.set("BungeeParties.Enable", null);
	}

	public void resetStringValue(String key, String value) {
		if (this.config.getString(key).equals("")) {
			this.config.set(key, value);
		}
	}

	private void ensureP4Defaults() {
		this.resetStringValue("Plugin.Party-Max-Size", "10");
		if (!this.config.contains("Plugin.Enable-Status")) this.config.set("Plugin.Enable-Status", true);
		if (!this.config.contains("Plugin.Enable-NoPartyWarp-Servers")) this.config.set("Plugin.Enable-NoPartyWarp-Servers", true);
		if (!this.config.contains("Plugin.NoPartyWarp-Servers")) this.config.set("Plugin.NoPartyWarp-Servers", new java.util.ArrayList<String>());
		if (!this.config.contains("Plugin.PartyWarp.Enable-Confirmation")) this.config.set("Plugin.PartyWarp.Enable-Confirmation", false);
		if (!this.config.contains("Plugin.PartyWarp.Confirm-Timeout")) this.config.set("Plugin.PartyWarp.Confirm-Timeout", 10);
		if (!this.config.contains("Plugin.PartyWarp.Game-Servers")) this.config.set("Plugin.PartyWarp.Game-Servers", new java.util.ArrayList<String>());
		this.resetStringValue("Plugin.PartyWarp.Initial-Delay", "0.5");
		this.resetStringValue("Plugin.PartyWarp.Member-Interval", "0.1");
	}

	private void ensureFeatureDefaults() {
		for (String invalid : applyFeatureDefaults(this.config))
			this.plugin.getLogger().warning("Invalid " + invalid + "; using default");
	}

	static List<String> applyFeatureDefaults(Configuration config) {
		migrateKey(config, "Plugin.Party-Disband-Time", "Plugin.Party-Disband-Delay-Minutes");
		migrateKey(config, "Plugin.Party-Kick-Offline-Time", "Plugin.Party-Offline-Member-Kick-Delay-Minutes");
		migrateKey(config, "Plugin.Send-Remove-Target-Message", "Plugin.Notify-Removed-Friend");
		defaultValue(config, "Plugin.Party-Disband-Delay-Minutes", 5);
		defaultValue(config, "Plugin.Party-Offline-Member-Kick-Delay-Minutes", 5);
		defaultValue(config, "Plugin.Notify-Removed-Friend", true);
		defaultValue(config, "Plugin.FriendList-Time.Format", "yyyy-MM-dd HH:mm:ss");
		defaultValue(config, "Plugin.LuckPerms.OfflinePrefixCacheMinutes", 30);
		defaultValue(config, "Plugin.LuckPerms.OfflinePrefixLoadTimeoutSeconds", 2);
		List<String> invalid = new ArrayList<>();
		validInt(config, "Plugin.Party-Disband-Delay-Minutes", 5, 0, invalid);
		validInt(config, "Plugin.Party-Offline-Member-Kick-Delay-Minutes", 5, 0, invalid);
		validInt(config, "Plugin.LuckPerms.OfflinePrefixCacheMinutes", 30, 0, invalid);
		validInt(config, "Plugin.LuckPerms.OfflinePrefixLoadTimeoutSeconds", 2, 1, invalid);
		return invalid;
	}

	private static void migrateKey(Configuration config, String oldKey, String newKey) {
		if (!config.contains(newKey) && config.contains(oldKey)) config.set(newKey, config.get(oldKey));
		config.set(oldKey, null);
	}

	private static void defaultValue(Configuration config, String key, Object value) {
		if (!config.contains(key)) config.set(key, value);
	}

	private static void validInt(Configuration config, String key, int fallback, int minimum, List<String> invalid) {
		try {
			Object raw = config.get(key);
			int value = Integer.parseInt(String.valueOf(raw));
			if (value >= minimum) {
				config.set(key, value);
				return;
			}
		} catch (NumberFormatException ignored) { }
		invalid.add(key);
		config.set(key, fallback);
	}

	public void resetStringValue(String key, int value) {
		if (this.config.getInt(key, -1) == -1) {
			this.config.set(key, value);
		}
	}

	public void resetValue(String key) {
		if (!this.config.getBoolean(key)) {
			this.config.set(key, false);
		}
	}

}
