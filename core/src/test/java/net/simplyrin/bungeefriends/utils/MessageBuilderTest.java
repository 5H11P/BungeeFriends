package net.simplyrin.bungeefriends.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import org.junit.Test;

import net.md_5.bungee.api.chat.TextComponent;

public class MessageBuilderTest {
	@Test
	public void appendKeepsNotificationInOneComponentTree() {
		TextComponent root = MessageBuilder.get("invite");
		TextComponent click = MessageBuilder.get("click");
		TextComponent accept = MessageBuilder.get("accept");

		TextComponent result = MessageBuilder.append(root, click, accept);

		assertEquals("invite", result.getText());
		assertEquals(2, result.getExtra().size());
		assertEquals("click", result.getExtra().get(0).toPlainText());
		assertEquals("accept", result.getExtra().get(1).toPlainText());
	}

	@Test
	public void splitLinesKeepsClickableButtonOnTheNextLine() {
		TextComponent message = MessageBuilder.append(MessageBuilder.get("Invite received"),
				MessageBuilder.get("\n[ACCEPT]", "/friend accept Alex", ChatColor.GREEN, "Accept request", true));

		List<TextComponent> lines = MessageBuilder.splitLines(message);

		assertEquals(2, lines.size());
		assertEquals("Invite received", lines.get(0).toPlainText());
		assertEquals("[ACCEPT]", lines.get(1).toPlainText());
		assertNotNull(lines.get(1).getExtra().get(0).getClickEvent());
		assertEquals(ClickEvent.Action.RUN_COMMAND, lines.get(1).getExtra().get(0).getClickEvent().getAction());
		assertEquals("/friend accept Alex", lines.get(1).getExtra().get(0).getClickEvent().getValue());
		assertNotNull(lines.get(1).getExtra().get(0).getHoverEvent());
	}

	@Test
	public void splitLinesTreatsOnlyParsedNewlinesAsLineBreaks() {
		assertEquals(java.util.Arrays.asList("first", "second"), MessageBuilder.splitLines("first\r\nsecond"));
		assertEquals(java.util.Arrays.asList("literal\\ntext"), MessageBuilder.splitLines("literal\\ntext"));
		assertTrue(MessageBuilder.splitLines("first\n\nsecond").size() == 2);
	}
}
