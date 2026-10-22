package net.simplyrin.bungeefriends.api.bungee;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import net.simplyrin.bungeefriends.api.contract.Friend;
import net.simplyrin.bungeefriends.api.contract.FriendActions;
import net.simplyrin.bungeefriends.api.contract.FriendQueryApi;
import net.simplyrin.bungeefriends.api.contract.Page;
import net.simplyrin.bungeefriends.api.contract.PartyActions;
import net.simplyrin.bungeefriends.api.contract.PartyMember;
import net.simplyrin.bungeefriends.api.contract.PartyQueryApi;
import net.simplyrin.bungeefriends.api.contract.PlayerActions;
import net.simplyrin.bungeefriends.api.contract.PlayerQueryApi;
import net.simplyrin.bungeefriends.api.contract.PlayerStatus;
import net.simplyrin.bungeefriends.tools.ThreadPool;

/** Public proxy-side API. All operations complete asynchronously. */
public final class BungeeFriendsAPI implements FriendQueryApi, PartyQueryApi, PlayerQueryApi, FriendActions, PartyActions, PlayerActions {
	private static volatile BungeeFriendsAPI instance;
	private final ProxyApiService service;
	private BungeeFriendsAPI(ProxyApiService service) { this.service = service; }
	static void enable(ProxyApiService service) { instance = new BungeeFriendsAPI(service); }
	static void disable() { instance = null; }
	public static BungeeFriendsAPI getInstance() {
		BungeeFriendsAPI current = instance;
		if (current == null) throw new IllegalStateException("BungeeFriends API is not enabled");
		return current;
	}
	private <T> CompletableFuture<T> submit(Supplier<T> task) {
		CompletableFuture<T> future = new CompletableFuture<>();
		try { ThreadPool.runAsync(() -> { try { future.complete(task.get()); } catch (Throwable error) { future.completeExceptionally(error); } }); }
		catch (RuntimeException error) { future.completeExceptionally(error); }
		return future;
	}
	private interface CheckedAction { void run() throws Exception; }
	private CompletableFuture<Void> action(CheckedAction task) { return submit(() -> { try { task.run(); } catch (RuntimeException e) { throw e; } catch (Exception e) { throw new IllegalStateException(e); } return null; }); }
	private static void page(int offset, int limit) {
		if (offset < 0 || limit < 1 || limit > 100) throw new IllegalArgumentException("Page limit must be between 1 and 100");
	}
	private static <T> Page<T> page(java.util.List<T> values, int offset, int limit) {
		page(offset, limit);
		int start = Math.min(offset, values.size()), end = Math.min(values.size(), start + limit);
		return new Page<>(values.subList(start, end), offset, limit, values.size());
	}
	@Override public CompletableFuture<Page<Friend>> friends(UUID id, int offset, int limit) { return submit(() -> service.friends(id, offset, limit)); }
	@Override public CompletableFuture<Page<UUID>> friendIds(UUID id, int offset, int limit) {
		return submit(() -> { java.util.List<UUID> ids = new java.util.ArrayList<>(); for (String value : service.friendIds(id)) ids.add(UUID.fromString(value)); return page(ids, offset, limit); });
	}
	@Override public CompletableFuture<Boolean> areFriends(UUID first, UUID second) { return submit(() -> service.areFriends(first, second)); }
	@Override public CompletableFuture<String> friendNickname(UUID viewer, UUID target) { return submit(() -> service.friendNickname(viewer, target)); }
	@Override public CompletableFuture<Boolean> isBestFriend(UUID viewer, UUID target) { return submit(() -> service.isBestFriend(viewer, target)); }
	@Override public CompletableFuture<Boolean> areFriendNotificationsEnabled(UUID id) { return submit(() -> service.friendNotificationsEnabled(id)); }
	@Override public CompletableFuture<Boolean> isRequestEnabled(UUID id) { return submit(() -> service.friendRequestsEnabled(id)); }
	@Override public CompletableFuture<Page<UUID>> ignoredPlayers(UUID id, int offset, int limit) {
		return submit(() -> { java.util.List<UUID> ids = new java.util.ArrayList<>(); for (String value : service.ignored(id)) ids.add(UUID.fromString(value)); return page(ids, offset, limit); });
	}
	@Override public CompletableFuture<Page<PartyMember>> members(UUID id, int offset, int limit) { return submit(() -> service.partyMembers(id, offset, limit)); }
	@Override public CompletableFuture<Boolean> isMember(UUID id) { return submit(() -> service.inParty(id)); }
	@Override public CompletableFuture<Boolean> isLeader(UUID id) { return submit(() -> service.leader(id)); }
	@Override public CompletableFuture<Boolean> isModerator(UUID id) { return submit(() -> service.moderator(id)); }
	@Override public CompletableFuture<Boolean> isPrivate(UUID id) { return submit(() -> service.isPrivate(id)); }
	@Override public CompletableFuture<UUID> owner(UUID id) { return submit(() -> service.owner(id)); }
	@Override public CompletableFuture<PlayerStatus> status(UUID id) { return submit(() -> service.status(id)); }
	@Override public CompletableFuture<Boolean> isPartyInviteEnabled(UUID id) { return submit(() -> service.partyInvitesEnabled(id)); }
	@Override public CompletableFuture<Void> requestFriend(UUID actor, UUID target) { return action(() -> service.requestFriend(actor, target)); }
	@Override public CompletableFuture<Void> acceptFriend(UUID actor, UUID requester) { return action(() -> service.acceptFriend(actor, requester)); }
	@Override public CompletableFuture<Void> removeFriend(UUID actor, UUID target) { return action(() -> service.removeFriend(actor, target)); }
	@Override public CompletableFuture<Void> ignore(UUID actor, UUID target) { return action(() -> service.ignore(actor, target)); }
	@Override public CompletableFuture<Void> unignore(UUID actor, UUID target) { return action(() -> service.unignore(actor, target)); }
	@Override public CompletableFuture<Void> setRequestEnabled(UUID actor, boolean enabled) { return action(() -> service.setFriendRequests(actor, enabled)); }
	@Override public CompletableFuture<Void> setFriendNickname(UUID actor, UUID target, String nickname) { return action(() -> service.setFriendNickname(actor, target, nickname)); }
	@Override public CompletableFuture<Void> setBestFriend(UUID actor, UUID target, boolean best) { return action(() -> service.setBestFriend(actor, target, best)); }
	@Override public CompletableFuture<Void> setFriendNotificationsEnabled(UUID actor, boolean enabled) { return action(() -> service.setFriendNotificationsEnabled(actor, enabled)); }
	@Override public CompletableFuture<Void> setStatus(UUID actor, PlayerStatus status) { return action(() -> service.setStatus(actor, status)); }
	@Override public CompletableFuture<Void> setPartyInviteEnabled(UUID actor, boolean enabled) { return action(() -> service.setPartyInvites(actor, enabled)); }
	@Override public CompletableFuture<Void> invite(UUID actor, UUID target) { return action(() -> service.invite(actor, target)); }
	@Override public CompletableFuture<Void> acceptInvite(UUID actor, UUID leader) { return action(() -> service.acceptInvite(actor, leader)); }
	@Override public CompletableFuture<Void> revokeInvite(UUID actor, UUID target) { return action(() -> service.revokeInvite(actor, target)); }
	@Override public CompletableFuture<Void> leave(UUID actor) { return action(() -> service.leave(actor)); }
	@Override public CompletableFuture<Void> kick(UUID actor, UUID target) { return action(() -> service.kick(actor, target)); }
	@Override public CompletableFuture<Void> transferLeadership(UUID actor, UUID target) { return action(() -> service.transfer(actor, target)); }
	@Override public CompletableFuture<Void> promoteModerator(UUID actor, UUID target) { return action(() -> service.promote(actor, target)); }
	@Override public CompletableFuture<Void> demoteModerator(UUID actor, UUID target) { return action(() -> service.demote(actor, target)); }
	@Override public CompletableFuture<Void> disband(UUID actor) { return action(() -> service.disband(actor)); }
	@Override public CompletableFuture<Void> setPrivate(UUID actor, boolean value) { return action(() -> service.setPrivate(actor, value)); }
	@Override public CompletableFuture<Void> setMute(UUID actor, boolean value) { return action(() -> service.setMute(actor, value)); }
	@Override public CompletableFuture<Void> chat(UUID actor, String message) { return action(() -> service.chat(actor, message)); }
}
