package net.simplyrin.bungeefriends.api.spigot;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.bukkit.entity.Player;

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
import net.simplyrin.bungeefriends.api.protocol.Protocol;
import net.simplyrin.bungeefriends.api.protocol.Protocol.Message;

/** Asynchronous, typed API for plugins running on a Spigot backend. */
public final class SpigotFriendsAPI implements FriendQueryApi, PartyQueryApi, PlayerQueryApi, FriendActions, PartyActions, PlayerActions {
	private static final int MAX_PAGE_SIZE = 100;
	private static final SpigotFriendsAPI INSTANCE = new SpigotFriendsAPI();
	private SpigotFriendsAPI() { }

	public static SpigotFriendsAPI getInstance() {
		BungeeFriendsAPISpigot.getInstance();
		return INSTANCE;
	}

	private BungeeFriendsAPISpigot plugin() { return BungeeFriendsAPISpigot.getInstance(); }
	private static <T> CompletableFuture<T> map(CompletableFuture<Message> source, Function<Message, T> decode) {
		CompletableFuture<T> result = new CompletableFuture<>();
		source.whenComplete((message, error) -> {
			if (error != null) result.completeExceptionally(error);
			else try { result.complete(decode.apply(message)); }
			catch (RuntimeException e) { result.completeExceptionally(e); }
		});
		result.whenComplete((value, error) -> { if (result.isCancelled()) source.cancel(true); });
		return result;
	}
	private CompletableFuture<Message> query(Player player, String operation, String... args) {
		return plugin().request(player, operation, args);
	}
	private CompletableFuture<Message> query(UUID actor, String operation, String... args) {
		if (actor == null) throw new IllegalArgumentException("Actor UUID is required");
		CompletableFuture<Message> result = new CompletableFuture<>();
		Runnable select = () -> {
			try {
				Player player = org.bukkit.Bukkit.getPlayer(actor);
				if (player == null || !player.isOnline()) { result.completeExceptionally(new IllegalStateException("Request player is offline")); return; }
				query(player, operation, args).whenComplete((message, error) -> {
					if (error == null) result.complete(message); else result.completeExceptionally(error);
				});
			} catch (RuntimeException e) { result.completeExceptionally(e); }
		};
		if (org.bukkit.Bukkit.isPrimaryThread()) select.run();
		else try { org.bukkit.Bukkit.getScheduler().runTask(plugin(), select); }
		catch (RuntimeException e) { result.completeExceptionally(e); }
		return result;
	}
	private CompletableFuture<Void> action(Player player, String operation, String... args) {
		return map(plugin().request(player, operation, args), message -> {
			if (!message.values.isEmpty()) throw new IllegalStateException("Unexpected action response");
			return null;
		});
	}
	private static String id(UUID uuid) { return uuid.toString(); }
	private static String pageArgs(int offset, int limit) {
		validatePage(offset, limit);
		return offset + "," + limit;
	}
	private static void validatePage(int offset, int limit) {
		if (offset < 0 || limit < 1 || limit > MAX_PAGE_SIZE) throw new IllegalArgumentException("Page must have offset >= 0 and limit in [1, 100]");
	}

	public CompletableFuture<Page<Friend>> friends(Player player, int offset, int limit) {
		return map(query(player, "friends", pageArgs(offset, limit)), SpigotFriendsAPI::friendPage);
	}
	public CompletableFuture<Page<UUID>> friendIds(Player player, int offset, int limit) {
		return map(query(player, "friendIds", pageArgs(offset, limit)), SpigotFriendsAPI::uuidPage);
	}
	public CompletableFuture<Boolean> areFriends(Player player, UUID target) {
		return map(query(player, "areFriends", id(target)), SpigotFriendsAPI::booleanValue);
	}
	public CompletableFuture<Boolean> isRequestEnabled(Player player) {
		return map(query(player, "isRequestEnabled"), SpigotFriendsAPI::booleanValue);
	}
	public CompletableFuture<Boolean> isPartyInviteEnabled(Player player) {
		return map(query(player, "isPartyInviteEnabled"), SpigotFriendsAPI::booleanValue);
	}
	public CompletableFuture<PlayerStatus> status(Player player) {
		return map(query(player, "status"), message -> PlayerStatus.valueOf(single(message)));
	}
	public CompletableFuture<Page<UUID>> ignoredPlayers(Player player, int offset, int limit) {
		return map(query(player, "ignoredPlayers", pageArgs(offset, limit)), SpigotFriendsAPI::uuidPage);
	}
	public CompletableFuture<Page<PartyMember>> partyMembers(Player player, int offset, int limit) {
		return map(query(player, "partyMembers", pageArgs(offset, limit)), SpigotFriendsAPI::partyPage);
	}
	public CompletableFuture<Boolean> isPartyMember(Player player) {
		return map(query(player, "isPartyMember"), SpigotFriendsAPI::booleanValue);
	}
	public CompletableFuture<Boolean> isPartyLeader(Player player) {
		return map(query(player, "isPartyLeader"), SpigotFriendsAPI::booleanValue);
	}
	public CompletableFuture<Boolean> isPartyPrivate(Player player) {
		return map(query(player, "isPartyPrivate"), SpigotFriendsAPI::booleanValue);
	}
	public CompletableFuture<UUID> partyOwner(Player player) {
		return map(query(player, "partyOwner"), message -> {
			String value = single(message);
			return value.isEmpty() ? null : UUID.fromString(value);
		});
	}

	public CompletableFuture<Void> requestFriend(Player actor, UUID target) { return action(actor, "requestFriend", id(target)); }
	public CompletableFuture<Void> acceptFriend(Player actor, UUID requester) { return action(actor, "acceptFriend", id(requester)); }
	public CompletableFuture<Void> removeFriend(Player actor, UUID target) { return action(actor, "removeFriend", id(target)); }
	public CompletableFuture<Void> ignore(Player actor, UUID target) { return action(actor, "ignore", id(target)); }
	public CompletableFuture<Void> unignore(Player actor, UUID target) { return action(actor, "unignore", id(target)); }
	public CompletableFuture<Void> setFriendRequestsEnabled(Player actor, boolean enabled) { return action(actor, "setRequestEnabled", Boolean.toString(enabled)); }
	public CompletableFuture<Void> setPartyInviteEnabled(Player actor, boolean enabled) { return action(actor, "setPartyInviteEnabled", Boolean.toString(enabled)); }
	public CompletableFuture<Void> setStatus(Player actor, PlayerStatus status) {
		if (status == null) throw new IllegalArgumentException("Status is required");
		return action(actor, "setStatus", status.name());
	}
	public CompletableFuture<Void> inviteToParty(Player actor, UUID target) { return action(actor, "inviteParty", id(target)); }
	public CompletableFuture<Void> acceptPartyInvite(Player actor, UUID leader) { return action(actor, "acceptPartyInvite", id(leader)); }
	public CompletableFuture<Void> revokePartyInvite(Player actor, UUID target) { return action(actor, "revokePartyInvite", id(target)); }
	public CompletableFuture<Void> leaveParty(Player actor) { return action(actor, "leaveParty"); }
	public CompletableFuture<Void> kickFromParty(Player actor, UUID target) { return action(actor, "kickPartyMember", id(target)); }
	public CompletableFuture<Void> transferPartyLeadership(Player actor, UUID target) { return action(actor, "transferPartyLeadership", id(target)); }
	public CompletableFuture<Void> disbandParty(Player actor) { return action(actor, "disbandParty"); }
	public CompletableFuture<Void> setPartyPrivate(Player actor, boolean isPrivate) { return action(actor, "setPartyPrivate", Boolean.toString(isPrivate)); }
	public CompletableFuture<Void> sendPartyChat(Player actor, String message) { return action(actor, "partyChat", message); }
	/** Completes when the proxy accepts command delivery, not when the target command finishes. */
	public CompletableFuture<Boolean> executePartyCommand(Player leader, UUID target, String commandWithoutSlash) {
		return map(plugin().request(leader, "executeCommand", id(target), commandWithoutSlash),
				message -> "queued".equals(single(message)));
	}

	@Override public CompletableFuture<Page<Friend>> friends(UUID actor, int offset, int limit) {
		return map(query(actor, "friends", pageArgs(offset, limit)), SpigotFriendsAPI::friendPage);
	}
	@Override public CompletableFuture<Page<UUID>> friendIds(UUID actor, int offset, int limit) {
		return map(query(actor, "friendIds", pageArgs(offset, limit)), SpigotFriendsAPI::uuidPage);
	}
	@Override public CompletableFuture<Boolean> areFriends(UUID actor, UUID target) {
		return map(query(actor, "areFriends", id(target)), SpigotFriendsAPI::booleanValue);
	}
	@Override public CompletableFuture<Boolean> isRequestEnabled(UUID actor) {
		return map(query(actor, "isRequestEnabled"), SpigotFriendsAPI::booleanValue);
	}
	@Override public CompletableFuture<Boolean> isPartyInviteEnabled(UUID actor) {
		return map(query(actor, "isPartyInviteEnabled"), SpigotFriendsAPI::booleanValue);
	}
	@Override public CompletableFuture<PlayerStatus> status(UUID actor) {
		return map(query(actor, "status"), message -> PlayerStatus.valueOf(single(message)));
	}
	@Override public CompletableFuture<Page<UUID>> ignoredPlayers(UUID actor, int offset, int limit) {
		return map(query(actor, "ignoredPlayers", pageArgs(offset, limit)), SpigotFriendsAPI::uuidPage);
	}
	@Override public CompletableFuture<Page<PartyMember>> members(UUID actor, int offset, int limit) {
		return map(query(actor, "partyMembers", pageArgs(offset, limit)), SpigotFriendsAPI::partyPage);
	}
	@Override public CompletableFuture<Boolean> isMember(UUID actor) {
		return map(query(actor, "isPartyMember"), SpigotFriendsAPI::booleanValue);
	}
	@Override public CompletableFuture<Boolean> isLeader(UUID actor) {
		return map(query(actor, "isPartyLeader"), SpigotFriendsAPI::booleanValue);
	}
	@Override public CompletableFuture<Boolean> isPrivate(UUID actor) {
		return map(query(actor, "isPartyPrivate"), SpigotFriendsAPI::booleanValue);
	}
	@Override public CompletableFuture<UUID> owner(UUID actor) {
		return map(query(actor, "partyOwner"), message -> { String value = single(message); return value.isEmpty() ? null : UUID.fromString(value); });
	}
	@Override public CompletableFuture<Void> requestFriend(UUID actor, UUID target) { return action(actor, "requestFriend", id(target)); }
	@Override public CompletableFuture<Void> acceptFriend(UUID actor, UUID requester) { return action(actor, "acceptFriend", id(requester)); }
	@Override public CompletableFuture<Void> removeFriend(UUID actor, UUID target) { return action(actor, "removeFriend", id(target)); }
	@Override public CompletableFuture<Void> ignore(UUID actor, UUID target) { return action(actor, "ignore", id(target)); }
	@Override public CompletableFuture<Void> unignore(UUID actor, UUID target) { return action(actor, "unignore", id(target)); }
	@Override public CompletableFuture<Void> setRequestEnabled(UUID actor, boolean enabled) { return action(actor, "setRequestEnabled", Boolean.toString(enabled)); }
	@Override public CompletableFuture<Void> setPartyInviteEnabled(UUID actor, boolean enabled) { return action(actor, "setPartyInviteEnabled", Boolean.toString(enabled)); }
	@Override public CompletableFuture<Void> setStatus(UUID actor, PlayerStatus status) {
		if (status == null) throw new IllegalArgumentException("Status is required");
		return action(actor, "setStatus", status.name());
	}
	@Override public CompletableFuture<Void> invite(UUID actor, UUID target) { return action(actor, "inviteParty", id(target)); }
	@Override public CompletableFuture<Void> acceptInvite(UUID actor, UUID leader) { return action(actor, "acceptPartyInvite", id(leader)); }
	@Override public CompletableFuture<Void> revokeInvite(UUID actor, UUID target) { return action(actor, "revokePartyInvite", id(target)); }
	@Override public CompletableFuture<Void> leave(UUID actor) { return action(actor, "leaveParty"); }
	@Override public CompletableFuture<Void> kick(UUID actor, UUID target) { return action(actor, "kickPartyMember", id(target)); }
	@Override public CompletableFuture<Void> transferLeadership(UUID actor, UUID target) { return action(actor, "transferPartyLeadership", id(target)); }
	@Override public CompletableFuture<Void> disband(UUID actor) { return action(actor, "disbandParty"); }
	@Override public CompletableFuture<Void> setPrivate(UUID actor, boolean value) { return action(actor, "setPartyPrivate", Boolean.toString(value)); }
	@Override public CompletableFuture<Void> chat(UUID actor, String message) { return action(actor, "partyChat", message); }
	private CompletableFuture<Void> action(UUID actor, String operation, String... args) {
		return map(query(actor, operation, args), message -> {
			if (!message.values.isEmpty()) throw new IllegalStateException("Unexpected action response");
			return null;
		});
	}

	static Page<Friend> friendPage(Message message) {
		List<String> values = message.values;
		PageHeader header = header(values, 5);
		List<Friend> items = new ArrayList<>();
		for (int i = 3; i < values.size(); i += 5)
			items.add(new Friend(UUID.fromString(values.get(i)), values.get(i + 1), values.get(i + 2), values.get(i + 3), parseBoolean(values.get(i + 4))));
		return new Page<>(items, header.offset, header.limit, header.total);
	}
	static Page<PartyMember> partyPage(Message message) {
		List<String> values = message.values;
		PageHeader header = header(values, 5);
		List<PartyMember> items = new ArrayList<>();
		for (int i = 3; i < values.size(); i += 5)
			items.add(new PartyMember(UUID.fromString(values.get(i)), values.get(i + 1), values.get(i + 2), parseBoolean(values.get(i + 3)), parseBoolean(values.get(i + 4))));
		return new Page<>(items, header.offset, header.limit, header.total);
	}
	private static Page<UUID> uuidPage(Message message) {
		List<String> values = message.values;
		PageHeader header = header(values, 1);
		List<UUID> items = new ArrayList<>();
		for (int i = 3; i < values.size(); i++) items.add(UUID.fromString(values.get(i)));
		return new Page<>(items, header.offset, header.limit, header.total);
	}
	private static PageHeader header(List<String> values, int tupleSize) {
		if (values.size() < 3 || (values.size() - 3) % tupleSize != 0) throw new IllegalStateException("Unexpected page response shape");
		try {
			int offset = Integer.parseInt(values.get(0)), limit = Integer.parseInt(values.get(1)), total = Integer.parseInt(values.get(2));
			if (offset < 0 || limit < 1 || total < 0 || values.size() - 3 > limit * tupleSize
					|| values.size() - 3 > Math.max(0, total - offset) * tupleSize)
				throw new IllegalStateException("Invalid page response metadata");
			return new PageHeader(offset, limit, total);
		} catch (NumberFormatException e) { throw new IllegalStateException("Invalid page response metadata", e); }
	}
	private static String single(Message message) {
		if (message.values.size() != 1) throw new IllegalStateException("Unexpected proxy response shape");
		return message.values.get(0);
	}
	static boolean booleanValue(Message message) { return parseBoolean(single(message)); }
	private static boolean parseBoolean(String value) {
		if (!"true".equals(value) && !"false".equals(value)) throw new IllegalStateException("Unexpected boolean proxy response");
		return Boolean.parseBoolean(value);
	}
	private static final class PageHeader {
		final int offset, limit, total;
		PageHeader(int offset, int limit, int total) { this.offset = offset; this.limit = limit; this.total = total; }
	}
}
