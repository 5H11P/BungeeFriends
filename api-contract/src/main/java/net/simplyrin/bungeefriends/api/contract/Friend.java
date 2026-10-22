package net.simplyrin.bungeefriends.api.contract;

import java.util.Objects;
import java.util.UUID;

public final class Friend {
    private final UUID uniqueId;
    private final String name;
    private final String displayName;
    private final String prefix;
    private final boolean online;

    public Friend(UUID uniqueId, String name, String displayName, String prefix, boolean online) {
        this.uniqueId = Objects.requireNonNull(uniqueId, "uniqueId");
        this.name = Objects.requireNonNull(name, "name");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.prefix = Objects.requireNonNull(prefix, "prefix");
        this.online = online;
    }
    public UUID getUniqueId() { return uniqueId; }
    public String getName() { return name; }
    public String getDisplayName() { return displayName; }
    public String getPrefix() { return prefix; }
    public boolean isOnline() { return online; }
}
