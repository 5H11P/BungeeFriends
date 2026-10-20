package net.simplyrin.bungeefriends.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class PartyCommandMatcherTest {
	@Test
	public void matchesOnlyTheConfiguredCommandAndArguments() {
		assertEquals("arena", PartyCommandMatcher.arguments("/join arena", "/join"));
		assertEquals("arena", PartyCommandMatcher.arguments("/sw join arena", "/sw join"));
		assertEquals("", PartyCommandMatcher.arguments("/join", "/join"));
		assertNull(PartyCommandMatcher.arguments("/joiner arena", "/join"));
		assertNull(PartyCommandMatcher.arguments("/sw joiner arena", "/sw join"));
	}
}
