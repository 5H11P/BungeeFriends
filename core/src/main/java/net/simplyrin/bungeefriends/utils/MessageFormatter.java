package net.simplyrin.bungeefriends.utils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Formats language templates using the canonical %identifier% syntax.
 */
public final class MessageFormatter {
	private static final Pattern PLACEHOLDER = Pattern.compile("%([A-Za-z][A-Za-z0-9]*)%");
	private static final Pattern UNRESOLVED = Pattern.compile("(?<!%)%{1,2}[A-Za-z][A-Za-z0-9]*%{0,2}");

	private MessageFormatter() { }

	public static Map<String, Object> values(Object... entries) {
		if (entries == null || entries.length == 0) return new LinkedHashMap<>();
		if ((entries.length & 1) != 0) throw new IllegalArgumentException("Placeholder entries must be key/value pairs");
		Map<String, Object> result = new LinkedHashMap<>();
		for (int i = 0; i < entries.length; i += 2) {
			if (!(entries[i] instanceof String)) throw new IllegalArgumentException("Placeholder key must be a String");
			result.put((String) entries[i], entries[i + 1]);
		}
		return result;
	}

	public static String replace(String template, Map<String, ?> values) {
		if (template == null) return "";
		Matcher matcher = PLACEHOLDER.matcher(template);
		StringBuffer result = new StringBuffer();
		while (matcher.find()) {
			String key = matcher.group(1);
			Object value = values == null ? null : values.get(key);
			if (value == null) {
				matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group()));
			} else {
				matcher.appendReplacement(result, Matcher.quoteReplacement(String.valueOf(value)));
			}
		}
		matcher.appendTail(result);
		return result.toString();
	}

	public static Set<String> unresolved(String message) {
		Set<String> result = new TreeSet<>();
		if (message == null) return result;
		Matcher matcher = UNRESOLVED.matcher(message);
		while (matcher.find()) result.add(matcher.group());
		return result;
	}

	public static Set<String> unresolvedExcept(String message, Set<String> allowed) {
		Set<String> result = unresolved(message);
		if (allowed != null) {
			for (String token : allowed) {
				result.remove("%" + token + "%");
				result.remove("%%" + token + "%%");
				result.remove("%" + token);
			}
		}
		return result;
	}

	public static String maskUnresolved(String message) {
		if (message == null) return "";
		return UNRESOLVED.matcher(message).replaceAll("?");
	}
}
