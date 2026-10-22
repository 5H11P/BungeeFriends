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
    CompletableFuture<Void> promoteModerator(UUID actor, UUID target);
    CompletableFuture<Void> demoteModerator(UUID actor, UUID target);
    default CompletableFuture<Void> promote(UUID actor, UUID target) { return promoteModerator(actor, target); }
    default CompletableFuture<Void> demote(UUID actor, UUID target) { return demoteModerator(actor, target); }
    CompletableFuture<Void> disband(UUID actor);
    CompletableFuture<Void> setPrivate(UUID actor, boolean isPrivate);
    CompletableFuture<Void> setMute(UUID actor, boolean muted);
    CompletableFuture<Void> chat(UUID actor, String message);
}
