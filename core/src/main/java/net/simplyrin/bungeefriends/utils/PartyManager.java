package net.simplyrin.bungeefriends.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.UUID;

import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.simplyrin.bungeefriends.Main;
import net.simplyrin.bungeefriends.exceptions.parties.AlreadyJoinedException;
import net.simplyrin.bungeefriends.exceptions.parties.FailedAddingException;
import net.simplyrin.bungeefriends.exceptions.parties.FailedInvitingException;
import net.simplyrin.bungeefriends.exceptions.parties.NotInvitedException;
import net.simplyrin.bungeefriends.exceptions.parties.NotJoinedException;
import net.simplyrin.bungeefriends.utils.FriendManager.FriendUtils;
import net.simplyrin.bungeefriends.utils.LanguageManager.LanguageUtils;

/**
 * Created by SimplyRin on 2018/07/31.
 *
 * author: SimplyRin
 * license: LGPL v3
 * copyright: Copyright (c) 2021 SimplyRin
 */
public class PartyManager {

	private Main plugin;

	public PartyManager(Main plugin) {
		this.plugin = plugin;
	}

	public PartyUtils getPlayer(ProxiedPlayer player) {
		return new PartyUtils(player.getUniqueId());
	}

	public PartyUtils getPlayer(String uuid) {
		return new PartyUtils(UUID.fromString(uuid));
	}

	public PartyUtils getPlayer(UUID uniqueId) {
		return new PartyUtils(uniqueId);
	}

	private synchronized void updateMembers(UUID leader, List<UUID> participants, List<UUID> detached) {
		if (this.plugin.isEnabledMySQL()) {
			this.plugin.getMySQLManager().getEditor().updateParty(leader, participants, detached);
			return;
		}
		synchronized (this.plugin.getDataManager()) {
		List<String> members = new ArrayList<>();
		for (UUID player : participants) if (!player.equals(leader)) members.add(player.toString());
		for (UUID player : participants) {
			String path = "Parties." + player + ".";
			this.plugin.set(path + "Currently-Joined-Party", leader.toString());
			this.plugin.set(path + "Party-List", new ArrayList<>(members));
		}
		for (UUID player : detached) {
			String path = "Parties." + player + ".";
			this.plugin.set(path + "Currently-Joined-Party", "NONE");
			this.plugin.set(path + "Party-List", new ArrayList<>());
		}
		this.plugin.getDataManager().save();
		}
	}

	private int maxSize() {
		try {
			return Math.max(2, Integer.parseInt(this.plugin.getString("Plugin.Party-Max-Size")));
		} catch (NumberFormatException e) {
			return 10;
		}
	}

	public class PartyUtils {

		private UUID uuid;

		public PartyUtils(UUID uuid) {
			this.uuid = uuid;

			if (plugin.getString("Parties." + this.uuid.toString() + ".Name").equals("")) {
				ProxiedPlayer player = plugin.getProxy().getPlayer(this.uuid);

				if (player == null) return;
				plugin.info("Creating data for player " + player.getName() + "...");

				plugin.set("Parties." + this.uuid.toString() + ".Name", player.getName());
				plugin.set("Parties." + this.uuid.toString() + ".Currently-Joined-Party", "NONE");
				plugin.set("Parties." + this.uuid.toString() + ".Party-List", new ArrayList<>());
				plugin.set("Parties." + this.uuid.toString() + ".Requests", new ArrayList<>());
				plugin.set("Parties." + this.uuid.toString() + ".Toggle", true);
				plugin.set("Parties." + this.uuid.toString() + ".AllInvite", false);
				plugin.set("Parties." + this.uuid.toString() + ".Mute", false);
				plugin.set("Parties." + this.uuid.toString() + ".Private", false);
				plugin.getPlayerManager().save();
			}
		}

		public ProxiedPlayer getPlayer() {
			try {
				return plugin.getProxy().getPlayer(this.uuid);
			} catch (Exception e) {
			}
			return null;
		}

		public void broadcast(String text) {
			for (String player : this.getParties()) {
				plugin.info(player, text);
			}
			plugin.info(uuid, text);
		}

		public void partyChat(ProxiedPlayer player, String text) throws NotJoinedException {
			FriendUtils myFriends = plugin.getFriendManager().getPlayer(player);
			PartyUtils myParties = this.getPartyLeader();
			if (plugin.getBoolean("Parties." + myParties.uuid + ".Mute")
					&& !player.getUniqueId().equals(myParties.uuid)) {
				plugin.info(player, plugin.getLanguageManager().getPlayer(player).getString("Party-Mute.Muted-1"));
				return;
			}

			LanguageUtils langUtils = plugin.getLanguageManager().getPlayer(this.uuid);
			plugin.info(myParties.getUniqueId(), langUtils.getString("Chat.Channels.Party") + " > " + myFriends.getDisplayName() + "&f: " + text);

			for (String uniqueId : myParties.getParties()) {
				PartyUtils member = plugin.getPartyManager().getPlayer(uniqueId);
				LanguageUtils targetLangUtils = plugin.getLanguageManager().getPlayer(member.getUniqueId());
				plugin.info(member.getPlayer(), targetLangUtils.getString("Chat.Channels.Party") + " > " + myFriends.getDisplayName() + "&f: " + text);
			}
		}

		/**
		 * @return Party leader
		 */
		public PartyUtils leaveCurrentParty() {
			PartyUtils leader = plugin.getPartyManager().getPlayer(this.getCurrentlyJoinedParty());
			try {
				leader.remove(this.uuid);
			} catch (NotJoinedException e) {
				throw new IllegalStateException("Cannot leave the current party", e);
			}
			return leader;
		}

		public void disband() {
			synchronized (PartyManager.this) {
			if (!this.uuid.toString().equals(this.getCurrentlyJoinedParty()))
				throw new IllegalStateException("Only the party leader can disband");
			List<UUID> detached = new ArrayList<>();
			detached.add(this.uuid);
			for (String member : this.getParties()) detached.add(UUID.fromString(member));
			updateMembers(this.uuid, Collections.emptyList(), detached);
			for (String request : plugin.getStringList("Parties." + this.uuid + ".Requests"))
				plugin.cancelExpiry("party:" + this.uuid + ":" + request);
			plugin.set("Parties." + this.uuid + ".Requests", new ArrayList<>());
			plugin.set("Parties." + this.uuid + ".AllInvite", false);
			plugin.set("Parties." + this.uuid + ".Mute", false);
			plugin.set("Parties." + this.uuid + ".Private", false);
			plugin.getPartyWarpManager().cancelFor(this.uuid);
			if (!plugin.isEnabledMySQL()) plugin.getDataManager().save();
			}
		}

		public boolean isJoinedParty() {
			String currentlyJoinedParty = this.getCurrentlyJoinedParty();
			return !currentlyJoinedParty.equalsIgnoreCase("NONE");
		}

		public PartyUtils getPartyLeader() throws NotJoinedException {
			String currentlyJoinedParty = this.getCurrentlyJoinedParty();
			if (currentlyJoinedParty.equalsIgnoreCase("NONE")) {
				throw new NotJoinedException("Exceptions.Not-Joined");
			}
			return plugin.getPartyManager().getPlayer(currentlyJoinedParty);
		}

		public String getCurrentlyJoinedParty() {
			String currentlyJoinedParty = plugin.getString("Parties." + this.uuid.toString() + ".Currently-Joined-Party");
			if (currentlyJoinedParty == null || currentlyJoinedParty.equals("")) {
				return "NONE";
			}
			return currentlyJoinedParty;
		}

		public boolean isPartyOwner() throws NotJoinedException {
			String leader = this.getCurrentlyJoinedParty();
			if ("NONE".equalsIgnoreCase(leader)) throw new NotJoinedException("Exceptions.Not-Joined");
			return this.uuid.toString().equals(leader);
		}

		public PartyUtils setEnabledReceiveRequest(boolean bool) {
			plugin.set("Parties." + this.uuid.toString() + ".Toggle", bool);
			if (!plugin.isEnabledMySQL()) plugin.getDataManager().save();
			return this;
		}

		public boolean isEnabledReceiveRequest() {
			return plugin.getBoolean("Parties." + this.uuid.toString() + ".Toggle");
		}

		public UUID getUniqueId() {
			return this.uuid;
		}

		public String getDisplayName() {
			return plugin.getFriendManager().getPlayer(this.uuid).getDisplayName();
		}

		public void promote(ProxiedPlayer player) throws NotJoinedException {
			this.promote(player.getUniqueId());
		}

		public void promote(UUID uniqueId) throws NotJoinedException {
			synchronized (PartyManager.this) {
			if (!this.uuid.toString().equals(this.getCurrentlyJoinedParty()))
				throw new NotJoinedException("Disband.Must-Leader");
			List<String> previous = this.getParties();
			if (!previous.contains(uniqueId.toString())) {
				throw new NotJoinedException("Exceptions.Isnt-In-Your-Party");
			}
			List<UUID> participants = new ArrayList<>();
			participants.add(uniqueId);
			participants.add(this.uuid);
			for (String player : previous) if (!player.equals(uniqueId.toString())) participants.add(UUID.fromString(player));
			boolean allInvite = plugin.getBoolean("Parties." + this.uuid + ".AllInvite");
			boolean mute = plugin.getBoolean("Parties." + this.uuid + ".Mute");
			boolean privateParty = plugin.getBoolean("Parties." + this.uuid + ".Private");
			updateMembers(uniqueId, participants, Collections.emptyList());
			plugin.set("Parties." + uniqueId + ".AllInvite", allInvite);
			plugin.set("Parties." + uniqueId + ".Mute", mute);
			plugin.set("Parties." + uniqueId + ".Private", privateParty);
			plugin.set("Parties." + this.uuid + ".AllInvite", false);
			plugin.set("Parties." + this.uuid + ".Mute", false);
			plugin.set("Parties." + this.uuid + ".Private", false);
			for (String request : plugin.getStringList("Parties." + this.uuid + ".Requests"))
				plugin.cancelExpiry("party:" + this.uuid + ":" + request);
			plugin.set("Parties." + this.uuid + ".Requests", new ArrayList<>());
			if (!plugin.isEnabledMySQL()) plugin.getDataManager().save();
			}
		}

		public void hijack(ProxiedPlayer player) {
			this.hijack(player.getUniqueId());
		}

		public void hijack(UUID uniqueId) {
			if (!this.getParties().contains(uniqueId.toString())) {
				try {
					this.add(uniqueId);
				} catch (AlreadyJoinedException | FailedAddingException e) {
					throw new IllegalStateException("Cannot join the party to hijack it", e);
				}
			}

			try {
				this.promote(uniqueId);
			} catch (NotJoinedException e) {
				throw new IllegalStateException("Cannot promote the hijacking player", e);
			}
		}

		public PartyUtils addRequest(ProxiedPlayer player) throws AlreadyJoinedException, FailedInvitingException {
			return this.addRequest(player.getUniqueId());
		}

		public PartyUtils addRequest(UUID uuid) throws AlreadyJoinedException, FailedInvitingException {
			synchronized (PartyManager.this) {
			if (!this.uuid.toString().equals(this.getCurrentlyJoinedParty())
					&& !"NONE".equalsIgnoreCase(this.getCurrentlyJoinedParty()))
				throw new FailedInvitingException("Disband.Must-Leader");
			if (this.uuid.toString().equals(uuid.toString())) {
				throw new FailedInvitingException("Exceptions.Cant-Invite-Self");
			}

			if (!plugin.getBoolean("Parties." + uuid.toString() + ".Toggle")) {
				throw new FailedInvitingException("Exceptions.Disable-Invite");
			}

			List<String> list = this.getParties();
			if (list.size() + 1 >= PartyManager.this.maxSize())
				throw new FailedInvitingException("Exceptions.Party-Full");
			if (list.contains(uuid.toString())) {
				throw new AlreadyJoinedException("Exceptions.Already.In");
			}

			if (!plugin.getString("Parties." + uuid.toString() + ".Currently-Joined-Party").equals("NONE")) {
				throw new AlreadyJoinedException("Exceptions.Already.Joined");
			}

			List<String> requests = plugin.getStringList("Parties." + this.uuid.toString() + ".Requests");
			if (requests.size() >= 5) throw new FailedInvitingException("Exceptions.Too-Many-Invites");
			if (requests.contains(uuid.toString())) {
				throw new FailedInvitingException("Exceptions.Already.Invited");
			}
			plugin.changePartyRequest(this.uuid, uuid, true);
			return this;
			}
		}

		public PartyUtils removeRequest(ProxiedPlayer player) throws NotInvitedException {
			return this.removeRequest(player.getUniqueId());
		}

		public PartyUtils removeRequest(UUID uuid) throws NotInvitedException {
			synchronized (PartyManager.this) {
			List<String> requests = plugin.getStringList("Parties." + this.uuid.toString() + ".Requests");
			if (!requests.contains(uuid.toString())) {
				throw new NotInvitedException("Exceptions.Havent-Received-Invite");
			}
			plugin.changePartyRequest(this.uuid, uuid, false);
			plugin.cancelExpiry("party:" + this.uuid + ":" + uuid);
			return this;
			}
		}

		public List<String> getParties() {
			return plugin.getStringList("Parties." + this.uuid.toString() + ".Party-List");
		}

		public List<String> getRequests() {
			return plugin.getStringList("Parties." + this.uuid + ".Requests");
		}

		public int getOnlinePlayers() {
			int i = 0;
			for (String uniqueId : this.getParties()) {
				ProxiedPlayer player = plugin.getProxy().getPlayer(UUID.fromString(uniqueId));
				if (player != null) {
					i++;
				}
			}
			if (this.getPlayer() != null) {
				i++;
			}
			return i;
		}

		public PartyUtils add(ProxiedPlayer player) throws AlreadyJoinedException, FailedAddingException {
			return this.add(player.getUniqueId());
		}

		public PartyUtils add(UUID uuid) throws AlreadyJoinedException, FailedAddingException {
			synchronized (PartyManager.this) {
			if (!this.uuid.toString().equals(this.getCurrentlyJoinedParty())
					&& !"NONE".equalsIgnoreCase(this.getCurrentlyJoinedParty()))
				throw new FailedAddingException("Exceptions.Not-Joined");
			if (this.uuid.toString().equals(uuid.toString())) {
				throw new FailedAddingException("Exceptions.Your-Self-Invite"); // "You can't add yourself to a party!"
			}

			List<String> list = this.getParties();
			if (list.contains(uuid.toString())) {
				throw new AlreadyJoinedException("Exceptions.Already.In");
						// plugin.getFriendManager().getPlayer(uuid).getDisplayName() + " &cis already in your party!"
			}
			if (list.size() + 1 >= PartyManager.this.maxSize())
				throw new FailedAddingException("Exceptions.Party-Full");
			String joined = plugin.getString("Parties." + uuid + ".Currently-Joined-Party");
			if (joined != null && !joined.isEmpty() && !"NONE".equalsIgnoreCase(joined))
				throw new AlreadyJoinedException("Exceptions.Already.Joined");
			List<UUID> participants = new ArrayList<>();
			participants.add(this.uuid);
			for (String member : list) participants.add(UUID.fromString(member));
			participants.add(uuid);
			updateMembers(this.uuid, participants, Collections.emptyList());
			return this;
			}
		}

		public PartyUtils remove(ProxiedPlayer player) throws NotJoinedException {
			return this.remove(player.getUniqueId());
		}

		public PartyUtils remove(UUID uuid) throws NotJoinedException {
			synchronized (PartyManager.this) {
			if (!this.uuid.toString().equals(this.getCurrentlyJoinedParty()))
				throw new NotJoinedException("Disband.Must-Leader");
			if (this.uuid.toString().equals(uuid.toString())) {
				throw new NotJoinedException("Exceptions.Cant-Remove-Your-Self"); // "&cYou can't remove yourself! Use /party disaband instead!"
			}

			List<String> list = this.getParties();
			if (!list.contains(uuid.toString())) {
				throw new NotJoinedException("Exceptions.Isnt-In-Your-Party");
						// plugin.getFriendManager().getPlayer(uuid).getDisplayName() + " &cisn't in your party! Invite them first!"
			}
			list.remove(uuid.toString());
			List<UUID> participants = new ArrayList<>();
			participants.add(this.uuid);
			for (String member : list) participants.add(UUID.fromString(member));
			if (list.isEmpty()) this.disband();
			else updateMembers(this.uuid, participants, Collections.singletonList(uuid));
			return this;
			}
		}

	}

}
