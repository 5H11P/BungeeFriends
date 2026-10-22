package net.simplyrin.bungeefriends.api.contract;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface PartyQueryApi {
    CompletableFuture<Page<PartyMember>> members(UUID playerId, int offset, int limit);
    CompletableFuture<Boolean> isMember(UUID playerId);
    CompletableFuture<Boolean> isLeader(UUID playerId);
    CompletableFuture<Boolean> isPrivate(UUID playerId);
    CompletableFuture<UUID> owner(UUID playerId);
}
