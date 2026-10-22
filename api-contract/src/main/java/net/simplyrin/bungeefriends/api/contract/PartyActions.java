package net.simplyrin.bungeefriends.api.contract;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface PartyActions {
    CompletableFuture<Void> invite(UUID actor, UUID target);
    CompletableFuture<Void> acceptInvite(UUID actor, UUID leader);
    CompletableFuture<Void> revokeInvite(UUID actor, UUID target);
    CompletableFuture<Void> leave(UUID actor);
    CompletableFuture<Void> kick(UUID actor, UUID target);
    CompletableFuture<Void> transferLeadership(UUID actor, UUID target);
    CompletableFuture<Void> disband(UUID actor);
    CompletableFuture<Void> setPrivate(UUID actor, boolean isPrivate);
    CompletableFuture<Void> chat(UUID actor, String message);
}
