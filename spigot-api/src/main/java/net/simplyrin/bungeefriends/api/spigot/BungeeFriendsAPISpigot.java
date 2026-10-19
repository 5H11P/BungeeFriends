package net.simplyrin.bungeefriends.api.spigot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import net.simplyrin.bungeefriends.api.protocol.Protocol;
import net.simplyrin.bungeefriends.api.protocol.Protocol.Message;
import net.simplyrin.bungeefriends.api.spigot.event.PartyWarpEvent;
import net.simplyrin.bungeefriends.api.spigot.RequestLedger.Pending;

/** Spigot endpoint. Bukkit calls and plugin messages run on the primary thread. */
public final class BungeeFriendsAPISpigot extends JavaPlugin implements PluginMessageListener, Listener {
	private static BungeeFriendsAPISpigot instance;
	private final RequestLedger pending = new RequestLedger();
	private final Map<UUID, Long> deliveredCommands = new ConcurrentHashMap<>();
	private final Map<UUID, Long> deliveredWarps = new ConcurrentHashMap<>();
	private volatile boolean stopping;

	public static BungeeFriendsAPISpigot getInstance() {
		if (instance == null || instance.stopping) throw new IllegalStateException("Spigot API is not enabled");
		return instance;
	}

	@Override
	public void onEnable() {
		saveDefaultConfig();
		pending.reopen();
		stopping = false;
		instance = this;
		getServer().getMessenger().registerOutgoingPluginChannel(this, Protocol.CHANNEL);
		getServer().getMessenger().registerIncomingPluginChannel(this, Protocol.CHANNEL, this);
		getServer().getMessenger().registerIncomingPluginChannel(this, Protocol.WARP_CHANNEL, this);
		getServer().getPluginManager().registerEvents(this, this);
	}

	@Override
	public void onDisable() {
		stopping = true;
		pending.close(new IllegalStateException("Spigot API was disabled"));
		deliveredCommands.clear();
		deliveredWarps.clear();
		getServer().getMessenger().unregisterOutgoingPluginChannel(this, Protocol.CHANNEL);
		getServer().getMessenger().unregisterIncomingPluginChannel(this, Protocol.CHANNEL, this);
		getServer().getMessenger().unregisterIncomingPluginChannel(this, Protocol.WARP_CHANNEL, this);
		instance = null;
	}

	public CompletableFuture<Message> request(Player player, String operation, String... args) {
		CompletableFuture<Message> result = new CompletableFuture<>();
		if (stopping) {
			result.completeExceptionally(new IllegalStateException("Spigot API is disabled"));
			return result;
		}
		Runnable setup = () -> {
			if (stopping || player == null || !player.isOnline()) {
				result.completeExceptionally(new IllegalStateException("Request player is offline"));
				return;
			}
			try {
				Message message = new Message(Protocol.REQUEST, UUID.randomUUID(), player.getUniqueId(), operation,
						Protocol.OK, Arrays.asList(args));
				Pending request = new Pending(message, Protocol.encode(message), result);
				if (!pending.register(request)) {
					result.completeExceptionally(new IllegalStateException("Spigot API is disabled or too busy"));
					return;
				}
				send(request);
			} catch (RuntimeException e) { result.completeExceptionally(e); }
		};
		if (Bukkit.isPrimaryThread()) setup.run();
		else {
			try { Bukkit.getScheduler().runTask(this, setup); }
			catch (RuntimeException e) { result.completeExceptionally(e); }
		}
		return result;
	}

	private void send(Pending request) {
		if (request.result.isDone()) return;
		Player player = Bukkit.getPlayer(request.actor);
		if (stopping || player == null || !player.isOnline()) {
			pending.fail(request, new IllegalStateException("Request player left the server"));
			return;
		}
		request.attempts++;
		try {
			request.timeout = Bukkit.getScheduler().runTaskLater(this, () -> {
				if (request.result.isDone()) return;
				if (request.attempts >= 3) pending.fail(request, new TimeoutException("Proxy API request timed out"));
				else send(request);
			}, 60L);
			player.sendPluginMessage(this, Protocol.CHANNEL, request.bytes);
		}
		catch (RuntimeException e) { pending.fail(request, e); }
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		UUID actor = event.getPlayer().getUniqueId();
		pending.failActor(actor, new IllegalStateException("Request player left the server"));
	}

	@Override
	public void onPluginMessageReceived(String channel, Player carrier, byte[] bytes) {
		if (!Protocol.CHANNEL.equals(channel) && !Protocol.WARP_CHANNEL.equals(channel)) return;
		if (!Bukkit.isPrimaryThread()) {
			try { Bukkit.getScheduler().runTask(this, () -> onPluginMessageReceived(channel, carrier, bytes)); }
			catch (RuntimeException ignored) { }
			return;
		}
		if (stopping || bytes == null || bytes.length > Protocol.MAX_BYTES) return;
		Message message;
		try { message = Protocol.decode(bytes); }
		catch (IllegalArgumentException e) { getLogger().warning("Rejected malformed API message: " + e.getMessage()); return; }
		if (Protocol.WARP_CHANNEL.equals(channel)) {
			if (message.kind == Protocol.EVENT) deliverWarp(message);
			return;
		}
		if (message.kind == Protocol.RESPONSE) pending.receive(message);
		else if (message.kind == Protocol.COMMAND) deliverCommand(message);
	}

	private void deliverCommand(Message message) {
		if (!"dispatchCommand".equals(message.operation) || message.values.size() != 2
				|| !Protocol.isFresh(message, System.currentTimeMillis())) return;
		UUID target;
		try { target = UUID.fromString(message.values.get(0)); }
		catch (IllegalArgumentException e) { return; }
		Player targetPlayer = Bukkit.getPlayer(target);
		if (targetPlayer == null || !targetPlayer.isOnline()) return;
		String command = message.values.get(1);
		if (!Protocol.allowedCommand(command, getConfig().getStringList("allowed-commands"))) return;
		if (deliveredCommands.size() >= 2048)
			deliveredCommands.entrySet().removeIf(entry -> entry.getValue() < System.currentTimeMillis());
		if (deliveredCommands.size() >= 2048 || deliveredCommands.putIfAbsent(message.id, System.currentTimeMillis() + 60000) != null) return;
		targetPlayer.performCommand(command);
	}

	private void deliverWarp(Message message) {
		if (!"partyWarp".equals(message.operation) || message.values.size() < 4
				|| !Protocol.isFresh(message, System.currentTimeMillis())) return;
		long created;
		try { created = Long.parseLong(message.values.get(3)); }
		catch (NumberFormatException e) { return; }
		if (created > System.currentTimeMillis() + 5000 || System.currentTimeMillis() - created > 30000) return;
		if (deliveredWarps.size() >= 2048)
			deliveredWarps.entrySet().removeIf(entry -> entry.getValue() < System.currentTimeMillis());
		if (deliveredWarps.size() >= 2048 || deliveredWarps.putIfAbsent(message.id, System.currentTimeMillis() + 60000) != null) return;
		try {
			List<UUID> members = new ArrayList<>();
			for (int index = 4; index < message.values.size(); index++) members.add(UUID.fromString(message.values.get(index)));
			PartyWarpEvent.Trigger trigger = PartyWarpEvent.Trigger.valueOf(message.values.get(2));
			Bukkit.getPluginManager().callEvent(new PartyWarpEvent(message.id, message.actor,
					message.values.get(0), message.values.get(1), trigger, members));
		} catch (IllegalArgumentException e) { getLogger().warning("Rejected malformed party warp event"); }
	}

	public static final class ApiException extends RuntimeException {
		private static final long serialVersionUID = 1L;
		private final byte status;
		ApiException(byte status, String message) { super(message); this.status = status; }
		public byte getStatus() { return status; }
	}
}
