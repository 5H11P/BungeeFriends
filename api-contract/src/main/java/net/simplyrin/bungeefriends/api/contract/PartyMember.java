package net.simplyrin.bungeefriends.api.contract;

import java.util.Objects;
import java.util.UUID;

public final class PartyMember {
    private final UUID uniqueId;
    private final String name;
    private final String displayName;
    private final boolean online;
    private final boolean leader;
    private final boolean moderator;

    public PartyMember(UUID uniqueId, String name, String displayName, boolean online, boolean leader) {
        this(uniqueId, name, displayName, online, leader, false);
    }
    public PartyMember(UUID uniqueId, String name, String displayName, boolean online, boolean leader, boolean moderator) {
        this.uniqueId = Objects.requireNonNull(uniqueId, "uniqueId");
        this.name = Objects.requireNonNull(name, "name");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.online = online;
        this.leader = leader;
        this.moderator = moderator;
    }
    public UUID getUniqueId() { return uniqueId; }
    public String getName() { return name; }
    public String getDisplayName() { return displayName; }
    public boolean isOnline() { return online; }
    public boolean isLeader() { return leader; }
    public boolean isModerator() { return moderator; }
}
