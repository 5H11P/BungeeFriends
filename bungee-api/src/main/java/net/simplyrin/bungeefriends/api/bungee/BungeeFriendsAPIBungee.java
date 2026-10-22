package net.simplyrin.bungeefriends.api.bungee;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import net.md_5.bungee.event.EventHandler;
import net.simplyrin.bungeefriends.Main;
import net.simplyrin.bungeefriends.api.contract.Friend;
import net.simplyrin.bungeefriends.api.contract.PartyMember;
import net.simplyrin.bungeefriends.api.contract.Page;
import net.simplyrin.bungeefriends.api.event.PartyWarpEvent;
import net.simplyrin.bungeefriends.api.protocol.Protocol;
import net.simplyrin.bungeefriends.api.protocol.Protocol.Message;
import net.simplyrin.bungeefriends.tools.ThreadPool;

/** Authoritative proxy endpoint for protocol version 1. */
public final class BungeeFriendsAPIBungee extends Plugin implements Listener {
	private final ReplayCache replies = new ReplayCache(2048, 60000);
	private volatile boolean closed;
	private Main core;
	private ProxyApiService api;
	private Configuration config;

	@Override
	public void onEnable() {
		Plugin installed = getProxy().getPluginManager().getPlugin("BungeeFriends");
		if (!(installed instanceof Main)) throw new IllegalStateException("BungeeFriends core is required");
		core = (Main) installed;
		api = new ProxyApiService(core);
		BungeeFriendsAPI.enable(api);
		config = loadConfig();
		closed = false;
		getProxy().registerChannel(Protocol.CHANNEL);
		getProxy().registerChannel(Protocol.WARP_CHANNEL);
		getProxy().getPluginManager().registerListener(this, this);
	}

	@Override
	public void onDisable() {
		closed = true;
		BungeeFriendsAPI.disable();
		replies.clear();
		getProxy().getPluginManager().unregisterListeners(this);
		getProxy().unregisterChannel(Protocol.CHANNEL);
		getProxy().unregisterChannel(Protocol.WARP_CHANNEL);
	}

	private Configuration loadConfig() {
		if (!getDataFolder().exists() && !getDataFolder().mkdirs())
			throw new IllegalStateException("Cannot create Bungee API data folder");
		File file = new File(getDataFolder(), "config.yml");
		try {
			if (!file.exists()) {
				Configuration defaults = new Configuration();
				defaults.set("Allowed-Commands", new ArrayList<String>());
				ConfigurationProvider.getProvider(YamlConfiguration.class).save(defaults, file);
			}
			return ConfigurationProvider.getProvider(YamlConfiguration.class).load(file);
		} catch (IOException e) {
			throw new IllegalStateException("Cannot load Bungee API config", e);
		}
	}

	@EventHandler
	public void onPluginMessage(PluginMessageEvent event) {
		if (!Protocol.CHANNEL.equals(event.getTag())) return;
		event.setCancelled(true);
		if (closed || !(event.getSender() instanceof Server) || !(event.getReceiver() instanceof ProxiedPlayer)) return;
		Server source = (Server) event.getSender();
		ProxiedPlayer carrier = (ProxiedPlayer) event.getReceiver();
		if (carrier.getServer() == null || !ProxyPolicy.validSource(carrier.getUniqueId(), carrier.getUniqueId(),
				source.getInfo().getName(), carrier.getServer().getInfo().getName())) return;
		Message request;
		try { request = Protocol.decode(event.getData()); }
		catch (IllegalArgumentException e) { getLogger().warning("Rejected malformed API packet: " + e.getMessage()); return; }
		if (request.kind != Protocol.REQUEST || !Protocol.isFresh(request, System.currentTimeMillis())
				|| !ProxyPolicy.validSource(request.actor, carrier.getUniqueId(),
				source.getInfo().getName(), carrier.getServer().getInfo().getName())) return;
		String fingerprint = fingerprint(source.getInfo().getName(), event.getData());
		ReplayCache.State state = replies.reserve(request.id, fingerprint, System.currentTimeMillis());
		if (state == ReplayCache.State.FULL) { send(source, reply(request, Protocol.ERROR, "Proxy request cache is full")); return; }
		if (state == ReplayCache.State.COLLISION) { send(source, reply(request, Protocol.DENIED, "Request ID was reused")); return; }
		if (state == ReplayCache.State.REPLAY) { send(source, replies.response(request.id)); return; }
		if (state == ReplayCache.State.PENDING) return;
		try {
			UUID actor = request.actor;
			String sourceName = source.getInfo().getName();
			String serverName = sourceName;
			boolean commandPermission = carrier.hasPermission("friends.api.execute-command");
			ThreadPool.runAsync(() -> {
				Message response = process(actor, commandPermission, request);
				try { Protocol.encode(response); }
				catch (IllegalArgumentException e) { response = reply(request, Protocol.ERROR, "Response exceeds protocol limits"); }
				Message finalResponse = response;
				getProxy().getScheduler().runAsync(this, () -> {
					if (closed) return;
					ProxiedPlayer current = getProxy().getPlayer(actor);
					if (current == null || current.getServer() == null
							|| !serverName.equalsIgnoreCase(current.getServer().getInfo().getName())) {
						replies.complete(request.id, reply(request, Protocol.DENIED, "Request player changed servers"));
						return;
					}
					Server currentServer = current.getServer();
					Message outgoing = finalResponse;
					if ("executeCommand".equals(request.operation) && finalResponse.status == Protocol.OK) {
						UUID targetId;
						try { targetId = UUID.fromString(request.values.get(0)); }
						catch (Exception e) { outgoing = reply(request, Protocol.INVALID, "Invalid command target"); targetId = null; }
						if (targetId == null) {
							replies.complete(request.id, outgoing);
							send(currentServer, outgoing);
							return;
						}
						ProxiedPlayer target = getProxy().getPlayer(targetId);
						if (target == null || target.getServer() == null || !current.hasPermission("friends.api.execute-command")
								|| !api.leader(actor) || !actor.equals(api.owner(targetId))
								|| !Protocol.allowedCommand(request.values.get(1), config.getStringList("Allowed-Commands"))) {
							outgoing = reply(request, Protocol.DENIED, "Command delivery no longer authorized");
						} else {
							Message delivery = new Message(Protocol.COMMAND, request.id, actor, "dispatchCommand", Protocol.OK,
									Arrays.asList(targetId.toString(), request.values.get(1)));
							if (!target.getServer().getInfo().sendData(Protocol.CHANNEL, Protocol.encode(delivery), false))
								outgoing = reply(request, Protocol.ERROR, "Target server could not accept delivery");
						}
					}
					replies.complete(request.id, outgoing);
					send(currentServer, outgoing);
				});
			});
		} catch (RejectedExecutionException e) {
			Message response = reply(request, Protocol.ERROR, "Proxy is shutting down or busy");
			replies.complete(request.id, response);
			send(source, response);
		}
	}

	private Message process(UUID actor, boolean commandPermission, Message request) {
		try {
			if (closed) return reply(request, Protocol.DENIED, "Proxy API is disabled");
			switch (request.operation) {
			case "friends": {
				PageSpec page = page(request, 1);
				return success(request, boundedPage(request, friendPage(api.friends(actor, page.offset, page.limit)), 5));
			}
			case "friendIds": {
				PageSpec page = page(request, 1);
				return success(request, boundedPage(request, uuidPage(api.friendIds(actor), page), 1));
			}
			case "areFriends": requireArgs(request, 1); return success(request, Boolean.toString(api.areFriends(actor, UUID.fromString(request.values.get(0)))));
			case "friendNickname": requireArgs(request, 1); return success(request, api.friendNickname(actor, UUID.fromString(request.values.get(0))));
			case "isBestFriend": requireArgs(request, 1); return success(request, Boolean.toString(api.isBestFriend(actor, UUID.fromString(request.values.get(0)))));
			case "areFriendNotificationsEnabled": requireArgs(request, 0); return success(request, Boolean.toString(api.friendNotificationsEnabled(actor)));
			case "isRequestEnabled": requireArgs(request, 0); return success(request, Boolean.toString(api.friendRequestsEnabled(actor)));
			case "isPartyInviteEnabled": requireArgs(request, 0); return success(request, Boolean.toString(api.partyInvitesEnabled(actor)));
			case "status": requireArgs(request, 0); return success(request, api.status(actor).name());
			case "ignoredPlayers": {
				PageSpec page = page(request, 1);
				return success(request, boundedPage(request, uuidPage(api.ignored(actor), page), 1));
			}
			case "partyMembers": {
				PageSpec page = page(request, 1);
				return success(request, boundedPage(request, partyPage(api.partyMembers(actor, page.offset, page.limit)), 6));
			}
			case "isPartyMember": requireArgs(request, 0); return success(request, Boolean.toString(api.inParty(actor)));
			case "isPartyLeader": requireArgs(request, 0); return success(request, Boolean.toString(api.leader(actor)));
			case "isPartyModerator": requireArgs(request, 0); return success(request, Boolean.toString(api.moderator(actor)));
			case "isPartyPrivate": requireArgs(request, 0); return success(request, Boolean.toString(api.isPrivate(actor)));
			case "partyOwner": { requireArgs(request, 0); UUID owner = api.owner(actor); return success(request, owner == null ? "" : owner.toString()); }
			case "requestFriend": requireArgs(request, 1); api.requestFriend(actor, UUID.fromString(request.values.get(0))); return successEmpty(request);
			case "acceptFriend": {
				requireArgs(request, 1); api.acceptFriend(actor, UUID.fromString(request.values.get(0))); return successEmpty(request);
			}
			case "removeFriend": requireArgs(request, 1); api.removeFriend(actor, UUID.fromString(request.values.get(0))); return successEmpty(request);
			case "ignore": requireArgs(request, 1); api.ignore(actor, UUID.fromString(request.values.get(0))); return successEmpty(request);
			case "unignore": requireArgs(request, 1); api.unignore(actor, UUID.fromString(request.values.get(0))); return successEmpty(request);
			case "setRequestEnabled": requireArgs(request, 1); api.setFriendRequests(actor, parseBoolean(request.values.get(0))); return successEmpty(request);
			case "setFriendNickname": requireArgs(request, 2); api.setFriendNickname(actor, UUID.fromString(request.values.get(0)), request.values.get(1)); return successEmpty(request);
			case "setBestFriend": requireArgs(request, 2); api.setBestFriend(actor, UUID.fromString(request.values.get(0)), parseBoolean(request.values.get(1))); return successEmpty(request);
			case "setFriendNotificationsEnabled": requireArgs(request, 1); api.setFriendNotificationsEnabled(actor, parseBoolean(request.values.get(0))); return successEmpty(request);
			case "setPartyInviteEnabled": requireArgs(request, 1); api.setPartyInvites(actor, parseBoolean(request.values.get(0))); return successEmpty(request);
			case "setStatus": {
				requireArgs(request, 1);
				try { api.setStatus(actor, net.simplyrin.bungeefriends.api.contract.PlayerStatus.valueOf(request.values.get(0))); }
				catch (IllegalArgumentException e) { throw new IllegalArgumentException("Unknown status"); }
				return successEmpty(request);
			}
			case "inviteParty": requireArgs(request, 1); api.invite(actor, UUID.fromString(request.values.get(0))); return successEmpty(request);
			case "acceptPartyInvite": requireArgs(request, 1); api.acceptInvite(actor, UUID.fromString(request.values.get(0))); return successEmpty(request);
			case "revokePartyInvite": requireArgs(request, 1); api.revokeInvite(actor, UUID.fromString(request.values.get(0))); return successEmpty(request);
			case "leaveParty": requireArgs(request, 0); api.leave(actor); return successEmpty(request);
			case "kickPartyMember": requireArgs(request, 1); api.kick(actor, UUID.fromString(request.values.get(0))); return successEmpty(request);
			case "transferPartyLeadership": requireArgs(request, 1); api.transfer(actor, UUID.fromString(request.values.get(0))); return successEmpty(request);
			case "promotePartyModerator": requireArgs(request, 1); api.promote(actor, UUID.fromString(request.values.get(0))); return successEmpty(request);
			case "demotePartyModerator": requireArgs(request, 1); api.demote(actor, UUID.fromString(request.values.get(0))); return successEmpty(request);
			case "disbandParty": requireArgs(request, 0); api.disband(actor); return successEmpty(request);
			case "setPartyPrivate": requireArgs(request, 1); api.setPrivate(actor, parseBoolean(request.values.get(0))); return successEmpty(request);
			case "setPartyMute": requireArgs(request, 1); api.setMute(actor, parseBoolean(request.values.get(0))); return successEmpty(request);
			case "partyChat": {
				requireArgs(request, 1);
				api.chat(actor, request.values.get(0));
				return successEmpty(request);
			}
			case "executeCommand": {
				requireArgs(request, 2);
				UUID target = UUID.fromString(request.values.get(0));
				if (!commandPermission || !api.leader(actor)
						|| !actor.equals(api.owner(target))
						|| !Protocol.allowedCommand(request.values.get(1), config.getStringList("Allowed-Commands")))
					return reply(request, Protocol.DENIED, "Target must be an online member of the leader's party");
				return success(request, "queued");
			}
			default: return reply(request, Protocol.UNSUPPORTED, "Unknown operation");
			}
		} catch (IllegalArgumentException e) {
			return reply(request, Protocol.INVALID, e.getMessage());
		} catch (Exception e) {
			getLogger().warning("API request failed (" + request.operation + "): " + e.getMessage());
			return reply(request, Protocol.ERROR, "Proxy operation failed");
		}
	}

	private static String fingerprint(String serverName, byte[] bytes) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			digest.update(serverName.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			digest.update((byte) 0);
			byte[] hash = digest.digest(bytes);
			StringBuilder result = new StringBuilder(hash.length * 2);
			for (byte value : hash) result.append(String.format("%02x", value & 0xff));
			return result.toString();
		} catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable", e); }
	}

	private static void requireArgs(Message request, int expected) {
		if (request.values.size() != expected) throw new IllegalArgumentException("Expected " + expected + " arguments");
	}

	private static final class PageSpec {
		final int offset, limit;
		PageSpec(int offset, int limit) { this.offset = offset; this.limit = limit; }
	}

	private static PageSpec page(Message request, int expected) {
		requireArgs(request, expected);
		String[] parts = request.values.get(0).split(",", -1);
		if (parts.length != 2) throw new IllegalArgumentException("Page requires offset and limit");
		try {
			int offset = Integer.parseInt(parts[0]), limit = Integer.parseInt(parts[1]);
			if (offset < 0 || limit < 1 || limit > 100) throw new IllegalArgumentException("Page limit must be between 1 and 100");
			return new PageSpec(offset, limit);
		} catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid page request", e); }
	}

	private static List<String> uuidPage(List<String> source, PageSpec page) {
		int start = Math.min(page.offset, source.size()), end = Math.min(source.size(), start + page.limit);
		List<String> result = new ArrayList<>();
		Collections.addAll(result, Integer.toString(page.offset), Integer.toString(page.limit), Integer.toString(source.size()));
		result.addAll(source.subList(start, end));
		return result;
	}

	private static boolean parseBoolean(String value) {
		if (!"true".equals(value) && !"false".equals(value)) throw new IllegalArgumentException("Expected true or false");
		return Boolean.parseBoolean(value);
	}

	private static List<String> friendPage(Page<Friend> page) {
		List<String> values = new ArrayList<>();
		Collections.addAll(values, Integer.toString(page.getOffset()), Integer.toString(page.getLimit()), Integer.toString(page.getTotal()));
		for (Friend item : page.getItems()) Collections.addAll(values, item.getUniqueId().toString(), item.getName(),
				item.getDisplayName(), item.getPrefix(), Boolean.toString(item.isOnline()));
		return values;
	}

	private static List<String> partyPage(Page<PartyMember> page) {
		List<String> values = new ArrayList<>();
		Collections.addAll(values, Integer.toString(page.getOffset()), Integer.toString(page.getLimit()), Integer.toString(page.getTotal()));
		for (PartyMember item : page.getItems()) Collections.addAll(values, item.getUniqueId().toString(), item.getName(),
				item.getDisplayName(), Boolean.toString(item.isOnline()), Boolean.toString(item.isLeader()), Boolean.toString(item.isModerator()));
		return values;
	}

	private static List<String> boundedPage(Message request, List<String> values, int tupleSize) {
		List<String> bounded = new ArrayList<>(values);
		while (true) {
			try {
				Protocol.encode(new Message(Protocol.RESPONSE, request.id, request.actor, request.operation, Protocol.OK, bounded));
				return bounded;
			} catch (IllegalArgumentException tooLarge) {
				if (bounded.size() <= 3) throw tooLarge;
				for (int i = 0; i < tupleSize; i++) bounded.remove(bounded.size() - 1);
			}
		}
	}

	private static Message success(Message request, String value) { return success(request, Collections.singletonList(value)); }
	private static Message successEmpty(Message request) { return success(request, Collections.emptyList()); }
	private static Message success(Message request, List<String> values) {
		return new Message(Protocol.RESPONSE, request.id, request.actor, request.operation, Protocol.OK, values);
	}
	private static Message reply(Message request, byte status, String error) {
		return new Message(Protocol.RESPONSE, request.id, request.actor, request.operation, status, Collections.singletonList(error));
	}

	private void send(Server server, Message response) {
		try { server.sendData(Protocol.CHANNEL, Protocol.encode(response)); }
		catch (RuntimeException e) { getLogger().warning("Cannot send API response: " + e.getMessage()); }
	}

	@EventHandler
	public void onPartyWarp(PartyWarpEvent event) {
		if (closed) return;
		ServerInfo target = getProxy().getServerInfo(event.getTargetServer());
		if (target == null) return;
		List<String> values = new ArrayList<>();
		Collections.addAll(values, event.getLeaderName(), event.getTargetServer(), event.getTrigger().name(),
				Long.toString(System.currentTimeMillis()));
		for (UUID member : event.getMemberUuids()) values.add(member.toString());
		try {
			Message message = new Message(Protocol.EVENT, event.getWarpId(), event.getLeaderUuid(), "partyWarp", Protocol.OK, values);
			target.sendData(Protocol.WARP_CHANNEL, Protocol.encode(message), true);
		} catch (RuntimeException e) { getLogger().warning("Cannot forward party warp event: " + e.getMessage()); }
	}

}
