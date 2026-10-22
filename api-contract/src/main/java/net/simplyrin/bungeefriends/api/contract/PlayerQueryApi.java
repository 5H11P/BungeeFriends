package net.simplyrin.bungeefriends.api.contract;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface PlayerQueryApi {
    CompletableFuture<PlayerStatus> status(UUID playerId);
    CompletableFuture<Boolean> isPartyInviteEnabled(UUID playerId);
}
