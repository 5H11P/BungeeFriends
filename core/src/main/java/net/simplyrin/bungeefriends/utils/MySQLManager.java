package net.simplyrin.bungeefriends.utils;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

import com.google.common.base.Charsets;

import lombok.Getter;
import net.md_5.bungee.config.Configuration;
import net.simplyrin.bungeefriends.Main;
import net.simplyrin.bungeefriends.tools.Config;
import net.simplyrin.bungeefriends.tools.MySQL;
import net.simplyrin.bungeefriends.tools.MySQL.Editor;

/** Owns the MySQL connection. Enabled MySQL must start successfully or plugin startup fails. */
@Getter
public class MySQLManager {
	private final Main plugin;
	private Configuration config;
	private Editor editor;
	private boolean debugMode;

	public MySQLManager(Main plugin) {
		this.plugin = plugin;
		createConfig();
		if (config.getBoolean("Enable")) {
			MySQL mysql = new MySQL(plugin, config.getString("Username"), config.getString("Password"))
					.setAddress(config.getString("Address"))
					.setDatabase(config.getString("Database"))
					.setTimezone(config.getString("Timezone"))
					.setUseSSL(config.getBoolean("UseSSL"));
			try {
				editor = mysql.connect();
			} catch (SQLException e) {
				throw new IllegalStateException("MySQL startup failed; storage was not switched to YAML", e);
			}
		}
	}

	private void createConfig() {
		File folder = plugin.getDataFolder();
		if (!folder.exists() && !folder.mkdirs()) throw new IllegalStateException("Cannot create plugin data folder");
		File file = new File(folder, "mysql.yml");
		if (!file.exists()) {
			try {
				if (!file.createNewFile()) throw new IOException("Cannot create mysql.yml");
			} catch (IOException e) {
				throw new IllegalStateException("Cannot create mysql.yml", e);
			}
			config = Config.getConfig(file, Charsets.UTF_8);
			config.set("Enable", false);
			config.set("DebugMode", false);
			config.set("Username", "ROOT");
			config.set("Password", "PASSWORD");
			config.set("Address", "localhost:3306");
			config.set("Database", "bungeefriends");
			config.set("Timezone", "UTC");
			config.set("UseSSL", false);
			Config.saveConfig(config, file);
		}
		config = Config.getConfig(file, Charsets.UTF_8);
		if (config == null) throw new IllegalStateException("Cannot load mysql.yml");
		debugMode = config.getBoolean("DebugMode");
	}

	public void close() {
		if (editor == null) return;
		try {
			editor.getMySQL().disconnect();
		} catch (SQLException e) {
			throw new IllegalStateException("MySQL shutdown failed", e);
		}
	}
}
