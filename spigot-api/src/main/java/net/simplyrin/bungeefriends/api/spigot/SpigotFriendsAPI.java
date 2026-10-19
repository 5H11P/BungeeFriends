package net.simplyrin.bungeefriends.api.spigot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.simplyrin.bungeefriends.api.protocol.Protocol.Message;

/** Typed asynchronous API for plugins running on a Spigot backend. */
public final class SpigotFriendsAPI {
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
			else {
				try { result.complete(decode.apply(message)); }
				catch (RuntimeException e) { result.completeExceptionally(e); }
			}
		});
		result.whenComplete((value, error) -> { if (result.isCancelled()) source.cancel(true); });
		return result;
	}

	public CompletableFuture<List<String>> getFriendList(Player player) {
		return map(plugin().request(player, "getFriendList"), message -> new ArrayList<>(message.values));
	}
	public CompletableFuture<List<Map<String, Object>>> getFriendDetailList(Player player) {
		return map(plugin().request(player, "getFriendDetailList"), message -> details(message, false));
	}
	public CompletableFuture<List<Map<String, Object>>> getFriendDetailListByName(Player actor, String name) {
		return map(plugin().request(actor, "getFriendDetailListByName", name), message -> details(message, false));
	}
	public CompletableFuture<List<Map<String, Object>>> getFriendDetailListByName(String name) {
		return withAuthorizedActor(player -> getFriendDetailListByName(player, name));
	}
	public CompletableFuture<List<String>> getPartyMembers(Player player) {
		return map(plugin().request(player, "getPartyMembers"), message -> new ArrayList<>(message.values));
	}
	public CompletableFuture<List<Map<String, Object>>> getPartyMemberDetailList(Player player) {
		return map(plugin().request(player, "getPartyMemberDetailList"), message -> details(message, true));
	}
	public CompletableFuture<List<Map<String, Object>>> getPartyMemberDetailListByName(Player actor, String name) {
		return map(plugin().request(actor, "getPartyMemberDetailListByName", name), message -> details(message, true));
	}
	public CompletableFuture<List<Map<String, Object>>> getPartyMemberDetailListByName(String name) {
		return withAuthorizedActor(player -> getPartyMemberDetailListByName(player, name));
	}
	public CompletableFuture<List<Map<String, Object>>> getPartyMemberDetailListByUUID(Player actor, UUID uuid) {
		return map(plugin().request(actor, "getPartyMemberDetailListByUUID", uuid.toString()), message -> details(message, true));
	}
	public CompletableFuture<List<Map<String, Object>>> getPartyMemberDetailListByUUID(String uuid) {
		try { return withAuthorizedActor(player -> getPartyMemberDetailListByUUID(player, UUID.fromString(uuid))); }
		catch (IllegalArgumentException e) {
			CompletableFuture<List<Map<String, Object>>> failed = new CompletableFuture<>();
			failed.completeExceptionally(e);
			return failed;
		}
	}
	public CompletableFuture<Boolean> areFriends(Player player, String targetName) {
		return map(plugin().request(player, "areFriends", targetName), SpigotFriendsAPI::booleanValue);
	}
	public CompletableFuture<Boolean> isInParty(Player player) {
		return map(plugin().request(player, "isInParty"), SpigotFriendsAPI::booleanValue);
	}
	public CompletableFuture<Boolean> isPartyLeader(Player player) {
		return map(plugin().request(player, "isPartyLeader"), SpigotFriendsAPI::booleanValue);
	}
	public CompletableFuture<Boolean> isPartyPrivate(Player player) {
		return map(plugin().request(player, "isPartyPrivate"), SpigotFriendsAPI::booleanValue);
	}
	public CompletableFuture<UUID> getOwner(Player player) {
		return map(plugin().request(player, "getOwner"), message -> {
			String value = single(message);
			return value.isEmpty() ? null : UUID.fromString(value);
		});
	}
	public CompletableFuture<Boolean> addToParty(Player leader, String targetName) {
		return map(plugin().request(leader, "addToParty", targetName), SpigotFriendsAPI::booleanValue);
	}
	/** Completes when the proxy queues delivery, not when the target command finishes. */
	public CompletableFuture<Boolean> executeCommand(Player leader, String targetName, String command) {
		return map(plugin().request(leader, "executeCommand", targetName, command),
				message -> "queued".equals(single(message)));
	}
	public CompletableFuture<Object> sendCustomRequest(Player player, String requestType, String... args) {
		return map(plugin().request(player, requestType, args), message -> {
			switch (requestType) {
			case "getFriendList": case "getPartyMembers": return new ArrayList<>(message.values);
			case "getFriendDetailList": case "getFriendDetailListByName": return details(message, false);
			case "getPartyMemberDetailList": case "getPartyMemberDetailListByName":
			case "getPartyMemberDetailListByUUID": return details(message, true);
			case "areFriends": case "isInParty": case "isPartyLeader": case "isPartyPrivate":
			case "addToParty": return booleanValue(message);
			case "getOwner": case "executeCommand": return single(message);
			default: return Collections.unmodifiableList(new ArrayList<>(message.values));
			}
		});
	}

	private static <T> CompletableFuture<T> withAuthorizedActor(Function<Player, CompletableFuture<T>> request) {
		CompletableFuture<T> result = new CompletableFuture<>();
		Runnable select = () -> {
			try {
				for (Player player : Bukkit.getOnlinePlayers()) {
					if (!player.hasPermission("friends.api.read-others")) continue;
					CompletableFuture<T> source = request.apply(player);
					source.whenComplete((value, error) -> {
						if (error == null) result.complete(value);
						else result.completeExceptionally(error);
					});
					result.whenComplete((value, error) -> { if (result.isCancelled()) source.cancel(true); });
					return;
				}
				result.completeExceptionally(new IllegalStateException("No authorized online request player"));
			} catch (RuntimeException e) { result.completeExceptionally(e); }
		};
		if (Bukkit.isPrimaryThread()) select.run();
		else {
			try { Bukkit.getScheduler().runTask(BungeeFriendsAPISpigot.getInstance(), select); }
			catch (RuntimeException e) { result.completeExceptionally(e); }
		}
		return result;
	}

	static String single(Message message) {
		if (message.values.size() != 1) throw new IllegalStateException("Unexpected proxy response shape");
		return message.values.get(0);
	}
	static boolean booleanValue(Message message) {
		String value = single(message);
		if (!"true".equals(value) && !"false".equals(value))
			throw new IllegalStateException("Unexpected boolean proxy response");
		return Boolean.parseBoolean(value);
	}
	static List<Map<String, Object>> details(Message message, boolean party) {
		if (message.values.size() % 5 != 0) throw new IllegalStateException("Unexpected detail list response shape");
		List<Map<String, Object>> result = new ArrayList<>();
		for (int index = 0; index < message.values.size(); index += 5) {
			Map<String, Object> item = new HashMap<>();
			item.put("uuid", message.values.get(index));
			item.put("name", message.values.get(index + 1));
			item.put("displayName", message.values.get(index + 2));
			if (party) {
				item.put("online", parseBoolean(message.values.get(index + 3)));
				item.put("leader", parseBoolean(message.values.get(index + 4)));
			} else {
				item.put("prefix", message.values.get(index + 3));
				item.put("online", parseBoolean(message.values.get(index + 4)));
			}
			result.add(Collections.unmodifiableMap(item));
		}
		return Collections.unmodifiableList(result);
	}
	private static boolean parseBoolean(String value) {
		if (!"true".equals(value) && !"false".equals(value))
			throw new IllegalStateException("Unexpected boolean proxy response");
		return Boolean.parseBoolean(value);
	}

	public static final class FriendInfo {
		private final String uuid, name, displayName, prefix;
		private final boolean online;
		public FriendInfo(Map<String, Object> data) {
			uuid = (String) data.get("uuid"); name = (String) data.get("name");
			displayName = (String) data.get("displayName"); prefix = (String) data.get("prefix");
			online = (Boolean) data.get("online");
		}
		public String getUuid() { return uuid; }
		public String getName() { return name; }
		public String getDisplayName() { return displayName; }
		public String getPrefix() { return prefix; }
		public boolean isOnline() { return online; }
	}
	public static final class PartyMemberInfo {
		private final String uuid, name, displayName;
		private final boolean online, leader;
		public PartyMemberInfo(Map<String, Object> data) {
			uuid = (String) data.get("uuid"); name = (String) data.get("name");
			displayName = (String) data.get("displayName");
			online = (Boolean) data.get("online"); leader = (Boolean) data.get("leader");
		}
		public String getUuid() { return uuid; }
		public String getName() { return name; }
		public String getDisplayName() { return displayName; }
		public boolean isOnline() { return online; }
		public boolean isLeader() { return leader; }
	}
}
