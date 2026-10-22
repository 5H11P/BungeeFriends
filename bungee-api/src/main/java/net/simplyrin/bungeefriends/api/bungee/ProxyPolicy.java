package net.simplyrin.bungeefriends.api.bungee;

import java.util.UUID;

/** Identity and party predicates used by the proxy request handler. */
final class ProxyPolicy {
	private ProxyPolicy() { }

	static boolean validSource(UUID messageActor, UUID carrier, String sourceServer, String currentServer) {
		return messageActor != null && messageActor.equals(carrier) && sourceServer != null
				&& sourceServer.equalsIgnoreCase(currentServer);
	}

	static boolean canReadParty(UUID actor, UUID target, UUID actorOwner, UUID targetOwner, boolean readOthers) {
		return actor.equals(target) || readOthers || targetOwner != null && targetOwner.equals(actorOwner);
	}

	static boolean canExecute(UUID leader, UUID target, UUID targetOwner, boolean permission,
			boolean isLeader, boolean targetOnline, boolean commandAllowed) {
		return permission && isLeader && targetOnline && commandAllowed && target != null
				&& !leader.equals(target) && leader.equals(targetOwner);
	}

	static boolean canModifyParty(UUID actor, UUID target, UUID owner, boolean leaderAction) {
		return actor != null && target != null && owner != null && actor.equals(owner)
				&& (leaderAction || !actor.equals(target));
	}
}
