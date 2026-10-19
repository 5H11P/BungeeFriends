package net.simplyrin.bungeefriends.utils;

import java.util.UUID;

import net.simplyrin.bungeefriends.Main;

public class StatusManager {
	public enum Status {
		ONLINE("Online"), AWAY("Away"), BUSY("Busy"), APPEAR_OFFLINE("AppearOffline");

		private final String key;

		Status(String key) { this.key = key; }
		public String getKey() { return key; }

		public static Status fromArgs(String[] args) {
			if (args == null || args.length == 0) return null;
			switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
			case "online": return args.length == 1 ? ONLINE : null;
			case "away": return args.length == 1 ? AWAY : null;
			case "busy": return args.length == 1 ? BUSY : null;
			case "offline":
			case "appearoffline":
			case "appear-offline": return args.length == 1 ? APPEAR_OFFLINE : null;
			case "appear": return args.length == 2 && "offline".equalsIgnoreCase(args[1]) ? APPEAR_OFFLINE : null;
			default: return null;
			}
		}
	}

	private final Main plugin;

	public StatusManager(Main plugin) { this.plugin = plugin; }

	public Status getStatus(UUID uuid) {
		String value = plugin.getString("Player." + uuid + ".Status");
		for (Status status : Status.values()) {
			if (status.key.equalsIgnoreCase(value) || status.name().equalsIgnoreCase(value)) return status;
		}
		return Status.ONLINE;
	}

	public void setStatus(UUID uuid, Status status) {
		if (uuid == null || status == null) throw new IllegalArgumentException("Player and status are required");
		plugin.set("Player." + uuid + ".Status", status.key);
	}
}
