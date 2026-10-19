package net.simplyrin.bungeefriends.api.spigot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import org.junit.Test;

import net.simplyrin.bungeefriends.api.protocol.Protocol;
import net.simplyrin.bungeefriends.api.protocol.Protocol.Message;

public class ResponseShapeTest {
	private Message response(String operation, String... values) {
		return new Message(Protocol.RESPONSE, UUID.randomUUID(), UUID.randomUUID(), operation,
				Protocol.OK, Arrays.asList(values));
	}

	@Test
	public void booleanAndDetailResponsesHaveTypedResults() {
		assertTrue(SpigotFriendsAPI.booleanValue(response("areFriends", "true")));
		assertFalse(SpigotFriendsAPI.booleanValue(response("isInParty", "false")));
		Map<String, Object> friend = SpigotFriendsAPI.details(response("getFriendDetailList",
				UUID.randomUUID().toString(), "Alice", "Alice", "&a", "true"), false).get(0);
		assertEquals("Alice", friend.get("name"));
		assertEquals(Boolean.TRUE, friend.get("online"));
		Map<String, Object> party = SpigotFriendsAPI.details(response("getPartyMemberDetailList",
				UUID.randomUUID().toString(), "Bob", "Bob", "false", "true"), true).get(0);
		assertEquals(Boolean.TRUE, party.get("leader"));
		assertEquals(Boolean.FALSE, party.get("online"));
		assertEquals(Collections.emptyList(), SpigotFriendsAPI.details(response("getFriendDetailList"), false));
	}

	@Test
	public void malformedResponseIsExceptional() {
		try { SpigotFriendsAPI.booleanValue(response("areFriends", "maybe")); fail("Expected type error"); }
		catch (IllegalStateException expected) { }
		try { SpigotFriendsAPI.details(response("getFriendDetailList", "uuid"), false); fail("Expected shape error"); }
		catch (IllegalStateException expected) { }
	}
}
