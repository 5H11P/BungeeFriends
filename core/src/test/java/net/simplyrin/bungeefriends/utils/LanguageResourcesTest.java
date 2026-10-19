package net.simplyrin.bungeefriends.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
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
			try (InputStream stream = getClass().getResourceAsStream("/" + language + ".yml")) {
				Configuration config = Config.getConfig(stream);
				for (String key : Arrays.asList("Block.Self", "Block.List-Format", "Block.RemoveAll-Done",
						"Status-Command.Usage", "Status-Command.Disabled", "Status.ChatBar.Busy",
						"Status.ActionBar.AppearOffline", "Warp.Blocked", "Warp.No-Server",
						"Warp.Confirm", "Warp.Confirm-Usage", "Lang.Updater.Disabled",
						"Requests.Entry", "Note.Usage", "Party-Setting.Usage", "Party-Private.No-Permission")) {
					assertFalse(language + ": " + key, config.getString(key).isEmpty());
				}
				Set<String> keys = new HashSet<>();
				collectKeys(config, "", keys);
				if (englishKeys == null) englishKeys = keys;
				else assertEquals("Chinese and English message keys differ", englishKeys, keys);
			}
		}
		try (InputStream stream = getClass().getResourceAsStream("/available.yml")) {
			assertEquals(Arrays.asList("Chinese", "English"), Config.getConfig(stream).getStringList("Langs"));
		}
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
	public void fixedLanguageKeysUsedByCoreExist() throws Exception {
		Configuration english;
		try (InputStream stream = getClass().getResourceAsStream("/English.yml")) {
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
