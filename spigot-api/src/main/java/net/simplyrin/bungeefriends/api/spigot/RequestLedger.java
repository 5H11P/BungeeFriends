package net.simplyrin.bungeefriends.api.spigot;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.scheduler.BukkitTask;

import net.simplyrin.bungeefriends.api.protocol.Protocol;
import net.simplyrin.bungeefriends.api.protocol.Protocol.Message;

/** Tracks outstanding calls independently of the Bukkit transport. */
final class RequestLedger {
	static final class Pending {
		final UUID actor;
		final Message message;
		final byte[] bytes;
		final CompletableFuture<Message> result;
		int attempts;
		volatile BukkitTask timeout;

		Pending(Message message, byte[] bytes, CompletableFuture<Message> result) {
			this.actor = message.actor;
			this.message = message;
			this.bytes = bytes;
			this.result = result;
		}
	}

	private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
	private volatile boolean closed;

	void reopen() { closed = false; }

	boolean register(Pending request) {
		if (closed || pending.size() >= 1024 || pending.putIfAbsent(request.message.id, request) != null)
			return false;
		request.result.whenComplete((value, error) -> {
			pending.remove(request.message.id, request);
			BukkitTask timeout = request.timeout;
			if (timeout != null) timeout.cancel();
		});
		if (closed) fail(request, new IllegalStateException("Spigot API was disabled"));
		return true;
	}

	boolean receive(Message response) {
		if (response.kind != Protocol.RESPONSE) return false;
		Pending request = pending.get(response.id);
		if (request == null || !request.actor.equals(response.actor)
				|| !request.message.operation.equals(response.operation)) return false;
		if (response.status == Protocol.OK) request.result.complete(response);
		else fail(request, new BungeeFriendsAPISpigot.ApiException(response.status,
				response.values.isEmpty() ? "Proxy rejected request" : response.values.get(0)));
		return true;
	}

	void failActor(UUID actor, Throwable error) {
		for (Pending request : new ArrayList<>(pending.values()))
			if (request.actor.equals(actor)) fail(request, error);
	}

	void close(Throwable error) {
		closed = true;
		for (Pending request : new ArrayList<>(pending.values())) fail(request, error);
	}

	void fail(Pending request, Throwable error) { request.result.completeExceptionally(error); }
}
