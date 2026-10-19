package net.simplyrin.bungeefriends.utils;

import java.util.function.Function;

import net.md_5.bungee.api.chat.TextComponent;
import net.simplyrin.bungeefriends.utils.LanguageManager.LanguageUtils;

public final class PaginationMessage {

	private PaginationMessage() { }

	public static TextComponent build(LanguageUtils language, String title, String command, int page, int total) {
		return build(language::getString, title, command, page, total);
	}

	static TextComponent build(Function<String, String> message, String title, String command, int page, int total) {
		String template = title.replace("%%currentPage%%", String.valueOf(page))
				.replace("%%maxPage%%", String.valueOf(total));
		String previous = "%%previousPage%%";
		String next = "%%nextPage%%";
		int first = template.indexOf(previous);
		int second = template.indexOf(next);
		if (first < 0 && second < 0) {
			TextComponent result = MessageBuilder.get(template);
			if (page > 1) {
				result.addExtra(" ");
				result.addExtra(button(message, true, command, page - 1));
			}
			if (page < total) {
				result.addExtra(" ");
				result.addExtra(button(message, false, command, page + 1));
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
				result.addExtra(button(message, back, command, back ? page - 1 : page + 1));
			position = at + (back ? previous.length() : next.length());
		}
		return result;
	}

	private static TextComponent button(Function<String, String> message, boolean previous, String command, int target) {
		String key = previous ? "Previous" : "Next";
		String hover = message.apply("Pagination." + key + "-Hover")
				.replace("%%previousPage%%", String.valueOf(target))
				.replace("%%nextPage%%", String.valueOf(target));
		return MessageBuilder.get(message.apply("Pagination." + key), command + target, null, hover, false);
	}
}
