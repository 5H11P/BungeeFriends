package net.simplyrin.bungeefriends.utils;

import java.util.Map;
import java.util.UUID;

public final class ReplyTargetUtils {
	private ReplyTargetUtils() { }

	public static void recordConversation(Map<UUID, UUID> targets, UUID first, UUID second) {
		if (targets == null || first == null || second == null) throw new IllegalArgumentException("Reply participants are required");
		targets.put(first, second);
		targets.put(second, first);
	}
}
