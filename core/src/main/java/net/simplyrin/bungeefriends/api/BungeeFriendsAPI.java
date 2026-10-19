package net.simplyrin.bungeefriends.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import net.simplyrin.bungeefriends.Main;
import net.simplyrin.bungeefriends.exceptions.AlreadyAddedException;
import net.simplyrin.bungeefriends.exceptions.FailedAddingException;
import net.simplyrin.bungeefriends.exceptions.FriendSlotLimitException;
import net.simplyrin.bungeefriends.exceptions.IgnoredException;
import net.simplyrin.bungeefriends.exceptions.NotAddedException;
import net.simplyrin.bungeefriends.exceptions.RequestDenyException;
import net.simplyrin.bungeefriends.exceptions.SelfException;
import net.simplyrin.bungeefriends.exceptions.parties.AlreadyJoinedException;
import net.simplyrin.bungeefriends.exceptions.parties.FailedInvitingException;
import net.simplyrin.bungeefriends.exceptions.parties.NotJoinedException;
import net.simplyrin.bungeefriends.utils.FriendManager.FriendUtils;
import net.simplyrin.bungeefriends.utils.PartyManager.PartyUtils;

/** Synchronous proxy API. Storage failures propagate as IllegalStateException. */
public final class BungeeFriendsAPI {
	private static final BungeeFriendsAPI INSTANCE = new BungeeFriendsAPI();

	private BungeeFriendsAPI() { }

	public static BungeeFriendsAPI getInstance() { return INSTANCE; }

	private Main core() {
		Plugin plugin = ProxyServer.getInstance().getPluginManager().getPlugin("BungeeFriends");
		if (!(plugin instanceof Main)) throw new IllegalStateException("BungeeFriends core is not enabled");
		return (Main) plugin;
	}

	private Main partyCore() {
		Main core = core();
		if (core.getPartyManager() == null) throw new IllegalStateException("Party support is disabled");
		return core;
	}

	public FriendUtils getFriendUtils(ProxiedPlayer player) { return getFriendUtils(Objects.requireNonNull(player).getUniqueId()); }
	public FriendUtils getFriendUtils(UUID uuid) { return core().getFriendManager().getPlayer(Objects.requireNonNull(uuid)); }
	public PartyUtils getPartyUtils(ProxiedPlayer player) { return getPartyUtils(Objects.requireNonNull(player).getUniqueId()); }
	public PartyUtils getPartyUtils(UUID uuid) { return partyCore().getPartyManager().getPlayer(Objects.requireNonNull(uuid)); }
	public boolean areFriends(ProxiedPlayer first, ProxiedPlayer second) { return areFriends(first.getUniqueId(), second.getUniqueId()); }
	public boolean areFriends(UUID first, UUID second) { return getFriendUtils(first).isFriend(Objects.requireNonNull(second)); }
	public List<String> getFriendList(ProxiedPlayer player) { return getFriendList(player.getUniqueId()); }
	public List<String> getFriendList(UUID uuid) { return new ArrayList<>(getFriendUtils(uuid).getFriends()); }
	public void addFriendRequest(ProxiedPlayer sender, ProxiedPlayer target)
			throws AlreadyAddedException, FailedAddingException, SelfException, IgnoredException,
			FriendSlotLimitException, RequestDenyException {
		getFriendUtils(sender).addRequest(target);
	}
	public void addFriend(ProxiedPlayer first, ProxiedPlayer second) throws AlreadyAddedException, FailedAddingException {
		getFriendUtils(first).add(second);
	}
	public void removeFriend(ProxiedPlayer first, ProxiedPlayer second) throws NotAddedException, SelfException {
		getFriendUtils(first).remove(second);
	}

	public List<FriendInfo> getFriendDetailList(ProxiedPlayer player) { return getFriendDetailList(player.getUniqueId()); }
	public List<FriendInfo> getFriendDetailList(UUID uuid) {
		List<FriendInfo> result = new ArrayList<>();
		for (String id : getFriendList(uuid)) {
			FriendUtils friend = getFriendUtils(UUID.fromString(id));
			result.add(new FriendInfo(friend.getUniqueId(), friend.getName(), friend.getDisplayNameFor(uuid),
					friend.getPrefix(), friend.getPlayer() != null));
		}
		return result;
	}
	public List<FriendInfo> getFriendDetailListByName(String name) {
		UUID uuid = core().getPlayerManager().getPlayerUniqueId(Objects.requireNonNull(name));
		return uuid == null ? Collections.emptyList() : getFriendDetailList(uuid);
	}

	public boolean isInParty(ProxiedPlayer player) { return isInParty(player.getUniqueId()); }
	public boolean isInParty(UUID uuid) { return getPartyUtils(uuid).isJoinedParty(); }
	public boolean isPartyLeader(ProxiedPlayer player) { return isPartyLeader(player.getUniqueId()); }
	public boolean isPartyLeader(UUID uuid) {
		try { return getPartyUtils(uuid).isPartyOwner(); }
		catch (NotJoinedException e) { return false; }
	}
	public UUID getOwner(ProxiedPlayer player) { return getOwner(player.getUniqueId()); }
	public UUID getOwner(UUID uuid) {
		try { return getPartyUtils(uuid).getPartyLeader().getUniqueId(); }
		catch (NotJoinedException e) { return null; }
	}
	public boolean isPartyPrivate(ProxiedPlayer player) { return isPartyPrivate(player.getUniqueId()); }
	public boolean isPartyPrivate(UUID uuid) {
		UUID owner = getOwner(uuid);
		return owner != null && core().getBoolean("Parties." + owner + ".Private");
	}
	public List<String> getPartyMembers(ProxiedPlayer player) { return getPartyMembers(player.getUniqueId()); }
	public PartyUtils getPartyLeader(ProxiedPlayer player) throws NotJoinedException {
		return getPartyUtils(player).getPartyLeader();
	}
	public void inviteToParty(ProxiedPlayer leader, ProxiedPlayer target)
			throws AlreadyJoinedException, FailedInvitingException {
		getPartyUtils(leader).addRequest(target);
	}
	public void addToParty(ProxiedPlayer leader, ProxiedPlayer member)
			throws AlreadyJoinedException, net.simplyrin.bungeefriends.exceptions.parties.FailedAddingException {
		getPartyUtils(leader).add(member);
	}
	public void removeFromParty(ProxiedPlayer leader, ProxiedPlayer member) throws NotJoinedException {
		getPartyUtils(leader).remove(member);
	}
	public void sendPartyChat(ProxiedPlayer player, String message) throws NotJoinedException {
		getPartyUtils(player).partyChat(player, message);
	}
	public List<String> getPartyMembers(UUID uuid) {
		UUID owner = getOwner(uuid);
		return owner == null ? Collections.emptyList() : new ArrayList<>(getPartyUtils(owner).getParties());
	}
	public List<PartyMemberInfo> getPartyMemberDetailList(ProxiedPlayer player) { return getPartyMemberDetailList(player.getUniqueId()); }
	public List<PartyMemberInfo> getPartyMemberDetailList(UUID uuid) {
		UUID owner = getOwner(uuid);
		if (owner == null) return Collections.emptyList();
		List<PartyMemberInfo> result = new ArrayList<>();
		result.add(memberInfo(owner, true));
		for (String id : getPartyMembers(owner)) result.add(memberInfo(UUID.fromString(id), false));
		return result;
	}
	public List<PartyMemberInfo> getPartyMemberDetailListByName(String name) {
		UUID uuid = core().getPlayerManager().getPlayerUniqueId(Objects.requireNonNull(name));
		return uuid == null ? Collections.emptyList() : getPartyMemberDetailList(uuid);
	}
	private PartyMemberInfo memberInfo(UUID uuid, boolean leader) {
		FriendUtils friend = getFriendUtils(uuid);
		return new PartyMemberInfo(uuid, friend.getName(), friend.getDisplayName(), friend.getPlayer() != null, leader);
	}

	public static final class FriendInfo {
		private final UUID uuid;
		private final String name;
		private final String displayName;
		private final String prefix;
		private final boolean online;
		public FriendInfo(UUID uuid, String name, String displayName, String prefix, boolean online) {
			this.uuid = uuid; this.name = name; this.displayName = displayName; this.prefix = prefix; this.online = online;
		}
		public UUID getUuid() { return uuid; }
		public String getName() { return name; }
		public String getDisplayName() { return displayName; }
		public String getPrefix() { return prefix; }
		public boolean isOnline() { return online; }
	}

	public static final class PartyMemberInfo {
		private final UUID uuid;
		private final String name;
		private final String displayName;
		private final boolean online;
		private final boolean leader;
		public PartyMemberInfo(UUID uuid, String name, String displayName, boolean online, boolean leader) {
			this.uuid = uuid; this.name = name; this.displayName = displayName; this.online = online; this.leader = leader;
		}
		public UUID getUuid() { return uuid; }
		public String getName() { return name; }
		public String getDisplayName() { return displayName; }
		public boolean isOnline() { return online; }
		public boolean isLeader() { return leader; }
	}
}
