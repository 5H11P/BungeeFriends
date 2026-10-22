package net.simplyrin.bungeefriends.api.spigot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.UUID;

import org.junit.Test;

import net.simplyrin.bungeefriends.api.contract.Friend;
import net.simplyrin.bungeefriends.api.contract.Page;
import net.simplyrin.bungeefriends.api.contract.PartyMember;
import net.simplyrin.bungeefriends.api.protocol.Protocol;
import net.simplyrin.bungeefriends.api.protocol.Protocol.Message;

public class ResponseShapeTest {
	private Message response(String operation, String... values) {
		return new Message(Protocol.RESPONSE, UUID.randomUUID(), UUID.randomUUID(), operation, Protocol.OK, Arrays.asList(values));
	}

	@Test
	public void decodesTypedPageResponses() {
		UUID friendId = UUID.randomUUID();
		Page<Friend> friends = SpigotFriendsAPI.friendPage(response("friends", "0", "20", "1",
				friendId.toString(), "Alice", "Alice", "&a", "true"));
		assertEquals(1, friends.getTotal());
		assertEquals(friendId, friends.getItems().get(0).getUniqueId());
		assertTrue(friends.getItems().get(0).isOnline());

		UUID memberId = UUID.randomUUID();
		Page<PartyMember> party = SpigotFriendsAPI.partyPage(response("partyMembers", "0", "20", "1",
				memberId.toString(), "Bob", "Bob", "false", "true"));
		assertEquals(memberId, party.getItems().get(0).getUniqueId());
		assertFalse(party.getItems().get(0).isOnline());
		assertTrue(party.getItems().get(0).isLeader());
	}

	@Test
	public void rejectsMalformedPageMetadataAndBoolean() {
		try { SpigotFriendsAPI.friendPage(response("friends", "0", "20", "1", "uuid")); fail("Expected shape error"); }
		catch (IllegalStateException expected) { }
		try { SpigotFriendsAPI.booleanValue(response("areFriends", "maybe")); fail("Expected boolean error"); }
		catch (IllegalStateException expected) { }
	}
}
