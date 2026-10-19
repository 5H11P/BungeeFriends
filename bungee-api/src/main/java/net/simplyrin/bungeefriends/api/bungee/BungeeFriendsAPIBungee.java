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
import net.simplyrin.bungeefriends.api.BungeeFriendsAPI;
import net.simplyrin.bungeefriends.api.BungeeFriendsAPI.FriendInfo;
import net.simplyrin.bungeefriends.api.BungeeFriendsAPI.PartyMemberInfo;
import net.simplyrin.bungeefriends.api.event.PartyWarpEvent;
import net.simplyrin.bungeefriends.api.protocol.Protocol;
import net.simplyrin.bungeefriends.api.protocol.Protocol.Message;
import net.simplyrin.bungeefriends.tools.ThreadPool;

/** Authoritative proxy endpoint for protocol version 1. */
public final class BungeeFriendsAPIBungee extends Plugin implements Listener {
	private final ReplayCache replies = new ReplayCache(2048, 60000);
	private volatile boolean closed;
	private Main core;
	private BungeeFriendsAPI api;
	private Configuration config;

	@Override
	public void onEnable() {
		Plugin installed = getProxy().getPluginManager().getPlugin("BungeeFriends");
		if (!(installed instanceof Main)) throw new IllegalStateException("BungeeFriends core is required");
		core = (Main) installed;
		api = BungeeFriendsAPI.getInstance();
		config = loadConfig();
		closed = false;
		getProxy().registerChannel(Protocol.CHANNEL);
		getProxy().registerChannel(Protocol.WARP_CHANNEL);
		getProxy().getPluginManager().registerListener(this, this);
	}

	@Override
	public void onDisable() {
		closed = true;
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
			ThreadPool.runAsync(() -> {
				Message response = process(carrier, source, request);
				try { Protocol.encode(response); }
				catch (IllegalArgumentException e) { response = reply(request, Protocol.ERROR, "Response exceeds protocol limits"); }
				replies.complete(request.id, response);
				if (!closed) send(source, response);
			});
		} catch (RejectedExecutionException e) {
			Message response = reply(request, Protocol.ERROR, "Proxy is shutting down or busy");
			replies.complete(request.id, response);
			send(source, response);
		}
	}

	private Message process(ProxiedPlayer carrier, Server source, Message request) {
		try {
			if (closed || getProxy().getPlayer(request.actor) != carrier || carrier.getServer() == null
					|| !ProxyPolicy.validSource(request.actor, carrier.getUniqueId(), source.getInfo().getName(),
						carrier.getServer().getInfo().getName()))
				return reply(request, Protocol.DENIED, "Player or source server changed");
			switch (request.operation) {
			case "getFriendList": requireArgs(request, 0); return success(request, api.getFriendList(request.actor));
			case "getFriendDetailList": requireArgs(request, 0); return success(request, friendDetails(api.getFriendDetailList(request.actor)));
			case "getFriendDetailListByName": {
				requireArgs(request, 1);
				UUID target = findPlayer(request.values.get(0));
				if (target == null) return reply(request, Protocol.NOT_FOUND, "Unknown player");
				if (!target.equals(request.actor) && !carrier.hasPermission("friends.api.read-others"))
					return reply(request, Protocol.DENIED, "Cannot read another player's friends");
				return success(request, friendDetails(api.getFriendDetailList(target)));
			}
			case "getPartyMembers": requireArgs(request, 0); return success(request, api.getPartyMembers(request.actor));
			case "getPartyMemberDetailList": requireArgs(request, 0); return success(request, partyDetails(api.getPartyMemberDetailList(request.actor)));
			case "getPartyMemberDetailListByName":
			case "getPartyMemberDetailListByUUID": {
				requireArgs(request, 1);
				UUID target = "getPartyMemberDetailListByUUID".equals(request.operation)
						? UUID.fromString(request.values.get(0)) : findPlayer(request.values.get(0));
				if (target == null) return reply(request, Protocol.NOT_FOUND, "Unknown player");
				UUID owner = api.getOwner(target);
				if (!ProxyPolicy.canReadParty(request.actor, target, api.getOwner(request.actor), owner,
						carrier.hasPermission("friends.api.read-others")))
					return reply(request, Protocol.DENIED, "Cannot read another party");
				return success(request, partyDetails(api.getPartyMemberDetailList(target)));
			}
			case "areFriends": {
				requireArgs(request, 1);
				UUID target = findPlayer(request.values.get(0));
				return target == null ? reply(request, Protocol.NOT_FOUND, "Unknown player")
						: success(request, Boolean.toString(api.areFriends(request.actor, target)));
			}
			case "isInParty": requireArgs(request, 0); return success(request, Boolean.toString(api.isInParty(request.actor)));
			case "isPartyLeader": requireArgs(request, 0); return success(request, Boolean.toString(api.isPartyLeader(request.actor)));
			case "isPartyPrivate": requireArgs(request, 0); return success(request, Boolean.toString(api.isPartyPrivate(request.actor)));
			case "getOwner": {
				requireArgs(request, 0);
				UUID owner = api.getOwner(request.actor);
				return success(request, owner == null ? "" : owner.toString());
			}
			case "addToParty": {
				requireArgs(request, 1);
				if (!carrier.hasPermission("friends.api.add-to-party") || !api.isPartyLeader(request.actor))
					return reply(request, Protocol.DENIED, "Party leader permission required");
				UUID target = findPlayer(request.values.get(0));
				if (target == null || getProxy().getPlayer(target) == null) return reply(request, Protocol.NOT_FOUND, "Target is offline");
				api.getPartyUtils(request.actor).add(target);
				return success(request, "true");
			}
			case "executeCommand": {
				requireArgs(request, 2);
				UUID target = findPlayer(request.values.get(0));
				ProxiedPlayer member = target == null ? null : getProxy().getPlayer(target);
				if (member == null || member.getServer() == null || !ProxyPolicy.canExecute(request.actor, target,
						api.getOwner(target), carrier.hasPermission("friends.api.execute-command"),
						api.isPartyLeader(request.actor), true,
						Protocol.allowedCommand(request.values.get(1), config.getStringList("Allowed-Commands"))))
					return reply(request, Protocol.DENIED, "Target must be an online member of the leader's party");
				String command = request.values.get(1);
				Message delivery = new Message(Protocol.COMMAND, request.id, request.actor, "dispatchCommand", Protocol.OK,
						Arrays.asList(target.toString(), command));
					if (!member.getServer().getInfo().sendData(Protocol.CHANNEL, Protocol.encode(delivery), false))
						return reply(request, Protocol.ERROR, "Target server could not accept delivery");
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

	private UUID findPlayer(String nameOrUuid) {
		try { return UUID.fromString(nameOrUuid); }
		catch (IllegalArgumentException ignored) { return core.getPlayerManager().getPlayerUniqueId(nameOrUuid); }
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

	private static List<String> friendDetails(List<FriendInfo> details) {
		List<String> values = new ArrayList<>();
		for (FriendInfo item : details) Collections.addAll(values, item.getUuid().toString(), item.getName(),
				item.getDisplayName(), item.getPrefix(), Boolean.toString(item.isOnline()));
		return values;
	}

	private static List<String> partyDetails(List<PartyMemberInfo> details) {
		List<String> values = new ArrayList<>();
		for (PartyMemberInfo item : details) Collections.addAll(values, item.getUuid().toString(), item.getName(),
				item.getDisplayName(), Boolean.toString(item.isOnline()), Boolean.toString(item.isLeader()));
		return values;
	}

	private static Message success(Message request, String value) { return success(request, Collections.singletonList(value)); }
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
