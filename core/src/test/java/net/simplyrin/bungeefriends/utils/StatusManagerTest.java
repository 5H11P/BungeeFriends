package net.simplyrin.bungeefriends.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import net.simplyrin.bungeefriends.utils.StatusManager.Status;

public class StatusManagerTest {
	@Test
	public void parsesStatusAliasesAndRejectsExtraArguments() {
		assertEquals(Status.APPEAR_OFFLINE, Status.fromArgs(new String[] {"appear", "offline"}));
		assertEquals(Status.APPEAR_OFFLINE, Status.fromArgs(new String[] {"appear-offline"}));
		assertEquals(Status.BUSY, Status.fromArgs(new String[] {"BUSY"}));
		assertNull(Status.fromArgs(new String[] {"online", "unexpected"}));
		assertNull(Status.fromArgs(new String[] {"appear"}));
		assertNull(Status.fromArgs(new String[0]));
	}
}
