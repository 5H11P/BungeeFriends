package net.simplyrin.bungeefriends.api.bungee;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.UUID;

import org.junit.Test;

public class ProxyPolicyTest {
	@Test
	public void bindsActorToCarrierAndCurrentServer() {
		UUID actor = UUID.randomUUID();
		assertTrue(ProxyPolicy.validSource(actor, actor, "lobby", "Lobby"));
		assertFalse(ProxyPolicy.validSource(UUID.randomUUID(), actor, "lobby", "lobby"));
		assertFalse(ProxyPolicy.validSource(actor, actor, "old-server", "new-server"));
	}

	@Test
	public void leaderMayTargetSamePartyAcrossServersOnlyWithPermission() {
		UUID leader = UUID.randomUUID();
		UUID member = UUID.randomUUID();
		assertTrue(ProxyPolicy.canExecute(leader, member, leader, true, true, true, true));
		assertFalse(ProxyPolicy.canExecute(leader, member, UUID.randomUUID(), true, true, true, true));
		assertFalse(ProxyPolicy.canExecute(leader, member, leader, false, true, true, true));
		assertFalse(ProxyPolicy.canExecute(leader, member, leader, true, false, true, true));
		assertFalse(ProxyPolicy.canExecute(leader, member, leader, true, true, false, true));
		assertFalse(ProxyPolicy.canExecute(leader, leader, leader, true, true, true, true));
		assertFalse(ProxyPolicy.canExecute(leader, member, leader, true, true, true, false));
	}

	@Test
	public void otherPartyReadNeedsAuthorization() {
		UUID actor = UUID.randomUUID();
		UUID target = UUID.randomUUID();
		UUID owner = UUID.randomUUID();
		assertTrue(ProxyPolicy.canReadParty(actor, target, owner, owner, false));
		assertFalse(ProxyPolicy.canReadParty(actor, target, owner, UUID.randomUUID(), false));
		assertTrue(ProxyPolicy.canReadParty(actor, target, owner, UUID.randomUUID(), true));
	}

	@Test
	public void partyMutationsRequireLeaderAndProtectSelfKick() {
		UUID leader = UUID.randomUUID();
		UUID member = UUID.randomUUID();
		assertTrue(ProxyPolicy.canModifyParty(leader, member, leader, false));
		assertFalse(ProxyPolicy.canModifyParty(leader, leader, leader, false));
		assertTrue(ProxyPolicy.canModifyParty(leader, leader, leader, true));
		assertFalse(ProxyPolicy.canModifyParty(member, leader, leader, true));
		assertFalse(ProxyPolicy.canModifyParty(leader, member, UUID.randomUUID(), true));
	}
}
