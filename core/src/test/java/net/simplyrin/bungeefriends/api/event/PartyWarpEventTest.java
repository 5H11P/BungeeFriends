package net.simplyrin.bungeefriends.api.event;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.Test;

public class PartyWarpEventTest {
	@Test
	public void memberSnapshotCannotChangeAfterConstruction() {
		UUID leader = UUID.randomUUID();
		UUID member = UUID.randomUUID();
		List<UUID> members = new ArrayList<>();
		members.add(member);
		PartyWarpEvent event = new PartyWarpEvent(UUID.randomUUID(), leader, "Leader", "game-1",
				PartyWarpEvent.Trigger.PARTY_COMMAND, members);
		members.clear();
		assertEquals(1, event.getMemberUuids().size());
		assertEquals(member, event.getMemberUuids().get(0));
	}
}
