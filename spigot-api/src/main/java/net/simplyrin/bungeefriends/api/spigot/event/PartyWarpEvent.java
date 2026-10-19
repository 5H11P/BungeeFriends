package net.simplyrin.bungeefriends.api.spigot.event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** One event per proxy warp ID, delivered on the Bukkit primary thread. */
public final class PartyWarpEvent extends Event {
	public enum Trigger { PARTY_COMMAND, LEADER_SERVER_SWITCH }
	private static final HandlerList HANDLERS = new HandlerList();
	private final UUID warpId;
	private final UUID leaderUuid;
	private final String leaderName;
	private final String targetServer;
	private final Trigger trigger;
	private final List<UUID> memberUuids;

	public PartyWarpEvent(UUID warpId, UUID leaderUuid, String leaderName, String targetServer,
			Trigger trigger, List<UUID> memberUuids) {
		this.warpId = warpId;
		this.leaderUuid = leaderUuid;
		this.leaderName = leaderName;
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
	@Override public HandlerList getHandlers() { return HANDLERS; }
	public static HandlerList getHandlerList() { return HANDLERS; }
}
