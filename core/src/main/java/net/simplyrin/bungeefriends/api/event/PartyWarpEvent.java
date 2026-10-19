package net.simplyrin.bungeefriends.api.event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import net.md_5.bungee.api.plugin.Event;

/** Asynchronous, non-cancellable notification of a scheduled party warp. */
public final class PartyWarpEvent extends Event {
	public enum Trigger { PARTY_COMMAND, LEADER_SERVER_SWITCH }

	private final UUID warpId;
	private final UUID leaderUuid;
	private final String leaderName;
	private final String targetServer;
	private final Trigger trigger;
	private final List<UUID> memberUuids;

	public PartyWarpEvent(UUID warpId, UUID leaderUuid, String leaderName, String targetServer,
			Trigger trigger, List<UUID> memberUuids) {
		if (warpId == null || leaderUuid == null || trigger == null || targetServer == null
				|| targetServer.trim().isEmpty() || memberUuids == null)
			throw new IllegalArgumentException("Warp event fields are required");
		this.warpId = warpId;
		this.leaderUuid = leaderUuid;
		this.leaderName = leaderName == null ? "" : leaderName;
		this.targetServer = targetServer;
		this.trigger = trigger;
		this.memberUuids = Collections.unmodifiableList(new ArrayList<>(memberUuids));
	}

	public UUID getWarpId() { return warpId; }
	public UUID getLeaderUuid() { return leaderUuid; }
	public String getLeaderName() { return leaderName; }
	public String getTargetServer() { return targetServer; }
	public Trigger getTrigger() { return trigger; }
	public List<UUID> getMemberUuids() { return memberUuids; }
}
