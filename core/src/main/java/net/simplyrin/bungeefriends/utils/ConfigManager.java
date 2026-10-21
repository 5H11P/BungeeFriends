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
		this.resetValue("Plugin.Disable-Server-Switch-Notifier");
		this.resetValue("Plugin.Disable-Party-Warp");
		this.resetValue("Plugin.Disable-LuckPerms-API");
		this.resetValue("Plugin.Disable-Hyphen");
		this.resetValue("Plugin.Disable-Tab-Complete");
		this.resetValue("Plugin.Disable-Party-Command-Sender");
		this.resetValue("Plugin.Enable-Bungee-Parties");
		this.ensureP4Defaults();
		this.ensureFeatureDefaults();
		this.resetValue("Plugin.Enable-Deprecated-Prefix-YML");
		this.resetValue("Plugin.Friend-List.No-Prefix");

		this.resetStringValue("Plugin.Default-Language", "english");
		this.resetStringValue("Plugin.Party-Warp.Lobby-Server-Name-Contains", "lobby");

		this.resetStringValue("Plugin.Server-Name.1.Type", "startsWith");
		this.resetStringValue("Plugin.Server-Name.1.Server-Name", "lobby-");
		this.resetStringValue("Plugin.Server-Name.1.Replace-Name", "Lobby");

		this.resetStringValue("Plugin.Server-Name.2.Type", "contains");
		this.resetStringValue("Plugin.Server-Name.2.Server-Name", "game");
		this.resetStringValue("Plugin.Server-Name.2.Replace-Name", "Game");

		this.resetStringValue("Plugin.Server-Name.3.Type", "equalsIgnoreCase");
		this.resetStringValue("Plugin.Server-Name.3.Server-Name", "sw-1");
		this.resetStringValue("Plugin.Server-Name.3.Replace-Name", "SkyWars");

		this.resetStringValue("Plugin.Bungee-Parties.Party-Command-Sender.1.Leader", "/join");
		this.resetStringValue("Plugin.Bungee-Parties.Party-Command-Sender.1.Member", "/join $args");

		this.resetStringValue("Plugin.Bungee-Parties.Party-Command-Sender.2.Leader", "/sw join");
		this.resetStringValue("Plugin.Bungee-Parties.Party-Command-Sender.2.Member", "/sw join $args");

		this.resetStringValue("Plugin.Friend-List.Sort.1.No-Color-Prefix", "[ADMIN]");
		this.resetStringValue("Plugin.Friend-List.Sort.1.Priority", 1);

		this.resetStringValue("Plugin.Friend-List.Sort.2.No-Color-Prefix", "[MOD]");
		this.resetStringValue("Plugin.Friend-List.Sort.2.Priority", 2);

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

	public void resetStringValue(String key, String value) {
		String current = this.config.getString(key);
		if (current == null || current.isEmpty()) {
			this.config.set(key, value);
		}
	}

	private void ensureP4Defaults() {
		defaultValue(this.config, "Plugin.Party.Max-Size", 10);
		if (!this.config.contains("Plugin.Enable-Status")) this.config.set("Plugin.Enable-Status", true);
		if (!this.config.contains("Plugin.Party-Warp.Server-Blocklist.Enabled")) this.config.set("Plugin.Party-Warp.Server-Blocklist.Enabled", true);
		if (!this.config.contains("Plugin.Party-Warp.Server-Blocklist.Servers")) this.config.set("Plugin.Party-Warp.Server-Blocklist.Servers", new java.util.ArrayList<String>());
		if (!this.config.contains("Plugin.Party-Warp.Enable-Confirmation")) this.config.set("Plugin.Party-Warp.Enable-Confirmation", false);
		if (!this.config.contains("Plugin.Party-Warp.Confirm-Timeout-Seconds")) this.config.set("Plugin.Party-Warp.Confirm-Timeout-Seconds", 10);
		if (!this.config.contains("Plugin.Party-Warp.Game-Servers")) this.config.set("Plugin.Party-Warp.Game-Servers", new java.util.ArrayList<String>());
		this.resetStringValue("Plugin.Party-Warp.Initial-Delay-Seconds", "0.5");
		this.resetStringValue("Plugin.Party-Warp.Member-Interval-Seconds", "0.1");
	}

	private void ensureFeatureDefaults() {
		for (String invalid : applyFeatureDefaults(this.config))
			this.plugin.getLogger().warning("Invalid " + invalid + "; using default");
	}

	static List<String> applyFeatureDefaults(Configuration config) {
		defaultValue(config, "Plugin.Hyphen.Categories.Command-Results", true);
		defaultValue(config, "Plugin.Hyphen.Categories.Lists", true);
		defaultValue(config, "Plugin.Hyphen.Categories.Party-Notifications", true);
		defaultValue(config, "Plugin.Hyphen.Categories.Warp-Notifications", true);
		defaultValue(config, "Plugin.Party.Max-Size", 10);
		defaultValue(config, "Plugin.Party.Disband-Delay-Minutes", 5);
		defaultValue(config, "Plugin.Party.Offline-Member-Kick-Delay-Minutes", 5);
		defaultValue(config, "Plugin.Notify-Removed-Friend", true);
		defaultValue(config, "Plugin.Friend-List.Time-Format", "yyyy-MM-dd HH:mm:ss");
		defaultValue(config, "Plugin.LuckPerms.Offline-Prefix-Cache-Minutes", 30);
		defaultValue(config, "Plugin.LuckPerms.Offline-Prefix-Load-Timeout-Seconds", 2);
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
		validInt(config, "Plugin.Party.Disband-Delay-Minutes", 5, 0, invalid);
		validInt(config, "Plugin.Party.Offline-Member-Kick-Delay-Minutes", 5, 0, invalid);
		validInt(config, "Plugin.Party.Max-Size", 10, 2, invalid);
		validInt(config, "Plugin.LuckPerms.Offline-Prefix-Cache-Minutes", 30, 0, invalid);
		validInt(config, "Plugin.LuckPerms.Offline-Prefix-Load-Timeout-Seconds", 2, 1, invalid);
		return invalid;
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
