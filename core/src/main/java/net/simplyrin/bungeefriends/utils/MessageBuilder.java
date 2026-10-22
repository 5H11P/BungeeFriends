package net.simplyrin.bungeefriends.utils;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by SimplyRin on 2018/07/03.
 *
 * author: SimplyRin
 * license: LGPL v3
 * copyright: Copyright (c) 2021 SimplyRin
 */
public class MessageBuilder {
	public static TextComponent append(TextComponent message, TextComponent... extras) {
		if (message == null || extras == null) return message;
		for (TextComponent extra : extras) {
			if (extra != null) message.addExtra(extra);
		}
		return message;
	}

	public static List<TextComponent> splitLines(TextComponent message) {
		List<TextComponent> lines = new ArrayList<>();
		if (message == null) return lines;
		lines.add(new TextComponent());
		appendLines(message, lines);
		lines.removeIf(line -> line.toPlainText().isEmpty());
		return lines;
	}

	public static List<String> splitLines(String message) {
		List<String> lines = new ArrayList<>();
		if (message == null) return lines;
		for (String line : message.split("\\r?\\n", -1)) {
			if (!line.isEmpty()) lines.add(line);
		}
		return lines;
	}

	private static void appendLines(BaseComponent component, List<TextComponent> lines) {
		if (component instanceof TextComponent) {
			String text = ((TextComponent) component).getText();
			int start = 0;
			for (int i = 0; i < text.length(); i++) {
				char current = text.charAt(i);
				if (current != '\n') continue;
				int end = i > start && text.charAt(i - 1) == '\r' ? i - 1 : i;
				appendText(component, text.substring(start, end), lines.get(lines.size() - 1));
				lines.add(new TextComponent());
				start = i + 1;
			}
			appendText(component, text.substring(start), lines.get(lines.size() - 1));
		}
		if (component.getExtra() != null) {
			for (BaseComponent extra : component.getExtra()) appendLines(extra, lines);
		}
	}

	private static void appendText(BaseComponent source, String text, TextComponent line) {
		if (text.isEmpty()) return;
		TextComponent part = new TextComponent(text);
		part.copyFormatting(source);
		part.setClickEvent(source.getClickEvent());
		part.setHoverEvent(source.getHoverEvent());
		part.setInsertion(source.getInsertion());
		line.addExtra(part);
	}

	public static TextComponent get(String message) {
		return new TextComponent(ChatColor.translateAlternateColorCodes('&', message));
	}

	public static TextComponent get(String message, String url) {
		TextComponent textComponent = new TextComponent(ChatColor.translateAlternateColorCodes('&', message));
		if (url != null) {
			textComponent.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
			textComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(ChatColor.translateAlternateColorCodes('&', "&bClick to open " + url)).create()));
		}
		return textComponent;
	}

	public static TextComponent get(String message, String command, ChatColor color, String hover, boolean bold) {
		TextComponent textComponent = new TextComponent(ChatColor.translateAlternateColorCodes('&', message));

		if (command != null) {
			textComponent.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
		}
		if (hover != null) {
			textComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(ChatColor.translateAlternateColorCodes('&', hover)).create()));
		}
		if (color != null) {
			textComponent.setColor(color);
		}

		textComponent.setBold(bold);
		return textComponent;
	}

}
