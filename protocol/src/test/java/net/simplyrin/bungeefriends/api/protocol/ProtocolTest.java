package net.simplyrin.bungeefriends.api.protocol;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import org.junit.Test;

import net.simplyrin.bungeefriends.api.protocol.Protocol;
import net.simplyrin.bungeefriends.api.protocol.Protocol.Message;

public class ProtocolTest {
	@Test
	public void roundTripAndVersionBoundary() {
		UUID id = UUID.randomUUID();
		UUID actor = UUID.randomUUID();
		Message request = new Message(Protocol.REQUEST, id, actor, "areFriends", Protocol.OK,
				Collections.singletonList("Alice"));
		byte[] bytes = Protocol.encode(request);
		Message decoded = Protocol.decode(bytes);
		assertEquals(id, decoded.id);
		assertEquals(actor, decoded.actor);
		assertEquals("areFriends", decoded.operation);
		assertEquals(Collections.singletonList("Alice"), decoded.values);
		assertTrue(Protocol.isFresh(decoded, System.currentTimeMillis()));
		bytes[0] = 2;
		try { Protocol.decode(bytes); fail("Expected version rejection"); }
		catch (IllegalArgumentException expected) { }
	}

	@Test
	public void rejectsTruncationOversizeAndStaleRequests() {
		Message request = new Message(Protocol.REQUEST, UUID.randomUUID(), UUID.randomUUID(), "getOwner",
				Protocol.OK, Collections.emptyList());
		byte[] bytes = Protocol.encode(request);
		try { Protocol.decode(Arrays.copyOf(bytes, bytes.length - 1)); fail("Expected truncation rejection"); }
		catch (IllegalArgumentException expected) { }
		try { Protocol.decode(new byte[Protocol.MAX_BYTES + 1]); fail("Expected size rejection"); }
		catch (IllegalArgumentException expected) { }
		ByteBuffer.wrap(bytes).putLong(34, System.currentTimeMillis() - 31000);
		assertFalse(Protocol.isFresh(Protocol.decode(bytes), System.currentTimeMillis()));
	}

	@Test
	public void commandAllowlistRejectsControlsAndUnlistedLabels() {
		assertTrue(Protocol.allowedCommand("say hello", Collections.singletonList("say")));
		assertFalse(Protocol.allowedCommand("op Alice", Collections.singletonList("say")));
		assertFalse(Protocol.allowedCommand("/say hello", Collections.singletonList("say")));
		assertFalse(Protocol.allowedCommand("say\nstop", Collections.singletonList("say")));
		assertFalse(Protocol.allowedCommand("say hello", Collections.emptyList()));
	}
}
