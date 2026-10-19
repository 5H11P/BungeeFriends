package net.simplyrin.bungeefriends.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.simplyrin.bungeefriends.Main;
import net.simplyrin.bungeefriends.api.event.PartyWarpEvent;
import net.simplyrin.bungeefriends.tools.ThreadPool;
import net.simplyrin.bungeefriends.utils.LanguageManager.LanguageUtils;
import net.simplyrin.bungeefriends.utils.PartyManager.PartyUtils;

public class PartyWarpManager {
	private final Main plugin;
	private final ConcurrentHashMap<UUID, Confirmation> confirmations = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<UUID, Long> generations = new ConcurrentHashMap<>();
	private final AtomicLong nextGeneration = new AtomicLong();
	private final ConcurrentHashMap<UUID, Set<String>> tasks = new ConcurrentHashMap<>();
	private volatile boolean closed;

	public PartyWarpManager(Main plugin) { this.plugin = plugin; }

	public synchronized boolean confirmOrPrompt(ProxiedPlayer leader, PartyUtils party, LanguageUtils language) {
		UUID leaderId = leader.getUniqueId();
		if (!plugin.getBoolean("Plugin.PartyWarp.Enable-Confirmation")) {
			confirmations.remove(leaderId);
			return true;
		}
		Set<String> gameMembers = gameMembers(leaderId, party.getParties());
		if (gameMembers.isEmpty()) {
			confirmations.remove(leaderId);
			return true;
		}
		String destination = leader.getServer().getInfo().getName();
		Confirmation current = confirmations.get(leaderId);
		if (current != null && current.expiresAt >= System.currentTimeMillis()
				&& current.destination.equalsIgnoreCase(destination) && current.gameMembers.equals(gameMembers)
				&& confirmations.remove(leaderId, current)) return true;

		long seconds = plugin.getConfigManager().getConfig().getInt("Plugin.PartyWarp.Confirm-Timeout", 10);
		if (seconds <= 0 || seconds > 300) seconds = 10;
		confirmations.put(leaderId, new Confirmation(destination, gameMembers,
				System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(seconds)));
		Set<String> servers = new HashSet<>();
		for (String entry : gameMembers) servers.add(plugin.getServerName(entry.substring(entry.indexOf('@') + 1)));
		plugin.info(leader, language.getString("Warp.Confirm")
				.replace("%count%", String.valueOf(gameMembers.size()))
				.replace("%servers%", String.join(", ", servers)));
		plugin.info(leader, language.getString("Warp.Confirm-Usage").replace("%time%", String.valueOf(seconds)));
		return false;
	}

	public synchronized void startWarp(ProxiedPlayer leader, PartyUtils party, boolean manual) {
		if (closed || leader == null || leader.getServer() == null || leader.getServer().getInfo() == null) return;
		UUID leaderId = leader.getUniqueId();
		String destination = leader.getServer().getInfo().getName();
		if (destination == null || plugin.isPartyWarpBlocked(destination)) return;
		cancelFor(leaderId);
		long generation = nextGeneration.incrementAndGet();
		generations.put(leaderId, generation);
		Set<String> scheduled = ConcurrentHashMap.newKeySet();
		tasks.put(leaderId, scheduled);
		long initial = delayMillis("Plugin.PartyWarp.Initial-Delay", 0.5, false);
		long interval = delayMillis("Plugin.PartyWarp.Member-Interval", 0.1, true);
		int index = 0;
		List<UUID> plannedMembers = new ArrayList<>();
		for (String member : new ArrayList<>(party.getParties())) {
			UUID memberId;
			try { memberId = UUID.fromString(member); } catch (IllegalArgumentException e) { continue; }
			if (memberId.equals(leaderId)) continue;
			plannedMembers.add(memberId);
			String key = "warp:" + leaderId + ":" + memberId;
			scheduled.add(key);
			long delay = initial + interval * index++;
			try {
				plugin.scheduleExpiry(key, () -> {
					synchronized (PartyWarpManager.this) {
						scheduled.remove(key);
						try {
							connectIfCurrent(leaderId, memberId, destination, generation, manual);
						} catch (RuntimeException e) {
							plugin.getLogger().warning("Party warp failed for " + memberId + ": " + e.getMessage());
						} finally {
							if (scheduled.isEmpty() && tasks.remove(leaderId, scheduled))
								generations.remove(leaderId, generation);
						}
					}
				}, delay, TimeUnit.MILLISECONDS);
			} catch (RuntimeException e) {
				cancelFor(leaderId);
				plugin.getLogger().warning("Could not schedule party warp: " + e.getMessage());
				return;
			}
		}
		if (scheduled.isEmpty() && tasks.remove(leaderId, scheduled)) generations.remove(leaderId, generation);
		if (!plannedMembers.isEmpty() && generations.containsKey(leaderId)) {
			PartyWarpEvent event = new PartyWarpEvent(UUID.randomUUID(), leaderId, leader.getName(), destination,
					manual ? PartyWarpEvent.Trigger.PARTY_COMMAND : PartyWarpEvent.Trigger.LEADER_SERVER_SWITCH,
					plannedMembers);
			try {
				ThreadPool.runAsync(() -> {
					if (!closed && Long.valueOf(generation).equals(generations.get(leaderId)))
						plugin.getProxy().getPluginManager().callEvent(event);
				});
			} catch (java.util.concurrent.RejectedExecutionException e) {
				plugin.getLogger().warning("Party warp event queue is full: " + event.getWarpId());
			}
		}
	}

	private void connectIfCurrent(UUID leaderId, UUID memberId, String destination, long generation, boolean manual) {
		if (closed || plugin.getBoolean("Plugin.Disable-PartyWarp")
				|| !Long.valueOf(generation).equals(generations.get(leaderId))
				|| plugin.isPartyWarpBlocked(destination)) return;
		ProxiedPlayer leader = plugin.getProxy().getPlayer(leaderId);
		ProxiedPlayer member = plugin.getProxy().getPlayer(memberId);
		if (leader == null || member == null || leader.getServer() == null || member.getServer() == null
				|| leader.getServer().getInfo() == null || member.getServer().getInfo() == null
				|| !destination.equalsIgnoreCase(leader.getServer().getInfo().getName())
				|| destination.equalsIgnoreCase(member.getServer().getInfo().getName())
				|| !leaderId.toString().equals(plugin.getString("Parties." + leaderId + ".Currently-Joined-Party"))
				|| !leaderId.toString().equals(plugin.getString("Parties." + memberId + ".Currently-Joined-Party"))
				|| !plugin.getStringList("Parties." + leaderId + ".Party-List").contains(memberId.toString())) return;
		LanguageUtils language = plugin.getLanguageManager().getPlayer(member);
		if (manual) {
			plugin.info(member, language.getString("Warp.Summon")
					.replace("%displayName", plugin.getFriendManager().getPlayer(leader).getDisplayName()));
		} else {
			plugin.info(member, language.getString("Warp.Sending").replace("%server%", plugin.getServerName(destination)));
		}
		member.connect(leader.getServer().getInfo());
	}

	private Set<String> gameMembers(UUID leaderId, List<String> members) {
		Set<String> result = new HashSet<>();
		Set<String> gameServers = new HashSet<>();
		for (String server : plugin.getStringList("Plugin.PartyWarp.Game-Servers"))
			gameServers.add(server.trim().toLowerCase(Locale.ROOT));
		for (String member : members) {
			UUID memberId;
			try { memberId = UUID.fromString(member); } catch (IllegalArgumentException e) { continue; }
			if (memberId.equals(leaderId)) continue;
			ProxiedPlayer player = plugin.getProxy().getPlayer(memberId);
			if (player == null || player.getServer() == null || player.getServer().getInfo() == null
					|| !leaderId.toString().equals(plugin.getString("Parties." + memberId + ".Currently-Joined-Party"))) continue;
			String server = player.getServer().getInfo().getName();
			if (server != null && gameServers.contains(server.toLowerCase(Locale.ROOT))) result.add(memberId + "@" + server);
		}
		return result;
	}

	private long delayMillis(String key, double fallback, boolean interval) {
		double seconds;
		try { seconds = Double.parseDouble(plugin.getString(key)); } catch (Exception e) { seconds = fallback; }
		if (!Double.isFinite(seconds) || seconds < 0 || seconds > 30) seconds = fallback;
		return Math.max(interval ? 50L : 0L, Math.round(seconds * 1000));
	}

	public synchronized void cancelFor(UUID leaderId) {
		confirmations.remove(leaderId);
		generations.remove(leaderId);
		Set<String> scheduled = tasks.remove(leaderId);
		if (scheduled != null) for (String key : scheduled) plugin.cancelExpiry(key);
	}

	public synchronized void cancelAll() {
		for (UUID leader : new HashSet<>(tasks.keySet())) cancelFor(leader);
		confirmations.clear();
	}

	public synchronized void close() {
		closed = true;
		cancelAll();
	}

	private static final class Confirmation {
		final String destination;
		final Set<String> gameMembers;
		final long expiresAt;
		Confirmation(String destination, Set<String> gameMembers, long expiresAt) {
			this.destination = destination;
			this.gameMembers = gameMembers;
			this.expiresAt = expiresAt;
		}
	}
}
