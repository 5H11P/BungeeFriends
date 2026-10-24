package net.simplyrin.bungeefriends.commands;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.junit.Test;

public class FriendListColorTest {
	@Test
	public void ignInheritsLuckPermsPrefixColor() {
		TextComponent display = FriendCommand.friendListDisplayName("&6[VIP] &a", "Alex", false, "Friend");
		BaseComponent name = display.getExtra().get(display.getExtra().size() - 1);
		assertEquals(ChatColor.GREEN, name.getColor());
		assertEquals("Alex", name.toPlainText());
		assertNotNull(name.getHoverEvent());
	}

	@Test
	public void colorOnlyAndBestFriendKeepNameFormatting() {
		TextComponent display = FriendCommand.friendListDisplayName("&b", "Alex*", true, null);
		BaseComponent name = display.getExtra().get(display.getExtra().size() - 1);
		assertEquals(ChatColor.AQUA, name.getColor());
		assertEquals("Alex*", name.toPlainText());
		assertEquals(true, name.isBold());
	}

	@Test
	public void ignUsesColorOfLastPrefixText() {
		TextComponent display = FriendCommand.friendListDisplayName("&7[Member] &6[VIP] ", "Alex", false, null);
		BaseComponent name = display.getExtra().get(display.getExtra().size() - 1);
		assertEquals(ChatColor.GOLD, name.getColor());
	}

	@Test
	public void resetAtEndOfPrefixResetsIgnColor() {
		TextComponent display = FriendCommand.friendListDisplayName("&c[ADMIN] &r", "Alex", false, null);
		BaseComponent name = display.getExtra().get(display.getExtra().size() - 1);
		assertEquals(ChatColor.WHITE, name.getColor());
		assertFalse(name.isBold());
	}
}
