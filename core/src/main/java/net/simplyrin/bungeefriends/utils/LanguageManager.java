package net.simplyrin.bungeefriends.utils;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.common.base.Charsets;

import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.config.Configuration;
import net.simplyrin.bungeefriends.Main;
import net.simplyrin.bungeefriends.tools.Config;

/**
 * Created by SimplyRin on 2018/08/29.
 *
 * author: SimplyRin
 * license: LGPL v3
 * copyright: Copyright (c) 2021 SimplyRin
 */
public class LanguageManager {
	private static final String BUNDLED_LANGUAGE_PATH = "/languages/";
	private Main plugin;

	private HashMap<String, Configuration> configMap;

	private Configuration latestEnglishConfig;
	private Configuration latestChineseConfig;

	private HashMap<String, String> localLangMap = new HashMap<>();

	static String resolveMessage(Configuration selected, Configuration bundled, Configuration english, String key) {
		String result = selected.getString(key);
		if (result != null && !result.isEmpty()) return result;
		result = bundled.getString(key);
		return result == null || result.isEmpty() ? english.getString(key) : result;
	}

	public LanguageManager(Main plugin) {
		this.plugin = plugin;
		this.configMap = new HashMap<>();

		File folder = plugin.getDataFolder();
		if (!folder.exists()) {
			folder.mkdir();
		}

		File languageFolder = new File(folder, "Language");
		if (!languageFolder.exists()) {
			languageFolder.mkdir();
		}

		{
			this.plugin.info("Copying language files from plugin.");

			try {
				this.latestEnglishConfig = Config.getConfig(this.getClass().getResourceAsStream(BUNDLED_LANGUAGE_PATH + "English.yml"));
				this.latestChineseConfig = Config.getConfig(this.getClass().getResourceAsStream(BUNDLED_LANGUAGE_PATH + "Chinese.yml"));
			} catch (Exception e) {
				e.printStackTrace();
				this.plugin.info("Failed loadgin file from plugin.");
				return;
			}

			Configuration config = Config.getConfig(this.getClass().getResourceAsStream(BUNDLED_LANGUAGE_PATH + "available.yml"));
			for (String lang : config.getStringList("Langs")) {
				File file = new File(languageFolder, lang.toLowerCase() + ".yml");
				if (!file.exists()) {
					this.plugin.info("&7" + lang + " found! Copying...");
					Configuration langConfig;
					try {
						langConfig = Config.getConfig(this.getClass().getResourceAsStream(BUNDLED_LANGUAGE_PATH + lang + ".yml"));
					} catch (Exception e) {
						return;
					}

					file = new File(languageFolder, lang.toLowerCase() + ".yml");
					Config.saveConfig(langConfig, file);

					this.plugin.info("&aCopied the " + lang + " language.");
				}
			}
		}
	}

	public LanguageUtils getPlayer(ProxiedPlayer player) {
		return new LanguageUtils(player.getUniqueId());
	}

	public LanguageUtils getPlayer(String uuid) {
		return new LanguageUtils(UUID.fromString(uuid));
	}

	public LanguageUtils getPlayer(UUID uniqueId) {
		return new LanguageUtils(uniqueId);
	}

	public class LanguageUtils {

		private UUID uuid;

		public LanguageUtils(UUID uuid) {
			this.uuid = uuid;

			Object lang = plugin.getString("Player." + this.uuid.toString() + ".Language");
			if (lang == null || lang.equals("")) {
				plugin.set("Player." + this.uuid.toString() + ".Language", "english");
			}

			if (configMap.get("english") == null) {
				configMap.put("english", Config.getConfig(this.getFile("english"), Charsets.UTF_8));
			}
		}

		public String getLanguage() {
			if (localLangMap.get("Player." + this.uuid.toString() + ".Language") == null) {
				String key = plugin.getString("Player." + this.uuid.toString() + ".Language");
				if (key == null || key.equals("")) {
					key = "english";
				} else {
					key = key.substring(0, 1).toUpperCase() + key.substring(1, key.length());
				}
				if (!"Chinese".equalsIgnoreCase(key) && !"English".equalsIgnoreCase(key)) key = "English";
				localLangMap.put("Player." + this.uuid.toString() + ".Language", key);
			}
			return localLangMap.get("Player." + this.uuid.toString() + ".Language");
		}

		public void setLanguage(String key) {
			if (!"Chinese".equalsIgnoreCase(key) && !"English".equalsIgnoreCase(key))
				throw new IllegalArgumentException("Unsupported language: " + key);
			localLangMap.put("Player." + this.uuid.toString() + ".Language", key);
			plugin.set("Player." + this.uuid.toString() + ".Language", key);
		}

		public void reloadLanguage(String language) {
			File file = new File(this.getLanguagesFolder(), language + ".yml");
			configMap.put(this.getLanguage().toLowerCase(), Config.getConfig(file, Charsets.UTF_8));
		}

		public String getString(String configKey) {
			if (configKey == null || configKey.isEmpty()) {
				plugin.getLogger().warning("Requested an empty language key");
				return latestEnglishConfig == null ? "" : latestEnglishConfig.getString("No-Permission", "");
			}
			if (configKey.equals("Hyphen") && !plugin.getConfigManager().getHyphenConfig().getBoolean("Enable", true)) {
				return "";
			}

			Configuration config = configMap.get(this.getLanguage().toLowerCase());

			if (config == null) {
				File file = new File(this.getLanguagesFolder(), this.getLanguage().toLowerCase() + ".yml");
				configMap.put(this.getLanguage().toLowerCase(), file.exists() ? Config.getConfig(file, Charsets.UTF_8) : latestEnglishConfig);
			}

			Configuration bundled = "Chinese".equalsIgnoreCase(this.getLanguage()) ? latestChineseConfig : latestEnglishConfig;
			String message = resolveMessage(configMap.get(this.getLanguage().toLowerCase()), bundled, latestEnglishConfig, configKey);
			return message == null ? "" : message;
		}

		public String format(String configKey, Object... entries) {
			return format(configKey, MessageFormatter.values(entries));
		}

		public String format(String configKey, Map<String, ?> values) {
			String selected = getString(configKey);
			Set<String> allowed = values == null ? null : values.keySet();
			Set<String> unresolved = MessageFormatter.unresolvedExcept(selected, allowed);
			if (!unresolved.isEmpty()) {
				plugin.getLogger().warning("Unresolved language placeholders for " + getLanguage() + "." + configKey + ": " + unresolved);
				String english = latestEnglishConfig == null ? "" : latestEnglishConfig.getString(configKey, "");
				selected = english;
				unresolved = MessageFormatter.unresolvedExcept(selected, allowed);
				if (!unresolved.isEmpty()) {
					plugin.getLogger().severe("Built-in English message still has unresolved placeholders for " + configKey + ": " + unresolved);
					selected = MessageFormatter.maskUnresolved(selected);
				}
			}
			return MessageFormatter.replace(selected, values);
		}

		public String formatException(Exception error, String fallbackKey, Object... entries) {
			String key = error == null ? null : error.getMessage();
			if (key == null || key.isEmpty() || getString(key).isEmpty()) {
				plugin.getLogger().warning("Invalid language key on exception; using " + fallbackKey);
				key = fallbackKey;
			}
			return format(key, entries);
		}

		public String replace(String template, Object... entries) {
			return MessageFormatter.replace(template, MessageFormatter.values(entries));
		}

		public String getBuiltInEnglish(String configKey) {
			return latestEnglishConfig == null ? "" : latestEnglishConfig.getString(configKey, "");
		}

		public File getLanguagesFolder() {
			File folder = plugin.getDataFolder();
			if (!folder.exists()) {
				folder.mkdir();
			}

			File languageFolder = new File(folder, "Language");
			if (!languageFolder.exists()) {
				languageFolder.mkdir();
			}

			return languageFolder;
		}

		public File getFile() {
			return this.getFile(this.getLanguage());
		}

		public File getFile(String key) {
			return new File(this.getLanguagesFolder(), key.toLowerCase() + ".yml");
		}

	}

}
