package net.simplyrin.bungeefriends.utils;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.Locale;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import com.google.common.base.Charsets;

import lombok.Getter;
import net.md_5.bungee.api.plugin.Listener;
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
public class PlayerManager implements Listener {

	private Main plugin;
	@Getter
	private Runnable runnable;
	@Getter
	private Configuration config;

	public PlayerManager(Main plugin) {
		this.plugin = plugin;

		this.createConfig();

		boolean hasKey = false;
		for (String key : this.plugin.getConfigManager().getConfig().getSection("UUID").getKeys()) {
			hasKey = true;
			this.config.set("UUID." + key, this.plugin.getConfigManager().getConfig().get("UUID." + key));
		}
		if (hasKey) {
			this.plugin.getConfigManager().getConfig().set("UUID", null);
			this.plugin.getConfigManager().save();
		}
		hasKey = false;
		for (String key : this.plugin.getConfigManager().getConfig().getSection("Name").getKeys()) {
			hasKey = true;
			this.config.set("Name." + key, this.plugin.getConfigManager().getConfig().get("Name." + key));
		}
		if (hasKey) {
			this.plugin.getConfigManager().getConfig().set("Name", null);
			this.plugin.getConfigManager().save();
		}

		this.config.set("Parties", null);
		this.config.set("Plugin", null);

		this.createConfig();
	}

	public UUID getPlayerUniqueId(String name) {
		if (name == null || name.trim().isEmpty()) return null;
		String candidate = name.trim();
		try { return UUID.fromString(candidate); } catch (IllegalArgumentException ignored) { }
		for (ProxiedPlayer player : this.plugin.getProxy().getPlayers())
			if (player.getName().equalsIgnoreCase(candidate)) return player.getUniqueId();
		String value = this.plugin.getString("Name." + candidate.toLowerCase(Locale.ROOT));
		try { return value == null ? null : UUID.fromString(value); } catch (IllegalArgumentException ignored) { return null; }
	}

	public String getPlayerName(UUID uuid) {
		return this.plugin.getString("UUID." + uuid.toString());
	}

	public void save() {
		File config = new File(this.plugin.getDataFolder(), "player.yml");
		Config.saveConfig(this.config, config);
	}

	public void createConfig() {
		File folder = this.plugin.getDataFolder();
		if (!folder.exists()) {
			folder.mkdir();
		}

		File config = new File(folder, "player.yml");
		if (!config.exists()) {
			try {
				config.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}

			this.config = Config.getConfig(config, Charsets.UTF_8);
			Config.saveConfig(this.config, config);
		}

		this.config = Config.getConfig(config, Charsets.UTF_8);

		this.save();
	}

}
