# BungeeFriends API 3

Install `BungeeFriends` and `BungeeFriends-BungeeAPI` on the proxy, and `BungeeFriend-SpigotAPI` on each game server. Compile against the matching API JAR with `provided` scope. All endpoints must use version 3.0.0; protocol v1 is incompatible.

Shared types are in `net.simplyrin.bungeefriends.api.contract`. Read operations are described by `FriendQueryApi` and `PartyQueryApi`; player actions are separate `FriendActions` and `PartyActions` interfaces. Results use immutable DTOs and `Page<T>` with a limit from 1 through 100. Implementations do not expose mutable core utility objects.

Spigot example:

```java
SpigotFriendsAPI api = SpigotFriendsAPI.getInstance();
api.friends(player, 0, 50).whenComplete((page, error) -> {
    if (error != null) {
        getLogger().warning("Friend query failed: " + error.getMessage());
        return;
    }
    page.getItems().forEach(friend -> getLogger().info(friend.getName()));
});
```

On the proxy, `BungeeFriendsAPI.getInstance()` implements the same `FriendQueryApi`, `PartyQueryApi`, `PlayerQueryApi`, and action interfaces. Proxy calls also return `CompletableFuture`; database and file work runs away from the proxy event handler.

Calls return `CompletableFuture`. Completion callbacks may run on the Bukkit primary thread; schedule Bukkit access appropriately, and never call `join()` or `get()` on the primary thread. API arguments use UUIDs and ordinary Java values.

Party membership uses invitation and acceptance. `executePartyCommand` is separately permissioned, checks command allow-lists on both proxy and destination server, and completes when the proxy accepts delivery rather than when the command finishes. `PartyWarpEvent` means a warp was scheduled, not completed.
