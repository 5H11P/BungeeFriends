package net.simplyrin.bungeefriends.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.Test;

import net.md_5.bungee.config.Configuration;
import net.simplyrin.bungeefriends.tools.Config;

public class LanguageResourcesTest {
	@Test
	public void builtInLanguagesContainNewCommandMessages() throws java.io.IOException {
		Set<String> englishKeys = null;
		for (String language : Arrays.asList("English", "Chinese")) {
			try (InputStream stream = getClass().getResourceAsStream("/languages/" + language + ".yml")) {
				Configuration config = Config.getConfig(stream);
				for (String key : Arrays.asList("Block.Self", "Block.List-Format", "Block.RemoveAll-Done",
						"Status-Command.Usage", "Status-Command.Disabled", "Status.ChatBar.Busy",
						"Status.ActionBar.AppearOffline", "Warp.Blocked", "Warp.No-Server",
						"Warp.Confirm",
						"Requests.Entry", "Note.Usage", "Note.Description.Usage", "Note.Description.TooLong",
						"Prefix.TooLong", "Help.Note-Description",
						"Party-Setting.Usage", "Party-Private.No-Permission")) {
					assertFalse(language + ": " + key, config.getString(key).isEmpty());
				}
				Set<String> keys = new HashSet<>();
				collectKeys(config, "", keys);
				assertFalse(language + " contains removed updater keys", keys.stream().anyMatch(key -> key.startsWith("Lang.Updater")));
				if (englishKeys == null) englishKeys = keys;
				else assertEquals("Chinese and English message keys differ", englishKeys, keys);
			}
		}
		try (InputStream stream = getClass().getResourceAsStream("/languages/available.yml")) {
			assertEquals(Arrays.asList("Chinese", "English"), Config.getConfig(stream).getStringList("Langs"));
		}
		assertNull(getClass().getResourceAsStream("/English.yml"));
		assertNull(getClass().getResourceAsStream("/Chinese.yml"));
		assertNull(getClass().getResourceAsStream("/languages/Arabic.yml"));
	}

	private void collectKeys(Configuration section, String prefix, Set<String> keys) {
		for (String name : section.getKeys()) {
			String path = prefix + name;
			Object value = section.get(name);
			if (value instanceof Configuration) collectKeys((Configuration) value, path + ".", keys);
			else keys.add(path);
		}
	}

	@Test
	public void newMessagesAndPlaceholdersMatchAcrossLanguages() {
		Configuration english = Config.getConfig(getClass().getResourceAsStream("/languages/English.yml"));
		Configuration chinese = Config.getConfig(getClass().getResourceAsStream("/languages/Chinese.yml"));
		for (String key : Arrays.asList("List.Hover-Online", "List.Hover-Offline", "List.Hover-Online-With-Note",
				"List.Hover-Offline-With-Note", "List.Now", "List.None",
				"List.Party-Header", "List.Party-Leader", "List.Party-Members", "List.Page",
				"Pagination.Previous", "Pagination.Next", "Party-Mute.Restricted-Chat-Info",
				"Party-Mute.Send-Denied", "Chat.No-Permission", "Chat.Channels.Admin",
				"Party-Connection.Offline", "Party-Connection.Offline-No-Auto-Kick",
				"Logout.Leader-Disconnected", "Logout.Disband-When-Empty", "Help-Party-Setting.Command",
				"In-Game-Only", "No-Permission", "List.Empty", "Ignore.Empty", "Add.Sent",
				"Invite.Request.Sent", "Invite.Request.Party-Notice", "Warp.Confirm")) {
			assertFalse("English: " + key, english.getString(key).isEmpty());
			assertFalse("Chinese: " + key, chinese.getString(key).isEmpty());
			assertEquals("Placeholders differ for " + key, placeholders(english.getString(key)), placeholders(chinese.getString(key)));
		}
		assertTrue(english.getString("List.Hover-Online-With-Note").contains("%note%"));
		assertTrue(chinese.getString("List.Hover-Online-With-Note").contains("%note%"));
	}

	@Test
	public void oldLanguageKeysDoNotOverrideCanonicalMessages() {
		Configuration custom = new Configuration();
		Configuration bundled = new Configuration();
		bundled.set("Party-Mute.Send-Denied", "bundled");
		custom.set("Party-Mute.Muted-2", "custom old");
		assertEquals("bundled", LanguageManager.resolveMessage(custom, bundled, bundled, "Party-Mute.Send-Denied"));
		custom.set("Party-Mute.Send-Denied", "custom new");
		assertEquals("custom new", LanguageManager.resolveMessage(custom, bundled, bundled, "Party-Mute.Send-Denied"));
		custom.set("Party-Mute.Send-Denied", null);
		assertEquals("bundled", LanguageManager.resolveMessage(new Configuration(), bundled, bundled, "Party-Mute.Send-Denied"));
		Configuration oldFile = new Configuration();
		oldFile.set("Chat.Chat-NoPermission", "older translation");
		bundled.set("Chat.No-Permission", "bundled chat permission");
		assertEquals("bundled chat permission", LanguageManager.resolveMessage(oldFile, bundled, bundled, "Chat.No-Permission"));
	}

	@Test
	public void mergedMessagesKeepNewlinesAndCanonicalKeysOnly() {
		for (String language : Arrays.asList("English", "Chinese")) {
			Configuration config = Config.getConfig(getClass().getResourceAsStream("/languages/" + language + ".yml"));
			Set<String> keys = new HashSet<>();
			collectKeys(config, "", keys);
			assertFalse(language + " contains Ingame_Only", keys.contains("Ingame_Only"));
			assertFalse(language + " contains No_Permission", keys.contains("No_Permission"));
			assertFalse(language + " contains Logout.One", keys.contains("Logout.One"));
			assertFalse(language + " contains Logout.Two", keys.contains("Logout.Two"));
			assertFalse(language + " contains Logout.Three", keys.contains("Logout.Three"));
			assertFalse(language + " contains Add.5-Minutes", keys.contains("Add.5-Minutes"));
			assertFalse(language + " contains Invite.Request.Sent.Invited", keys.contains("Invite.Request.Sent.Invited"));
			assertFalse(language + " contains Invite.Request.Sent.60-Seconds", keys.contains("Invite.Request.Sent.60-Seconds"));
			assertFalse(language + " contains Warp.Confirm-Usage", keys.contains("Warp.Confirm-Usage"));
			assertFalse(language + " contains Party-Mute.Info-Enabled", keys.contains("Party-Mute.Info-Enabled"));
			assertFalse(language + " contains Party-Mute.Muted-1", keys.contains("Party-Mute.Muted-1"));
			assertFalse(language + " contains Party-Mute.Muted-2", keys.contains("Party-Mute.Muted-2"));
			assertFalse(language + " contains List.DontHave.One", keys.contains("List.DontHave.One"));
			assertFalse(language + " contains List.DontHave.Two", keys.contains("List.DontHave.Two"));
			assertFalse(language + " contains Ignore.Havent.One", keys.contains("Ignore.Havent.One"));
			assertFalse(language + " contains Ignore.Havent.Two", keys.contains("Ignore.Havent.Two"));
			assertFalse(language + " contains Prefix.Reset", keys.contains("Prefix.Reset"));
			assertFalse(language + " contains Prefix.ResetAll", keys.contains("Prefix.ResetAll"));
			assertTrue(language + " Add.Sent has no newline", config.getString("Add.Sent").contains("\n"));
			assertTrue(language + " Logout.Leader-Disconnected has no newline", config.getString("Logout.Leader-Disconnected").contains("\n"));
			assertTrue(language + " Invite.Request.Sent has no newline", config.getString("Invite.Request.Sent").contains("\n"));
			assertTrue(language + " Add.Accept.Prefix has no leading newline", config.getString("Add.Accept.Prefix").startsWith("\n"));
			assertTrue(language + " Invite button has no leading newline", config.getString("Invite.Request.Click-here.Here").startsWith("\n"));
		}
	}

	@Test
	public void chinesePartyAndFriendTranslationsUseCanonicalPlaceholders() {
		Configuration chinese = Config.getConfig(getClass().getResourceAsStream("/languages/Chinese.yml"));
		assertTrue(chinese.getString("List.Hover-Online").contains("添加时间"));
		assertTrue(chinese.getString("List.Hover-Online").contains("最后上线"));
		assertTrue(chinese.getString("List.Hover-Online").contains("距今"));
		assertTrue(chinese.getString("List.Hover-Offline").contains("状态: &c离线"));
		assertTrue(chinese.getString("Warp.Confirm").contains("%count%"));
		assertTrue(chinese.getString("Warp.Confirm").contains("%servers%"));
		assertTrue(chinese.getString("Warp.Confirm").contains("%duration%"));
		assertTrue(chinese.getString("Logout.Leader-Disconnected").contains("%duration%"));
		assertTrue(chinese.getString("Logout.Disband-When-Empty").contains("%duration%"));
		assertFalse(chinese.getString("Logout.Leader-Disconnected").contains("%time"));
		assertFalse(chinese.getString("Logout.Disband-When-Empty").contains("%time"));
	}

	private Set<String> placeholders(String message) {
		Set<String> values = new HashSet<>();
		Matcher matcher = Pattern.compile("%([A-Za-z][A-Za-z0-9]*)%").matcher(message);
		while (matcher.find()) values.add(matcher.group());
		return values;
	}

	@Test
	public void allBuiltInLanguagePlaceholdersAreCanonicalAndMatch() {
		Map<String, String> english = values(Config.getConfig(getClass().getResourceAsStream("/languages/English.yml")));
		Map<String, String> chinese = values(Config.getConfig(getClass().getResourceAsStream("/languages/Chinese.yml")));
		assertEquals("English and Chinese message keys differ", english.keySet(), chinese.keySet());
		for (String key : english.keySet()) {
			assertCanonical(key, english.get(key));
			assertCanonical(key, chinese.get(key));
			assertEquals("Placeholders differ for " + key, placeholders(english.get(key)), placeholders(chinese.get(key)));
		}
	}

	private Map<String, String> values(Configuration section) {
		Map<String, String> values = new LinkedHashMap<>();
		collectValues(section, "", values);
		return values;
	}

	private void collectValues(Configuration section, String prefix, Map<String, String> values) {
		for (String name : section.getKeys()) {
			String path = prefix + name;
			Object value = section.get(name);
			if (value instanceof Configuration) collectValues((Configuration) value, path + ".", values);
			else if (value instanceof String) values.put(path, (String) value);
		}
	}

	private void assertCanonical(String key, String value) {
		Matcher matcher = Pattern.compile("(?<!%)%{1,2}[A-Za-z][A-Za-z0-9]*%{0,2}").matcher(value);
		while (matcher.find())
			assertTrue(key + " contains a non-canonical placeholder: " + matcher.group(),
				matcher.group().matches("%[A-Za-z][A-Za-z0-9]*%"));
	}

	@Test
	public void formatterUsesExactTokensAndSafeReplacementValues() {
		assertEquals("name=$player\\path", MessageFormatter.replace("name=%name%", MessageFormatter.values("name", "$player\\path")));
		assertEquals("A/B A/B", MessageFormatter.replace("%name% %name%", MessageFormatter.values("name", "A/B")));
		assertTrue(MessageFormatter.unresolvedExcept("%message%", new HashSet<>(Arrays.asList("message"))).isEmpty());
		assertEquals("hello %unknown%", MessageFormatter.replace("hello %message%", MessageFormatter.values("message", "%unknown%")));
		assertTrue(MessageFormatter.unresolved("legacy %name and %%name%%").contains("%name"));
		assertEquals("legacy ?", MessageFormatter.maskUnresolved("legacy %name"));
	}

	@Test
	public void fixedLanguageKeysUsedByCoreExist() throws Exception {
		Configuration english;
		try (InputStream stream = getClass().getResourceAsStream("/languages/English.yml")) {
			english = Config.getConfig(stream);
		}
		Path source = Files.exists(Paths.get("src/main/java")) ? Paths.get("src/main/java") : Paths.get("core/src/main/java");
		Pattern reference = Pattern.compile("(?:langUtils|targetLangUtils|language|pLangUtils|partyPlayerLangUtils|currentLanguage|playerLangUtils)\\.getString\\(\"([^\"]+)\"\\)");
		try (Stream<Path> files = Files.walk(source)) {
			for (Path file : (Iterable<Path>) files.filter(path -> path.toString().endsWith(".java"))::iterator) {
				Matcher matcher = reference.matcher(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
				while (matcher.find()) assertFalse(file + ": " + matcher.group(1), english.getString(matcher.group(1)).isEmpty());
			}
		}
	}
}
