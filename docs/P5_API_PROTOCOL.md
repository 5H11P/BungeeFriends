# API and Protocol 3

The 3.0.0 API is a breaking release. The shared contract is in `api-contract`; it is shaded into both endpoint JARs. The Bungee API exposes `BungeeFriendsAPI.getInstance()` and the same query/action interfaces. The Spigot API exposes `SpigotFriendsAPI.getInstance()` and methods returning `CompletableFuture`.

Read operations use typed immutable `Friend`, `PartyMember`, `PlayerStatus`, and `Page<T>` values. Page sizes are limited to 100. Actions are explicit player operations. Party membership can only be changed through invite/accept, leave, kick, leadership transfer, and disband operations. `addToParty` and public mutable `FriendUtils`/`PartyUtils` access have been removed. Status, friend-request and party-invite toggles are explicit actions.

## Wire protocol v2

The `bungeefriends:api` channel carries queries and player actions. `bungeefriends:warp` carries the scheduled-warp event. The packet includes a version byte, kind, request UUID, actor UUID, timestamp, operation, status, and bounded string values. Version 1 packets are rejected. Requests are authenticated against the carrier player and their current server. Request IDs use a bounded replay cache; duplicate IDs with different payloads are rejected.

Page responses encode offset, limit, total count, then typed tuple values. The request page argument is `offset,limit`; valid limits are 1 through 100. The protocol retains a 30,000-byte packet ceiling and allows up to 512 values. The Spigot client validates response shape before completing the future.

Cross-server player actions are authenticated by the network carrier UUID; callers cannot specify a different actor. Party state transitions use the existing manager methods and enforce leader/member rules and invitation expiry. Administrative force actions are not exposed. The proxy-side Java API is a trusted in-process plugin interface, so installed proxy plugins must only pass UUIDs for players on whose behalf they are authorized to act.

`executePartyCommand` is separately permissioned (`friends.api.execute-command`), requires the actor to remain party leader and the target to remain an online party member, and checks command labels against both proxy and destination-server allow-lists. Completion reports proxy queue acceptance only. Warp events continue to mean scheduled delivery rather than completed teleportation.

Spigot retries requests at most three times and fails outstanding calls on player disconnect, timeout, or plugin shutdown. Futures are completed on the response-processing thread, which is normally Bukkit's primary thread; callbacks that access Bukkit state must respect that thread requirement. Do not block the primary thread with `Future.get()` or `join()`.

## Validation status

The API contract and protocol tests cover immutable paging values, protocol-version rejection, typed page response shape, and malformed values. Full reactor validation is currently blocked before compilation because the configured Maven mirror cannot resolve plugins (`maven-replacer-plugin:1.4.1`, and `maven-resources-plugin:3.3.1`). Live Bungee/Spigot/MySQL integration remains required before release.
