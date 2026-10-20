package net.simplyrin.bungeefriends.utils;

import static org.junit.Assert.assertEquals;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.Test;

public class ReplyTargetUtilsTest {
	@Test
	public void recordsReplyTargetsForBothPlayers() {
		Map<UUID, UUID> targets = new ConcurrentHashMap<>();
		UUID first = UUID.randomUUID();
		UUID second = UUID.randomUUID();

		ReplyTargetUtils.recordConversation(targets, first, second);

		assertEquals(second, targets.get(first));
		assertEquals(first, targets.get(second));
	}
}
