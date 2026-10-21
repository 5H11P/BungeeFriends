package net.simplyrin.bungeefriends;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MainServerNameTest {
	@Test
	public void supportsConfiguredMatchModes() {
		assertTrue(Main.matchesServerName("lobby-1", "lobby-", "PREFIX"));
		assertTrue(Main.matchesServerName("minigame-1", "game", "CONTAINS"));
		assertTrue(Main.matchesServerName("sw-1", "sw-1", "EXACT"));
		assertTrue(Main.matchesServerName("SW-1", "sw-1", "EXACT_IGNORE_CASE"));
		assertFalse(Main.matchesServerName("Lobby-1", "lobby-", "PREFIX"));
		assertFalse(Main.matchesServerName("sw-1", "SW-1", "EXACT"));
		assertFalse(Main.matchesServerName("sw-1", "sw", "UNKNOWN"));
	}
}
