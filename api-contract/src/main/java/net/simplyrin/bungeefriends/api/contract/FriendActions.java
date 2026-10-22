package net.simplyrin.bungeefriends.api.contract;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface FriendActions {
    CompletableFuture<Void> requestFriend(UUID actor, UUID target);
    CompletableFuture<Void> acceptFriend(UUID actor, UUID requester);
    CompletableFuture<Void> removeFriend(UUID actor, UUID target);
    CompletableFuture<Void> ignore(UUID actor, UUID target);
    CompletableFuture<Void> unignore(UUID actor, UUID target);
    CompletableFuture<Void> setRequestEnabled(UUID actor, boolean enabled);
}
