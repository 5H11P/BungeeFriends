package net.simplyrin.bungeefriends.api.contract;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface PlayerActions {
    CompletableFuture<Void> setStatus(UUID actor, PlayerStatus status);
    CompletableFuture<Void> setPartyInviteEnabled(UUID actor, boolean enabled);
}
