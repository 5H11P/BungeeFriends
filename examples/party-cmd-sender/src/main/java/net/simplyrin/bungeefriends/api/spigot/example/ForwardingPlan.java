package net.simplyrin.bungeefriends.api.spigot.example;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.simplyrin.bungeefriends.api.spigot.SpigotFriendsAPI.PartyMemberInfo;

/** Chooses at most one delivery per online member from an API snapshot. */
final class ForwardingPlan {
	private ForwardingPlan() { }

	static List<String> targets(String leaderId, List<Map<String, Object>> details) {
		boolean leaderConfirmed = false;
		Set<String> recipients = new LinkedHashSet<>();
		for (Map<String, Object> data : details) {
			PartyMemberInfo member = new PartyMemberInfo(data);
			if (leaderId.equals(member.getUuid())) leaderConfirmed |= member.isLeader();
			else if (member.isOnline()) recipients.add(member.getUuid());
		}
		return leaderConfirmed ? new ArrayList<>(recipients) : Collections.emptyList();
	}
}
