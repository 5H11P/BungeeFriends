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
			assertEquals(5, generated.getInt("Plugin.Party.Disband-Delay-Minutes"));
			assertEquals("/join $args", generated.getString("Plugin.Bungee-Parties.Party-Command-Sender.1.Member"));
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
	public void canonicalDefaultsAreAppliedWithoutMigratingOldKeys() {
		Configuration config = new Configuration();
		config.set("Plugin.Party-Disband-Time", "7");
		config.set("Plugin.Party-Kick-Offline-Time", "0");
		config.set("Plugin.Send-Remove-Target-Message", false);
		config.set("Plugin.Party.Disband-Delay-Minutes", 9);

		assertTrue(ConfigManager.applyFeatureDefaults(config).isEmpty());
		assertEquals(9, config.getInt("Plugin.Party.Disband-Delay-Minutes"));
		assertEquals(5, config.getInt("Plugin.Party.Offline-Member-Kick-Delay-Minutes"));
		assertTrue(config.getBoolean("Plugin.Notify-Removed-Friend"));
		assertEquals("yyyy-MM-dd HH:mm:ss", config.getString("Plugin.Friend-List.Time-Format"));
		assertEquals(30, config.getInt("Plugin.LuckPerms.Offline-Prefix-Cache-Minutes"));
		assertEquals(2, config.getInt("Plugin.LuckPerms.Offline-Prefix-Load-Timeout-Seconds"));
		assertTrue(config.getBoolean("Plugin.Hyphen.Categories.Command-Results"));
		assertTrue(config.getBoolean("Plugin.Hyphen.Categories.Lists"));
		assertTrue(config.getBoolean("Plugin.Hyphen.Categories.Party-Notifications"));
		assertTrue(config.getBoolean("Plugin.Hyphen.Categories.Warp-Notifications"));
		assertTrue(config.contains("Plugin.Party-Disband-Time"));
	}

	@Test
	public void hyphenCategoryOverridesSurviveDefaulting() {
		Configuration config = new Configuration();
		config.set("Plugin.Hyphen.Categories.Lists", false);
		config.set("Plugin.Hyphen.Messages.PARTY_MUTE_DENIED", false);

		ConfigManager.applyFeatureDefaults(config);

		assertFalse(config.getBoolean("Plugin.Hyphen.Categories.Lists"));
		assertFalse(config.getBoolean("Plugin.Hyphen.Messages.PARTY_MUTE_DENIED"));
		assertTrue(config.getBoolean("Plugin.Hyphen.Categories.Command-Results"));
	}

	@Test
	public void unknownHyphenMessageOverridesAreReported() {
		Configuration config = new Configuration();
		config.set("Plugin.Hyphen.Messages.NOT_A_MESSAGE", false);

		List<String> invalid = ConfigManager.applyFeatureDefaults(config);

		assertTrue(invalid.contains("Plugin.Hyphen.Messages.NOT_A_MESSAGE (unknown message id)"));
		assertFalse(config.getBoolean("Plugin.Hyphen.Messages.NOT_A_MESSAGE"));
	}

	@Test
	public void canonicalValuesSurviveFileReload() throws Exception {
		Path path = Files.createTempFile("bungeefriends-config-", ".yml");
		try {
			Configuration initial = new Configuration();
			initial.set("Plugin.Party.Disband-Delay-Minutes", 12);
			initial.set("Plugin.Notify-Removed-Friend", false);
			Config.saveConfig(initial, path.toFile());
			Configuration reloaded = Config.getConfig(path.toFile(), StandardCharsets.UTF_8);
			ConfigManager.applyFeatureDefaults(reloaded);
			Config.saveConfig(reloaded, path.toFile());
			Configuration again = Config.getConfig(path.toFile(), StandardCharsets.UTF_8);
			assertEquals(12, again.getInt("Plugin.Party.Disband-Delay-Minutes"));
			assertFalse(again.getBoolean("Plugin.Notify-Removed-Friend"));
			assertFalse(again.contains("Plugin.Party-Disband-Time"));
		} finally {
			Files.deleteIfExists(path);
		}
	}

	@Test
	public void invalidNumbersFallBackWithoutChangingValidValues() {
		Configuration config = new Configuration();
		config.set("Plugin.Party.Disband-Delay-Minutes", "bad");
		config.set("Plugin.Party.Offline-Member-Kick-Delay-Minutes", -1);
		config.set("Plugin.LuckPerms.Offline-Prefix-Cache-Minutes", 4);
		config.set("Plugin.LuckPerms.Offline-Prefix-Load-Timeout-Seconds", 0);
		List<String> invalid = ConfigManager.applyFeatureDefaults(config);
		assertEquals(3, invalid.size());
		assertEquals(5, config.getInt("Plugin.Party.Disband-Delay-Minutes"));
		assertEquals(5, config.getInt("Plugin.Party.Offline-Member-Kick-Delay-Minutes"));
		assertEquals(4, config.getInt("Plugin.LuckPerms.Offline-Prefix-Cache-Minutes"));
		assertEquals(2, config.getInt("Plugin.LuckPerms.Offline-Prefix-Load-Timeout-Seconds"));
	}
}
