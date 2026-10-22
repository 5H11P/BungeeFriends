# BungeeFriends API 3

This document is the integration reference for BungeeFriends 3.0.0. It describes
the public Java API exposed by the proxy and Spigot endpoint plugins. The API is
a breaking change from the old `BungeeFriends.getAPI()` and
`sendCustomRequest(...)` interfaces.

## Installation and dependencies

Install the plugins as follows:

| Runtime | Required plugins |
| --- | --- |
| BungeeCord/Waterfall proxy | `BungeeFriends`, `BungeeFriends-BungeeAPI` |
| Every Spigot backend | `BungeeFriend-SpigotAPI` |

Use the same `3.0.0` version for every endpoint. Protocol v1 endpoints are not
compatible with API 3. The endpoint JARs contain the `protocol` and
`api-contract` classes, so those modules are compile-time dependencies and are
not installed as separate server plugins.

For Maven consumers, compile against the endpoint JAR with `provided` scope.
The shared contract artifact is:

```xml
<dependency>
  <groupId>net.simplyrin.bungeefriends.api</groupId>
  <artifactId>BungeeFriends-API-Contract</artifactId>
  <version>3.0.0</version>
  <scope>provided</scope>
</dependency>
```

Most plugins should compile against `BungeeFriend-SpigotAPI` on a backend or
`BungeeFriends-BungeeAPI` in a proxy plugin instead of declaring the contract
directly. Do not install both the normal and `jar-with-dependencies` artifact
of the same endpoint.

## Entry points and lifecycle

### Spigot backend

```java
import net.simplyrin.bungeefriends.api.spigot.SpigotFriendsAPI;

SpigotFriendsAPI api = SpigotFriendsAPI.getInstance();
```

`getInstance()` succeeds only while `BungeeFriend-SpigotAPI` is enabled. A
call made while the plugin is disabled, after shutdown, or before startup
throws `IllegalStateException`.

Spigot exposes convenience overloads that take an online Bukkit `Player`, and
also implements the shared UUID-based interfaces. The `Player` overload is
usually the simplest form for a player command. UUID calls are useful when a
caller already uses the shared contract, but the actor must still be online:
the endpoint resolves the UUID on the Bukkit primary thread before sending the
request.

### Proxy

```java
import net.simplyrin.bungeefriends.api.bungee.BungeeFriendsAPI;

BungeeFriendsAPI api = BungeeFriendsAPI.getInstance();
```

The proxy API is an in-process trusted API. It accepts UUIDs and does not infer
the caller's authorization from the Java call site. A proxy plugin must only
pass an actor UUID for a player it is authorized to act for. The proxy API
instance is available only while `BungeeFriends-BungeeAPI` is enabled.

## Async and thread model

Every public operation returns `CompletableFuture<T>`. The call starts an
asynchronous operation; it does not return a snapshot synchronously.

```java
api.friends(player, 0, 50).whenComplete((page, error) -> {
    if (error != null) {
        getLogger().warning("Friend query failed: " + error.getMessage());
        return;
    }
    page.getItems().forEach(friend ->
        getLogger().info(friend.getName())
    );
});
```

On Spigot, request setup, plugin-message processing, response decoding, and
completion callbacks normally run on the Bukkit primary thread. Do not call
`Future.get()` or `join()` there. If a callback runs on another executor and
needs Bukkit objects, schedule that access with the Bukkit scheduler.

On the proxy, core data and file/database work is submitted to the BungeeFriends
thread pool. Callbacks must still be written as asynchronous code; their
execution thread is not a stable API guarantee.

Cancellation cancels the client-side future and releases its pending request,
but it cannot undo a mutation already accepted by the proxy. Spigot retries a
request at most twice after the initial send (three attempts total).

## Shared API interfaces

The interfaces and DTOs are in
`net.simplyrin.bungeefriends.api.contract`. Both endpoint facades implement all
six interfaces, so code can depend on a narrow interface when appropriate.

### FriendQueryApi (read operations)

```java
CompletableFuture<Page<Friend>> friends(UUID playerId, int offset, int limit);
CompletableFuture<Page<UUID>> friendIds(UUID playerId, int offset, int limit);
CompletableFuture<Boolean> areFriends(UUID first, UUID second);
CompletableFuture<String> friendNickname(UUID viewer, UUID target);
CompletableFuture<Boolean> isBestFriend(UUID viewer, UUID target);
CompletableFuture<Boolean> areFriendNotificationsEnabled(UUID playerId);
CompletableFuture<Boolean> isRequestEnabled(UUID playerId);
CompletableFuture<Page<UUID>> ignoredPlayers(UUID playerId, int offset, int limit);
```

`friends` returns typed friend details. `friendIds` returns only IDs.
`ignoredPlayers` returns the IDs ignored by the player.

### PartyQueryApi (read operations)

```java
CompletableFuture<Page<PartyMember>> members(UUID playerId, int offset, int limit);
CompletableFuture<Boolean> isMember(UUID playerId);
CompletableFuture<Boolean> isLeader(UUID playerId);
CompletableFuture<Boolean> isModerator(UUID playerId);
CompletableFuture<Boolean> isPrivate(UUID playerId);
CompletableFuture<UUID> owner(UUID playerId);
```

`members` returns the party containing the player. `owner` returns `null` when
the player is not in a party. `isPrivate` returns `false` when there is no
party.

### PlayerQueryApi (read operations)

```java
CompletableFuture<PlayerStatus> status(UUID playerId);
CompletableFuture<Boolean> isPartyInviteEnabled(UUID playerId);
```

`PlayerStatus` is one of `ONLINE`, `AWAY`, `BUSY`, or `APPEAR_OFFLINE`.

### FriendActions (mutating operations)

```java
CompletableFuture<Void> requestFriend(UUID actor, UUID target);
CompletableFuture<Void> acceptFriend(UUID actor, UUID requester);
CompletableFuture<Void> removeFriend(UUID actor, UUID target);
CompletableFuture<Void> ignore(UUID actor, UUID target);
CompletableFuture<Void> unignore(UUID actor, UUID target);
CompletableFuture<Void> setRequestEnabled(UUID actor, boolean enabled);
CompletableFuture<Void> setFriendNickname(UUID actor, UUID target, String nickname);
CompletableFuture<Void> setBestFriend(UUID actor, UUID target, boolean best);
CompletableFuture<Void> setFriendNotificationsEnabled(UUID actor, boolean enabled);
```

These methods modify friend requests, friend relationships, the ignore list, or
the actor's request preference. They complete when the proxy has applied the
operation, or exceptionally when the domain rule rejects it.

### PartyActions (mutating operations)

```java
CompletableFuture<Void> invite(UUID actor, UUID target);
CompletableFuture<Void> acceptInvite(UUID actor, UUID leader);
CompletableFuture<Void> revokeInvite(UUID actor, UUID target);
CompletableFuture<Void> leave(UUID actor);
CompletableFuture<Void> kick(UUID actor, UUID target);
CompletableFuture<Void> transferLeadership(UUID actor, UUID target);
CompletableFuture<Void> promoteModerator(UUID actor, UUID target);
CompletableFuture<Void> demoteModerator(UUID actor, UUID target);
CompletableFuture<Void> disband(UUID actor);
CompletableFuture<Void> setPrivate(UUID actor, boolean isPrivate);
CompletableFuture<Void> setMute(UUID actor, boolean muted);
CompletableFuture<Void> chat(UUID actor, String message);
```

Party membership changes use invitation and acceptance. Leadership-only rules
are enforced by the core: a leader may revoke invitations, kick members,
transfer leadership, change privacy, or disband; a member may leave. There is
no public force-add or administrative party mutation method.

### PlayerActions (mutating operations)

```java
CompletableFuture<Void> setStatus(UUID actor, PlayerStatus status);
CompletableFuture<Void> setPartyInviteEnabled(UUID actor, boolean enabled);
```

## Spigot convenience methods

`SpigotFriendsAPI` additionally provides the same operations with an online
`Player` actor and names that match Bukkit usage:

| Operation | Spigot convenience method |
| --- | --- |
| Friend list | `friends(Player, offset, limit)` |
| Friend IDs | `friendIds(Player, offset, limit)` |
| Friend check | `areFriends(Player, targetUuid)` |
| Private nickname | `friendNickname(Player, targetUuid)` / `setFriendNickname(Player, targetUuid, nickname)` |
| Best friend | `isBestFriend(Player, targetUuid)` / `setBestFriend(Player, targetUuid, best)` |
| Friend notifications | `areFriendNotificationsEnabled(Player)` / `setFriendNotificationsEnabled(Player, enabled)` |
| Friend request preference | `isRequestEnabled(Player)` / `setFriendRequestsEnabled(Player, enabled)` |
| Party invite preference | `isPartyInviteEnabled(Player)` / `setPartyInviteEnabled(Player, enabled)` |
| Status | `status(Player)` / `setStatus(Player, PlayerStatus)` |
| Ignored IDs | `ignoredPlayers(Player, offset, limit)` |
| Party members | `partyMembers(Player, offset, limit)` |
| Party checks | `isPartyMember(Player)`, `isPartyLeader(Player)`, `isPartyModerator(Player)`, `isPartyPrivate(Player)`, `partyOwner(Player)` |
| Friend actions | `requestFriend`, `acceptFriend`, `removeFriend`, `ignore`, `unignore` |
| Party actions | `inviteToParty`, `acceptPartyInvite`, `revokePartyInvite`, `leaveParty`, `kickFromParty`, `transferPartyLeadership`, `promotePartyModerator`, `demotePartyModerator`, `disbandParty`, `setPartyPrivate`, `setPartyMute`, `sendPartyChat` |

All rows return the same `CompletableFuture` result types as the shared
interfaces. The UUID-based names (`members`, `isMember`, `owner`, `invite`,
`chat`, and so on) are available through the implemented contract interfaces.

## Pagination and immutable DTOs

All paged methods require `offset >= 0` and `1 <= limit <= 100`. An invalid
page argument throws `IllegalArgumentException` before a request is sent.

`Page<T>` contains:

```java
List<T> getItems();
int getOffset();
int getLimit();
int getTotal();
boolean hasNext();
```

The item list is an immutable defensive copy. `hasNext()` is based on
`offset + items.size() < total`; an empty page beyond the end is valid.

`Friend` contains `getUniqueId()`, `getName()`, `getDisplayName()`,
`getPrefix()`, and `isOnline()`.

`Friend.getDisplayName()` is resolved for the requesting player. It includes that
player's private nickname and a trailing `*` when one is configured; the
original `getName()` remains unchanged.

`PartyMember` contains `getUniqueId()`, `getName()`, `getDisplayName()`,
`isOnline()`, `isLeader()`, and `isModerator()`.

The DTOs are immutable snapshots and do not expose `FriendUtils`, `PartyUtils`,
database objects, or mutable core collections.

## Errors and result handling

Always handle the exceptional completion path:

```java
api.requestFriend(player, target).whenComplete((ignored, error) -> {
    if (error == null) return;
    Throwable cause = error;
    if (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null)
        cause = cause.getCause();
    getLogger().warning("Friend request failed: " + cause.getMessage());
});
```

Typical failures include an offline actor, endpoint shutdown, timeout, invalid
arguments, an expired or missing invitation, a domain rule violation, or a
permission denial. On Spigot, transport responses are represented by
`BungeeFriendsAPISpigot.ApiException`; call `getStatus()` to inspect the
protocol status. The defined statuses are:

| Status | Meaning |
| --- | --- |
| `INVALID` | Malformed arguments or invalid page/operation input |
| `DENIED` | Authorization, lifecycle, or current-state check rejected the request |
| `NOT_FOUND` | Reserved for a missing resource |
| `ERROR` | Proxy, transport, or endpoint failure |
| `UNSUPPORTED` | Operation is not implemented by the endpoint |

The proxy-side in-process facade may complete with the underlying domain
exception rather than `ApiException`; callers should handle the future
exceptionally instead of matching only one exception class.

## Party command delivery

The Spigot facade exposes one additional operation:

```java
CompletableFuture<Boolean> executePartyCommand(
    Player leader, UUID target, String commandWithoutSlash);
```

Example:

```java
api.executePartyCommand(leader, targetUuid, "say hello")
   .thenAccept(queued -> getLogger().info("Accepted: " + queued));
```

The command must not begin with `/`. The operation requires the actor to still
be the party leader, the target to be an online member, and the actor to have
`friends.api.execute-command`. The command label must be present in
`Allowed-Commands` in the proxy API configuration and in `allowed-commands` in
the target Spigot API configuration. Both allow-lists are checked.

`true` means the proxy accepted delivery to the target server. It does not
confirm that the target server executed the command or that the command
succeeded.

## PartyWarpEvent

The scheduled warp notification is available on both endpoints:

```text
net.simplyrin.bungeefriends.api.event.PartyWarpEvent
net.simplyrin.bungeefriends.api.spigot.event.PartyWarpEvent
```

The Spigot event is delivered on the Bukkit primary thread. The proxy event is
an asynchronous, non-cancellable notification. Both expose:

```java
getWarpId();
getLeaderUuid();
getLeaderName();
getTargetServer();
getTrigger();              // PARTY_COMMAND or LEADER_SERVER_SWITCH
getMemberUuids();
```

The event means that a warp was scheduled and a member snapshot was emitted;
it is not confirmation that every member changed server or completed a
teleport. The member list is immutable.

## Wire protocol and security boundary

Normal consumers must use the typed facades and must not construct plugin
messages. Internally, `bungeefriends:api` carries protocol v2 requests,
responses, and command delivery; `bungeefriends:warp` carries warp events.
Packets have a 30,000-byte limit, bounded values, timestamps, request IDs, and
replay protection. Requests are authenticated against the carrier player and
current server. A caller cannot put a different actor UUID into a Spigot
request.

`BungeeFriendsAPISpigot.request(Player, String, String...)` is a low-level
transport method retained by the endpoint implementation. It accepts raw
operation names and is not a stable application API; use `SpigotFriendsAPI`
instead.

## Compatibility checklist

- Upgrade `BungeeFriends`, `BungeeFriends-BungeeAPI`, and every
  `BungeeFriend-SpigotAPI` together.
- Recompile consumers against the 3.0.0 endpoint JAR.
- Replace `BungeeFriends.getAPI()`, `sendCustomRequest(...)`, and mutable
  `FriendUtils`/`PartyUtils` access with the typed interfaces.
- Do not block the Bukkit primary thread on a future.
- Configure both command allow-lists before using `executePartyCommand`.
- Test endpoint startup, player disconnect, timeout, permission rejection, and
  proxy/backend version mismatch before production rollout.
