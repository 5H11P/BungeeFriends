package net.simplyrin.bungeefriends.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import com.google.common.base.Charsets;

import lombok.Getter;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import net.simplyrin.bungeefriends.Main;
import net.simplyrin.bungeefriends.tools.Config;

/**
 * Created by SimplyRin on 2021/03/09.
 *
 * author: SimplyRin
 * license: LGPL v3
 * copyright: Copyright (c) 2021 SimplyRin
 */
public class DataManager {

	private Main plugin;
	@Getter
	private Runnable runnable;
	@Getter
	private Configuration config;

	public DataManager(Main plugin) {
		this.plugin = plugin;

		this.autoMigration();
		this.save();
	}

	public void autoMigration() {
		ConfigManager manager = this.plugin.getConfigManager();

		File configF = new File(this.plugin.getDataFolder(), "data.yml");
		if (!configF.exists()) {
			try {
				configF.createNewFile();
			} catch (IOException e) {
			}
			Config.saveConfig(manager.getConfig(), configF);
			this.config = loadData(configF);
			this.config.set("Plugin", null);
			this.config.set("BungeeParties", null);
			this.config.set("UUID", null);
			this.config.set("Name", null);

			this.save();

			manager.getConfig().set("Player", null);
			manager.getConfig().set("Parties", null);
			manager.save();
		}

		this.config = loadData(configF);

	}

	static Configuration loadData(File file) {
		Configuration loaded = Config.getConfig(file, Charsets.UTF_8);
		if (loaded == null) throw new IllegalStateException("Cannot load data.yml");
		return loaded;
	}

	public synchronized void save() {
		persist(this.config, new File(this.plugin.getDataFolder(), "data.yml"));
	}

	static void persist(Configuration data, File config) {
		File temporary = new File(config.getParentFile(), config.getName() + ".tmp");
		try {
			ConfigurationProvider.getProvider(YamlConfiguration.class).save(data, temporary);
			try {
				Files.move(temporary.toPath(), config.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temporary.toPath(), config.toPath(), StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			throw new IllegalStateException("Cannot save data.yml", e);
		}
	}

}
