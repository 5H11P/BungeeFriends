package net.simplyrin.bungeefriends.api.bungee;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.simplyrin.bungeefriends.api.protocol.Protocol.Message;

/** Bounded request-ID ledger, including in-flight requests. */
final class ReplayCache {
	enum State { NEW, PENDING, REPLAY, COLLISION, FULL }
	private final Map<UUID, Entry> entries = new HashMap<>();
	private final int capacity;
	private final long retentionMillis;

	ReplayCache(int capacity, long retentionMillis) {
		this.capacity = capacity;
		this.retentionMillis = retentionMillis;
	}

	synchronized State reserve(UUID id, String fingerprint, long now) {
		entries.entrySet().removeIf(entry -> entry.getValue().response != null && entry.getValue().expiresAt < now);
		Entry previous = entries.get(id);
		if (previous != null) {
			if (!previous.fingerprint.equals(fingerprint)) return State.COLLISION;
			return previous.response == null ? State.PENDING : State.REPLAY;
		}
		if (entries.size() >= capacity) return State.FULL;
		entries.put(id, new Entry(fingerprint, now + retentionMillis));
		return State.NEW;
	}

	synchronized void complete(UUID id, Message response) {
		Entry entry = entries.get(id);
		if (entry != null) entry.response = response;
	}

	synchronized Message response(UUID id) {
		Entry entry = entries.get(id);
		return entry == null ? null : entry.response;
	}

	synchronized void clear() { entries.clear(); }

	private static final class Entry {
		final String fingerprint;
		final long expiresAt;
		Message response;
		Entry(String fingerprint, long expiresAt) {
			this.fingerprint = fingerprint;
			this.expiresAt = expiresAt;
		}
	}
}
