package net.simplyrin.bungeefriends.api.spigot.example;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class CommandRulesTest {
	@Test
	public void matchesCompleteCommandWordsAndLegacyArgumentRule() {
		assertTrue(CommandRules.allowed("say hello", Arrays.asList("say", "bw join $args")));
		assertTrue(CommandRules.allowed("BW JOIN red", Arrays.asList("say", "bw join $args")));
		assertFalse(CommandRules.allowed("bw joiner red", Collections.singletonList("bw join $args")));
		assertFalse(CommandRules.allowed("op Alice", Collections.singletonList("say")));
	}

	@Test
	public void failsClosedForEmptyRulesAndUnsafeInputs() {
		assertFalse(CommandRules.allowed("say hello", Collections.emptyList()));
		assertFalse(CommandRules.allowed("/say hello", Collections.singletonList("say")));
		assertFalse(CommandRules.allowed("say\nstop", Collections.singletonList("say")));
		assertFalse(CommandRules.allowed("say hello", Collections.singletonList("")));
	}
}
