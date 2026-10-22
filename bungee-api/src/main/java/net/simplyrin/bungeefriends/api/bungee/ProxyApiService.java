package net.simplyrin.bungeefriends.api.bungee;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.simplyrin.bungeefriends.Main;
import net.simplyrin.bungeefriends.api.contract.Friend;
import net.simplyrin.bungeefriends.api.contract.PartyMember;
import net.simplyrin.bungeefriends.api.contract.Page;
import net.simplyrin.bungeefriends.api.contract.PlayerStatus;
import net.simplyrin.bungeefriends.exceptions.NotAddedException;
import net.simplyrin.bungeefriends.utils.FriendManager.FriendUtils;
import net.simplyrin.bungeefriends.utils.PartyManager.PartyUtils;

/** Internal adapter between the wire protocol and the core domain managers. */
final class ProxyApiService {
	private final Main core;
    ProxyApiService(Main core) { this.core = core; }

    List<String> friendIds(UUID player) { return new ArrayList<>(friend(player).getFriends()); }
    boolean areFriends(UUID first, UUID second) { return friend(first).isFriend(second); }
    String friendNickname(UUID viewer, UUID target) {
        requireFriend(viewer, target);
        String value = core.getFriendNickname(viewer, target);
        return value == null ? "" : value;
    }
    boolean isBestFriend(UUID viewer, UUID target) {
        requireFriend(viewer, target);
        return core.isBestFriend(viewer, target);
    }
    boolean friendNotificationsEnabled(UUID player) { return core.areFriendNotificationsEnabled(player); }
    boolean friendRequestsEnabled(UUID player) { return friend(player).isEnabledReceiveRequest(); }
    boolean partyInvitesEnabled(UUID player) { return party(player).isEnabledReceiveRequest(); }
    PlayerStatus status(UUID player) { return PlayerStatus.valueOf(core.getStatusManager().getStatus(player).name()); }
    List<String> ignored(UUID player) { return new ArrayList<>(friend(player).getIgnoreList()); }
    Page<Friend> friends(UUID player, int offset, int limit) {
        List<String> ids = friendIds(player);
        int start = start(offset, limit, ids.size()), end = Math.min(ids.size(), start + limit);
        List<Friend> result = new ArrayList<>();
        for (String id : ids.subList(start, end)) {
            UUID target = UUID.fromString(id);
            FriendUtils info = friend(target);
            result.add(new Friend(target, info.getName(), info.getDisplayNameFor(player), info.getPrefix(), info.getPlayer() != null));
        }
        return new Page<>(result, offset, limit, ids.size());
    }
    List<String> partyIds(UUID player) {
        UUID owner = owner(player);
        return owner == null ? Collections.emptyList() : new ArrayList<>(party(owner).getParties());
    }
    Page<PartyMember> partyMembers(UUID player, int offset, int limit) {
        UUID owner = owner(player);
        if (owner == null) return new Page<>(Collections.<PartyMember>emptyList(), offset, limit, 0);
        List<String> ids = new ArrayList<>();
        ids.add(owner.toString());
        ids.addAll(party(owner).getParties());
        int start = start(offset, limit, ids.size()), end = Math.min(ids.size(), start + limit);
        List<PartyMember> result = new ArrayList<>();
        for (int i = start; i < end; i++) result.add(member(UUID.fromString(ids.get(i)), i == 0));
        return new Page<>(result, offset, limit, ids.size());
    }
    boolean inParty(UUID player) { return party(player).isJoinedParty(); }
    boolean leader(UUID player) { try { return party(player).isPartyOwner(); } catch (Exception e) { return false; } }
    boolean isPrivate(UUID player) { UUID owner = owner(player); return owner != null && core.getBoolean("Parties." + owner + ".Private"); }
    UUID owner(UUID player) {
        try { return party(player).getPartyLeader().getUniqueId(); }
        catch (Exception e) { return null; }
    }

    void requestFriend(UUID actor, UUID target) throws Exception { friend(actor).addRequest(target); }
    void acceptFriend(UUID actor, UUID requester) throws Exception {
        if (!friend(requester).getRequests().contains(actor.toString())) throw new NotAddedException();
        String created = core.getFriendRequestCreatedAt(requester, actor);
        long sentAt;
        try { sentAt = Long.parseLong(created); } catch (NumberFormatException e) { sentAt = 0; }
        if (sentAt <= 0 || System.currentTimeMillis() - sentAt > 300_000L) {
            try { friend(requester).removeRequest(actor); } catch (NotAddedException ignored) { }
            throw new NotAddedException();
        }
        friend(actor).add(requester);
        friend(requester).removeRequest(actor);
    }
    void removeFriend(UUID actor, UUID target) throws Exception { friend(actor).remove(target); }
    void setFriendNickname(UUID actor, UUID target, String nickname) {
        requireFriend(actor, target);
        if (nickname == null) nickname = "";
        nickname = nickname.trim();
        if (nickname.length() > 255 || nickname.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Nickname must be at most 255 characters and contain no control characters");
        FriendUtils targetInfo = friend(target);
        if (nickname.isEmpty() || nickname.equalsIgnoreCase(targetInfo.getName())) core.clearFriendNickname(actor, target);
        else core.setFriendNickname(actor, target, nickname);
    }
    void setBestFriend(UUID actor, UUID target, boolean best) {
        requireFriend(actor, target);
        core.setBestFriend(actor, target, best);
    }
    void setFriendNotificationsEnabled(UUID actor, boolean enabled) { core.setFriendNotificationsEnabled(actor, enabled); }
    void ignore(UUID actor, UUID target) throws Exception { friend(actor).addIgnore(target); }
    void unignore(UUID actor, UUID target) throws Exception { friend(actor).removeIgnore(target); }
    void setFriendRequests(UUID actor, boolean enabled) {
        core.set("Player." + actor + ".Toggle", enabled);
        if (!core.isEnabledMySQL()) core.getDataManager().save();
    }
    void setPartyInvites(UUID actor, boolean enabled) { party(actor).setEnabledReceiveRequest(enabled); }
    void setStatus(UUID actor, PlayerStatus status) {
        if (status == null) throw new IllegalArgumentException("Status is required");
        core.getStatusManager().setStatus(actor, net.simplyrin.bungeefriends.utils.StatusManager.Status.valueOf(status.name()));
    }
    void invite(UUID actor, UUID target) throws Exception { party(actor).addRequest(target); }
    void acceptInvite(UUID actor, UUID leader) throws Exception { party(leader).acceptRequest(actor); }
    void revokeInvite(UUID actor, UUID target) throws Exception {
        if (!ProxyPolicy.canModifyParty(actor, target, owner(actor), true)) throw new IllegalStateException("Party leader required");
        party(actor).removeRequest(target);
    }
    void leave(UUID actor) { party(actor).leaveCurrentParty(); }
    void kick(UUID actor, UUID target) throws Exception {
        if (!ProxyPolicy.canModifyParty(actor, target, owner(actor), false)) throw new IllegalStateException("Party leader cannot kick self or another party");
        party(actor).remove(target);
    }
    void transfer(UUID actor, UUID target) throws Exception {
        if (!ProxyPolicy.canModifyParty(actor, target, owner(actor), true) || !party(actor).getParties().contains(target.toString()))
            throw new IllegalStateException("Leadership can only transfer to a party member");
        party(actor).promote(target);
    }
    void disband(UUID actor) { party(actor).disband(); }
    void setPrivate(UUID actor, boolean value) {
        if (!leader(actor)) throw new IllegalStateException("Party leader required");
        core.set("Parties." + actor + ".Private", value);
        if (!core.isEnabledMySQL()) core.getDataManager().save();
    }
    void chat(UUID actor, String message) throws Exception {
        ProxiedPlayer player = ProxyServer.getInstance().getPlayer(actor);
        if (player == null) throw new IllegalStateException("Player is offline");
        party(actor).partyChat(player, message);
    }

    private FriendUtils friend(UUID uuid) { return core.getFriendManager().getPlayer(uuid); }
    private void requireFriend(UUID viewer, UUID target) {
        if (!friend(viewer).isFriend(target)) throw new IllegalStateException("Target is not a friend");
    }
    private PartyUtils party(UUID uuid) { return core.getPartyManager().getPlayer(uuid); }
    private PartyMember member(UUID uuid, boolean leader) {
        FriendUtils info = friend(uuid);
        return new PartyMember(uuid, info.getName(), info.getDisplayName(), info.getPlayer() != null, leader);
    }
    private static int start(int offset, int limit, int total) {
        if (offset < 0 || limit < 1 || limit > 100) throw new IllegalArgumentException("Page limit must be between 1 and 100");
        return Math.min(offset, total);
    }
}
