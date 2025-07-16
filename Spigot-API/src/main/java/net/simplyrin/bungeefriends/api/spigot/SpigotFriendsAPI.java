package net.simplyrin.bungeefriends.api.spigot;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * BungeeFriends API的Spigot端接口
 * 提供给其他Spigot插件使用的API接口
 */
public class SpigotFriendsAPI {

    private static SpigotFriendsAPI instance;
    private final BungeeFriendsAPISpigot plugin;

    private SpigotFriendsAPI() {
        this.plugin = BungeeFriendsAPISpigot.getInstance();
    }

    /**
     * 获取API实例
     * @return API实例
     */
    public static SpigotFriendsAPI getInstance() {
        if (instance == null) {
            instance = new SpigotFriendsAPI();
        }
        return instance;
    }

    /**
     * 获取玩家的好友列表
     * @param player 玩家
     * @return 好友UUID列表的Future
     */
    @SuppressWarnings("unchecked")
    public CompletableFuture<List<String>> getFriendList(Player player) {
        return (CompletableFuture<List<String>>) (CompletableFuture<?>) plugin.getFriendList(player);
    }

    /**
     * 获取玩家的好友详细信息列表
     * @param player 玩家
     * @return 好友详细信息列表的Future
     */
    @SuppressWarnings("unchecked")
    public CompletableFuture<List<Map<String, Object>>> getFriendDetailList(Player player) {
        return (CompletableFuture<List<Map<String, Object>>>) (CompletableFuture<?>) plugin.getFriendDetailList(player);
    }

    /**
     * 通过玩家名称获取好友详细信息列表
     * @param playerName 玩家名称
     * @return 好友详细信息列表的Future
     */
    @SuppressWarnings("unchecked")
    public CompletableFuture<List<Map<String, Object>>> getFriendDetailListByName(String playerName) {
        return (CompletableFuture<List<Map<String, Object>>>) (CompletableFuture<?>) plugin.getFriendDetailListByName(playerName);
    }

    /**
     * 获取玩家的组队成员列表
     * @param player 玩家
     * @return 组队成员UUID列表的Future
     */
    @SuppressWarnings("unchecked")
    public CompletableFuture<List<String>> getPartyMembers(Player player) {
        return plugin.getPartyMembers(player).thenApply(result -> {
            if (result instanceof Map) {
                Map<String, Object> response = (Map<String, Object>) result;
                return (List<String>) response.get("data");
            }
            return null;
        });
    }

    /**
     * 获取玩家的组队成员详细信息列表
     * @param player 玩家
     * @return 组队成员详细信息列表的Future
     */
    @SuppressWarnings("unchecked")
    public CompletableFuture<List<Map<String, Object>>> getPartyMemberDetailList(Player player) {
        return (CompletableFuture<List<Map<String, Object>>>) (CompletableFuture<?>) plugin.getPartyMemberDetailList(player);
    }

    /**
     * 通过玩家名称获取组队成员详细信息列表
     * @param playerName 玩家名称
     * @return 组队成员详细信息列表的Future
     */
    @SuppressWarnings("unchecked")
    public CompletableFuture<List<Map<String, Object>>> getPartyMemberDetailListByName(String playerName) {
        return (CompletableFuture<List<Map<String, Object>>>) (CompletableFuture<?>) plugin.getPartyMemberDetailListByName(playerName);
    }
    
    /**
     * 通过玩家UUID获取组队成员详细信息列表
     * @param uuid 玩家UUID
     * @return 组队成员详细信息列表的Future
     */
    @SuppressWarnings("unchecked")
    public CompletableFuture<List<Map<String, Object>>> getPartyMemberDetailListByUUID(String uuid) {
        return (CompletableFuture<List<Map<String, Object>>>) (CompletableFuture<?>) plugin.getPartyMemberDetailListByUUID(uuid);
    }
    
    /**
     * 检查两个玩家是否是好友
     * @param player 玩家
     * @param targetName 目标玩家名称
     * @return 是否是好友的Future
     */
    public CompletableFuture<Boolean> areFriends(Player player, String targetName) {
        return plugin.areFriends(player, targetName).thenApply(result -> {
            if (result instanceof Map) {
                Map<String, Object> response = (Map<String, Object>) result;
                return (Boolean) response.get("data");
            }
            return false;
        });
    }

    /**
     * 检查玩家是否在组队中
     * @param player 玩家
     * @return 是否在组队中的Future
     */
    public CompletableFuture<Boolean> isInParty(Player player) {
        return plugin.isInParty(player).thenApply(result -> (Boolean) result);
    }

    /**
     * 检查玩家是否是组队队长
     * @param player 玩家
     * @return 是否是组队队长的Future
     */
    public CompletableFuture<Boolean> isPartyLeader(Player player) {
        return plugin.isPartyLeader(player).thenApply(result -> (Boolean) result);
    }

    /**
     * 获取好友信息类
     */
    public static class FriendInfo {
        private final String uuid;
        private final String name;
        private final String displayName;
        private final String prefix;
        private final boolean online;

        public FriendInfo(Map<String, Object> data) {
            this.uuid = (String) data.get("uuid");
            this.name = (String) data.get("name");
            this.displayName = (String) data.get("displayName");
            this.prefix = (String) data.get("prefix");
            this.online = (Boolean) data.get("online");
        }

        public String getUuid() {
            return uuid;
        }

        public String getName() {
            return name;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getPrefix() {
            return prefix;
        }

        public boolean isOnline() {
            return online;
        }
    }

    /**
     * 获取组队成员信息类
     */
    public static class PartyMemberInfo {
        private final String uuid;
        private final String name;
        private final String displayName;
        private final boolean online;
        private final boolean leader;

        public PartyMemberInfo(Map<String, Object> data) {
            this.uuid = (String) data.get("uuid");
            this.name = (String) data.get("name");
            this.displayName = (String) data.get("displayName");
            this.online = (Boolean) data.get("online");
            this.leader = (Boolean) data.get("leader");
        }

        public String getUuid() {
            return uuid;
        }

        public String getName() {
            return name;
        }

        public String getDisplayName() {
            return displayName;
        }

        public boolean isOnline() {
            return online;
        }

        public boolean isLeader() {
            return leader;
        }
    }

    /**
     * 发送自定义请求到Bungee
     * 用于发送命令执行请求等自定义操作
     * @param player 玩家
     * @param requestType 请求类型
     * @param args 参数
     * @return 响应的Future
     */
    @SuppressWarnings("unchecked")
    public CompletableFuture<Object> sendCustomRequest(Player player, String requestType, String... args) {
        return plugin.sendCustomRequest(player, requestType, args);
    }
}