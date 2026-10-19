package net.simplyrin.bungeefriends.api.bungee;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.util.Collections;
import java.util.UUID;

import org.junit.Test;

import net.simplyrin.bungeefriends.api.protocol.Protocol;
import net.simplyrin.bungeefriends.api.protocol.Protocol.Message;

public class ReplayCacheTest {
	@Test
	public void duplicateIsReplayedAndCollisionDenied() {
		ReplayCache cache = new ReplayCache(2, 60000);
		UUID id = UUID.randomUUID();
		assertEquals(ReplayCache.State.NEW, cache.reserve(id, "actor:request", 100));
		assertEquals(ReplayCache.State.PENDING, cache.reserve(id, "actor:request", 101));
		assertEquals(ReplayCache.State.COLLISION, cache.reserve(id, "different", 102));
		Message response = new Message(Protocol.RESPONSE, id, UUID.randomUUID(), "getOwner", Protocol.OK,
				Collections.singletonList(""));
		cache.complete(id, response);
		assertEquals(ReplayCache.State.REPLAY, cache.reserve(id, "actor:request", 103));
		assertSame(response, cache.response(id));
	}

	@Test
	public void capacityAndInFlightExpiry() {
		ReplayCache cache = new ReplayCache(1, 10);
		UUID first = UUID.randomUUID();
		UUID second = UUID.randomUUID();
		assertEquals(ReplayCache.State.NEW, cache.reserve(first, "write", 100));
		assertEquals(ReplayCache.State.FULL, cache.reserve(second, "write", 111));
		cache.complete(first, new Message(Protocol.RESPONSE, first, UUID.randomUUID(), "addToParty",
				Protocol.OK, Collections.singletonList("true")));
		assertEquals(ReplayCache.State.NEW, cache.reserve(second, "write", 111));
		cache.clear();
		assertEquals(ReplayCache.State.NEW, cache.reserve(first, "write", 112));
	}
}
