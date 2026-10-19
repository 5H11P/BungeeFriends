package net.simplyrin.bungeefriends.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

import net.md_5.bungee.config.Configuration;
import net.simplyrin.bungeefriends.tools.Config;

public class ConfigManagerFeatureTest {

	@Test
	public void defaultsAndLegacyValuesSurviveAnotherNormalization() {
		Configuration config = new Configuration();
		config.set("Plugin.Party-Disband-Time", "7");
		config.set("Plugin.Party-Kick-Offline-Time", "0");
		config.set("Plugin.Send-Remove-Target-Message", false);
		config.set("Plugin.Party-Disband-Delay-Minutes", 9);

		assertTrue(ConfigManager.applyFeatureDefaults(config).isEmpty());
		assertEquals(9, config.getInt("Plugin.Party-Disband-Delay-Minutes"));
		assertEquals(0, config.getInt("Plugin.Party-Offline-Member-Kick-Delay-Minutes"));
		assertFalse(config.getBoolean("Plugin.Notify-Removed-Friend"));
		assertEquals("yyyy-MM-dd HH:mm:ss", config.getString("Plugin.FriendList-Time.Format"));
		assertEquals(30, config.getInt("Plugin.LuckPerms.OfflinePrefixCacheMinutes"));
		assertEquals(2, config.getInt("Plugin.LuckPerms.OfflinePrefixLoadTimeoutSeconds"));
		assertFalse(config.contains("Plugin.Party-Disband-Time"));
		config.set("Plugin.Party-Kick-Offline-Time", "99");
		ConfigManager.applyFeatureDefaults(config);
		assertEquals(0, config.getInt("Plugin.Party-Offline-Member-Kick-Delay-Minutes"));
	}

	@Test
	public void normalizedValuesSurviveFileReload() throws Exception {
		Path path = Files.createTempFile("bungeefriends-config-", ".yml");
		try {
			Configuration initial = new Configuration();
			initial.set("Plugin.Party-Disband-Time", "12");
			initial.set("Plugin.Notify-Removed-Friend", false);
			Config.saveConfig(initial, path.toFile());
			Configuration reloaded = Config.getConfig(path.toFile(), StandardCharsets.UTF_8);
			ConfigManager.applyFeatureDefaults(reloaded);
			Config.saveConfig(reloaded, path.toFile());
			Configuration again = Config.getConfig(path.toFile(), StandardCharsets.UTF_8);
			assertEquals(12, again.getInt("Plugin.Party-Disband-Delay-Minutes"));
			assertFalse(again.getBoolean("Plugin.Notify-Removed-Friend"));
			assertFalse(again.contains("Plugin.Party-Disband-Time"));
		} finally {
			Files.deleteIfExists(path);
		}
	}

	@Test
	public void invalidNumbersFallBackWithoutChangingValidValues() {
		Configuration config = new Configuration();
		config.set("Plugin.Party-Disband-Delay-Minutes", "bad");
		config.set("Plugin.Party-Offline-Member-Kick-Delay-Minutes", -1);
		config.set("Plugin.LuckPerms.OfflinePrefixCacheMinutes", 4);
		config.set("Plugin.LuckPerms.OfflinePrefixLoadTimeoutSeconds", 0);
		List<String> invalid = ConfigManager.applyFeatureDefaults(config);
		assertEquals(3, invalid.size());
		assertEquals(5, config.getInt("Plugin.Party-Disband-Delay-Minutes"));
		assertEquals(5, config.getInt("Plugin.Party-Offline-Member-Kick-Delay-Minutes"));
		assertEquals(4, config.getInt("Plugin.LuckPerms.OfflinePrefixCacheMinutes"));
		assertEquals(2, config.getInt("Plugin.LuckPerms.OfflinePrefixLoadTimeoutSeconds"));
	}
}
