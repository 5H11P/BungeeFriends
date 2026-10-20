package net.simplyrin.bungeefriends.commands;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PartyListCommandTest {

	@Test
	public void delegationRequiresInitializedPartyCommand() {
		assertFalse(PartyListCommand.canDelegate(null));
		assertTrue(PartyListCommand.canDelegate(new PartyCommand(null)));
	}
}
