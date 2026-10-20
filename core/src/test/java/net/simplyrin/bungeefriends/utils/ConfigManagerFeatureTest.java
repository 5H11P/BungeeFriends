package net.simplyrin.bungeefriends.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.Test;

import net.md_5.bungee.config.Configuration;
import net.simplyrin.bungeefriends.tools.Config;

public class ConfigManagerFeatureTest {

	@Test
	public void bundledTemplateCreatesExternalConfigAndKeepsExistingFile() throws Exception {
		Path folder = Files.createTempDirectory("bungeefriends-config-");
		Path path = folder.resolve("config.yml");
		try {
			try (InputStream resource = getClass().getResourceAsStream("/config.yml")) {
				assertNotNull(resource);
			}
			Configuration generated = ConfigManager.loadOrCreateConfig(path.toFile());
			assertTrue(Files.exists(path));
			assertEquals("&7[&cFriends&7] &r", generated.getString("Plugin.Prefix"));
			assertEquals("english", generated.getString("Plugin.Default-Language"));
			assertFalse(generated.getBoolean("Plugin.Disable-Aliases./pl"));
			assertFalse(generated.getBoolean("Plugin.Disable-Aliases./ac"));
			assertEquals(5, generated.getInt("Plugin.Party-Disband-Delay-Minutes"));
			assertEquals("/join $args", generated.getString("Plugin.BungeeParties.PartyCmdSender.1.Member"));
			generated.set("Plugin.Prefix", "custom");
			generated.set("Name.alice", "id-1");
			generated.set("UUID.id-1", "Alice");
			Config.saveConfig(generated, path.toFile());
			byte[] before = Files.readAllBytes(path);
			Configuration existing = ConfigManager.loadOrCreateConfig(path.toFile());
			assertEquals("custom", existing.getString("Plugin.Prefix"));
			assertEquals("id-1", existing.getString("Name.alice"));
			assertEquals("Alice", existing.getString("UUID.id-1"));
			assertTrue(java.util.Arrays.equals(before, Files.readAllBytes(path)));
		} finally {
			Files.deleteIfExists(path);
			Files.deleteIfExists(folder);
		}
	}

	@Test
	public void oldPartySettingMigratesOnlyWhenNewSettingIsMissing() {
		Configuration legacy = new Configuration();
		legacy.set("BungeeParties.Enable", true);
		ConfigManager.migrateBungeeParties(legacy);
		assertTrue(legacy.getBoolean("Plugin.Enable-BungeeParties"));
		assertFalse(legacy.contains("BungeeParties.Enable"));

		Configuration explicit = new Configuration();
		explicit.set("BungeeParties.Enable", true);
		explicit.set("Plugin.Enable-BungeeParties", false);
		ConfigManager.migrateBungeeParties(explicit);
		assertFalse(explicit.getBoolean("Plugin.Enable-BungeeParties"));
		assertFalse(explicit.contains("BungeeParties.Enable"));
	}

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
