package net.simplyrin.bungeefriends.utils;

public final class PartyCommandMatcher {
	private PartyCommandMatcher() { }

	public static String arguments(String input, String configuredCommand) {
		if (input == null || configuredCommand == null) return null;
		String command = input.trim();
		String expected = configuredCommand.trim();
		if (command.startsWith("/")) command = command.substring(1);
		if (expected.startsWith("/")) expected = expected.substring(1);
		if (command.length() < expected.length()
				|| !command.regionMatches(true, 0, expected, 0, expected.length())) return null;
		if (command.length() > expected.length() && !Character.isWhitespace(command.charAt(expected.length()))) return null;
		return command.substring(expected.length()).trim();
	}
}
