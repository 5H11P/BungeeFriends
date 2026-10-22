package net.simplyrin.bungeefriends.utils;

import java.util.function.Function;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import net.md_5.bungee.api.chat.TextComponent;
import net.simplyrin.bungeefriends.utils.LanguageManager.LanguageUtils;

public final class PaginationMessage {

	private PaginationMessage() { }

	public static TextComponent build(LanguageUtils language, String title, String command, int page, int total) {
		return build(language::getString, language::getBuiltInEnglish, title, command, page, total);
	}

	static TextComponent build(Function<String, String> message, String title, String command, int page, int total) {
		return build(message, message, title, command, page, total);
	}

	private static TextComponent build(Function<String, String> message, Function<String, String> fallback,
			String title, String command, int page, int total) {
		Set<String> navigation = new HashSet<>(Arrays.asList("previousPage", "nextPage"));
		String template = MessageFormatter.replace(title,
				MessageFormatter.values("currentPage", page, "maxPage", total));
		if (!MessageFormatter.unresolvedExcept(template, navigation).isEmpty()) {
			template = MessageFormatter.replace(fallback.apply("List.Page"),
					MessageFormatter.values("currentPage", page, "maxPage", total));
		}
		String previous = "%previousPage%";
		String next = "%nextPage%";
		int first = template.indexOf(previous);
		int second = template.indexOf(next);
		if (first < 0 && second < 0) {
			TextComponent result = MessageBuilder.get(template);
			if (page > 1) {
				result.addExtra(" ");
				result.addExtra(button(message, fallback, true, command, page - 1, page));
			}
			if (page < total) {
				result.addExtra(" ");
				result.addExtra(button(message, fallback, false, command, page + 1, page));
			}
			return result;
		}
		TextComponent result = new TextComponent();
		int position = 0;
		while (position < template.length()) {
			int previousAt = template.indexOf(previous, position);
			int nextAt = template.indexOf(next, position);
			int at = previousAt < 0 ? nextAt : nextAt < 0 ? previousAt : Math.min(previousAt, nextAt);
			if (at < 0) {
				result.addExtra(MessageBuilder.get(template.substring(position)));
				break;
			}
			result.addExtra(MessageBuilder.get(template.substring(position, at)));
			boolean back = at == previousAt;
			if (back ? page > 1 : page < total)
				result.addExtra(button(message, fallback, back, command, back ? page - 1 : page + 1, page));
			position = at + (back ? previous.length() : next.length());
		}
		return result;
	}

	private static TextComponent button(Function<String, String> message, Function<String, String> fallback,
			boolean previous, String command, int target, int currentPage) {
		String key = previous ? "Previous" : "Next";
		String hover = MessageFormatter.replace(message.apply("Pagination." + key + "-Hover"),
				MessageFormatter.values("previousPage", target, "nextPage", target, "currentPage", currentPage));
		if (!MessageFormatter.unresolved(hover).isEmpty()) {
			hover = MessageFormatter.replace(fallback.apply("Pagination." + key + "-Hover"),
					MessageFormatter.values("previousPage", target, "nextPage", target, "currentPage", currentPage));
			hover = MessageFormatter.maskUnresolved(hover);
		}
		return MessageBuilder.get(message.apply("Pagination." + key), command + target, null, hover, false);
	}
}
