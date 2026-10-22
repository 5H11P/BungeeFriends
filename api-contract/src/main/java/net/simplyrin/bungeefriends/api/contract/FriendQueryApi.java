package net.simplyrin.bungeefriends.api.contract;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface FriendQueryApi {
    CompletableFuture<Page<Friend>> friends(UUID playerId, int offset, int limit);
    CompletableFuture<Page<UUID>> friendIds(UUID playerId, int offset, int limit);
    CompletableFuture<Boolean> areFriends(UUID first, UUID second);
    CompletableFuture<Boolean> isRequestEnabled(UUID playerId);
    CompletableFuture<Page<UUID>> ignoredPlayers(UUID playerId, int offset, int limit);
}
