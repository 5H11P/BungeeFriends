package net.simplyrin.bungeefriends.api.spigot.example;

import java.util.List;

/** Matches complete command words; a rule may end with the legacy "$args" marker. */
final class CommandRules {
	private CommandRules() { }

	static boolean allowed(String command, List<String> rules) {
		if (command == null || rules == null || command.isEmpty() || command.length() > 256
				|| command.charAt(0) == '/') return false;
		for (int index = 0; index < command.length(); index++)
			if (Character.isISOControl(command.charAt(index))) return false;
		for (String configured : rules) {
			if (configured == null) continue;
			String rule = configured.trim();
			if (rule.endsWith(" $args")) rule = rule.substring(0, rule.length() - 6);
			if (rule.isEmpty() || rule.charAt(0) == '/') continue;
			if (command.equalsIgnoreCase(rule) || command.length() > rule.length()
					&& command.regionMatches(true, 0, rule, 0, rule.length())
					&& command.charAt(rule.length()) == ' ') return true;
		}
		return false;
	}
}
