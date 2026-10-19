package net.simplyrin.bungeefriends.api.spigot.example;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class ForwardingPlanTest {
	private static Map<String, Object> member(String uuid, boolean online, boolean leader) {
		Map<String, Object> result = new HashMap<>();
		result.put("uuid", uuid);
		result.put("name", uuid);
		result.put("displayName", uuid);
		result.put("online", online);
		result.put("leader", leader);
		return result;
	}

	@Test
	public void sendsOnceToEachOnlineMemberOnlyForConfirmedLeader() {
		assertEquals(Arrays.asList("remote", "local"), ForwardingPlan.targets("leader", Arrays.asList(
				member("leader", true, true), member("remote", true, false),
				member("remote", true, false), member("offline", false, false), member("local", true, false))));
		assertEquals(Collections.emptyList(), ForwardingPlan.targets("leader", Arrays.asList(
				member("leader", true, false), member("remote", true, false))));
	}
}
