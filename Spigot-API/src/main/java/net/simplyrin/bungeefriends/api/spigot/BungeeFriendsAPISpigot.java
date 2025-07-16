package net.simplyrin.bungeefriends.api.spigot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.reflect.TypeToken;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BungeeFriends API Spigot端插件
 * 用于向Bungee发送API请求并处理响应
 */
public class BungeeFriendsAPISpigot extends JavaPlugin implements PluginMessageListener {

    private static final String CHANNEL = "bungeefriends:api";
    private static BungeeFriendsAPISpigot instance;
    private Gson gson;
    
    // 存储待处理的请求
    private final Map<UUID, Map<String, CompletableFuture<Object>>> pendingRequests = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        // 设置实例
        instance = this;
        
        // 初始化Gson
        this.gson = new GsonBuilder().create();
        
        // 注册插件消息通道
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeFriends");
        getServer().getMessenger().registerIncomingPluginChannel(this, "BungeeFriends", this);
        
        // 注册命令
        getCommand("bfspigot").setExecutor(this);
        
        // 注册PartyCmdSender监听器
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(new PartyCmdSenderListener(this), this);
        
        getLogger().info("BungeeFriends API Spigot端已启用！");
    }

    @Override
    public void onDisable() {
        // 注销插件消息通道
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, "BungeeFriends");
        getServer().getMessenger().unregisterIncomingPluginChannel(this, "BungeeFriends", this);
        
        // 清除所有待处理的请求
        pendingRequests.clear();
        
        getLogger().info("BungeeFriends API Spigot端已禁用！");
    }

    /**
     * 获取插件实例
     */
    public static BungeeFriendsAPISpigot getInstance() {
        return instance;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "此命令只能由玩家执行！");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "help":
                sendHelp(player);
                break;
                
            case "friends":
                // 获取好友列表
                getFriendList(player).thenAccept(result -> {
                    if (result instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<String> friends = (List<String>) result;
                        player.sendMessage(ChatColor.GOLD + "=== 你的好友列表 (通过API) ===");
                        if (friends.isEmpty()) {
                            player.sendMessage(ChatColor.GRAY + "你还没有好友。");
                            return;
                        }
                        for (String friendUUID : friends) {
                            player.sendMessage(ChatColor.GRAY + "- " + friendUUID);
                        }
                    } else {
                        player.sendMessage(ChatColor.RED + "获取好友列表失败！");
                    }
                }).exceptionally(ex -> {
                    player.sendMessage(ChatColor.RED + "获取好友列表时出错: " + ex.getMessage());
                    return null;
                });
                break;
                
            case "frienddetails":
                // 获取好友详细信息列表
                getFriendDetailList(player).thenAccept(result -> {
                    if (result instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> friendDetails = (List<Map<String, Object>>) result;
                        player.sendMessage(ChatColor.GOLD + "=== 你的好友详细信息列表 (通过API) ===");
                        if (friendDetails.isEmpty()) {
                            player.sendMessage(ChatColor.GRAY + "你还没有好友。");
                            return;
                        }
                        for (Map<String, Object> friend : friendDetails) {
                            boolean isOnline = (boolean) friend.get("online");
                            String status = isOnline ? ChatColor.GREEN + "在线" : ChatColor.RED + "离线";
                            player.sendMessage(ChatColor.GRAY + "- " + friend.get("displayName") + " " + ChatColor.GRAY + "[" + status + ChatColor.GRAY + "]");
                            player.sendMessage("  " + ChatColor.DARK_GRAY + "UUID: " + ChatColor.GRAY + friend.get("uuid"));
                            player.sendMessage("  " + ChatColor.DARK_GRAY + "名称: " + ChatColor.GRAY + friend.get("name"));
                            player.sendMessage("  " + ChatColor.DARK_GRAY + "前缀: " + ChatColor.GRAY + friend.get("prefix"));
                        }
                    } else {
                        player.sendMessage(ChatColor.RED + "获取好友详细信息列表失败！");
                    }
                }).exceptionally(ex -> {
                    player.sendMessage(ChatColor.RED + "获取好友详细信息列表时出错: " + ex.getMessage());
                    return null;
                });
                break;
                
            case "frienddetailsbyname":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "用法: /bfspigot frienddetailsbyname <玩家名称>");
                    return true;
                }
                // 通过玩家名称获取好友详细信息列表
                getFriendDetailListByName(args[1]).thenAccept(result -> {
                    if (result instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> friendDetails = (List<Map<String, Object>>) result;
                        player.sendMessage(ChatColor.GOLD + "=== " + args[1] + " 的好友详细信息列表 (通过API) ===");
                        if (friendDetails == null) {
                            player.sendMessage(ChatColor.RED + "找不到玩家 " + args[1] + " 的信息。");
                            return;
                        }
                        if (friendDetails.isEmpty()) {
                            player.sendMessage(ChatColor.GRAY + "该玩家还没有好友。");
                            return;
                        }
                        for (Map<String, Object> friend : friendDetails) {
                            boolean isOnline = (boolean) friend.get("online");
                            String status = isOnline ? ChatColor.GREEN + "在线" : ChatColor.RED + "离线";
                            player.sendMessage(ChatColor.GRAY + "- " + friend.get("displayName") + " " + ChatColor.GRAY + "[" + status + ChatColor.GRAY + "]");
                            player.sendMessage("  " + ChatColor.DARK_GRAY + "UUID: " + ChatColor.GRAY + friend.get("uuid"));
                            player.sendMessage("  " + ChatColor.DARK_GRAY + "名称: " + ChatColor.GRAY + friend.get("name"));
                            player.sendMessage("  " + ChatColor.DARK_GRAY + "前缀: " + ChatColor.GRAY + friend.get("prefix"));
                        }
                    } else {
                        player.sendMessage(ChatColor.RED + "获取好友详细信息列表失败！");
                    }
                }).exceptionally(ex -> {
                    player.sendMessage(ChatColor.RED + "获取好友详细信息列表时出错: " + ex.getMessage());
                    return null;
                });
                break;
                
            case "party":
                // 获取组队成员列表
                getPartyMembers(player).thenAccept(result -> {
                    player.sendMessage(ChatColor.GOLD + "=== 你的组队成员列表 (通过API) ===");
                    if (result instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<String> members = (List<String>) result;
                        if (members.isEmpty()) {
                            player.sendMessage(ChatColor.GRAY + "你的组队中没有其他成员。");
                            return;
                        }
                        for (String memberUUID : members) {
                            player.sendMessage(ChatColor.GRAY + "- " + memberUUID);
                        }
                    } else if (result instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> response = (Map<String, Object>) result;
                        boolean success = (boolean) response.getOrDefault("success", false);
                        if (success) {
                            @SuppressWarnings("unchecked")
                            List<String> members = (List<String>) response.get("data");
                            if (members == null || members.isEmpty()) {
                                player.sendMessage(ChatColor.GRAY + "你的组队中没有其他成员。");
                                return;
                            }
                            for (String memberUUID : members) {
                                player.sendMessage(ChatColor.GRAY + "- " + memberUUID);
                            }
                        } else {
                            String error = (String) response.getOrDefault("error", "未知错误");
                            player.sendMessage(ChatColor.RED + "获取组队成员列表失败: " + error);
                        }
                    } else {
                        player.sendMessage(ChatColor.RED + "获取组队成员列表失败！");
                    }
                }).exceptionally(ex -> {
                    player.sendMessage(ChatColor.RED + "获取组队成员列表时出错: " + ex.getMessage());
                    return null;
                });
                break;
                
            case "partydetails":
                // 获取组队成员详细信息列表
                getPartyMemberDetailList(player).thenAccept(result -> {
                    if (result instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> partyDetails = (List<Map<String, Object>>) result;
                        player.sendMessage(ChatColor.GOLD + "=== 你的组队成员详细信息列表 (通过API) ===");
                        if (partyDetails.isEmpty()) {
                            player.sendMessage(ChatColor.RED + "你当前不在任何组队中。");
                            return;
                        }
                        player.sendMessage(ChatColor.YELLOW + "成员数量: " + ChatColor.GRAY + partyDetails.size());
                        for (Map<String, Object> member : partyDetails) {
                            boolean isOnline = (boolean) member.get("online");
                            boolean isLeader = (boolean) member.get("leader");
                            String uuid = (String) member.get("uuid");
                            boolean isYou = uuid.equals(player.getUniqueId().toString());
                            
                            String status = isOnline ? ChatColor.GREEN + "在线" : ChatColor.RED + "离线";
                            String leaderTag = isLeader ? " " + ChatColor.GOLD + "[队长]" : "";
                            String youTag = isYou ? " " + ChatColor.GREEN + "(你)" : "";
                            
                            player.sendMessage(ChatColor.GRAY + "- " + member.get("displayName") + youTag + leaderTag + " " + ChatColor.GRAY + "[" + status + ChatColor.GRAY + "]");
                            player.sendMessage("  " + ChatColor.DARK_GRAY + "UUID: " + ChatColor.GRAY + member.get("uuid"));
                            player.sendMessage("  " + ChatColor.DARK_GRAY + "名称: " + ChatColor.GRAY + member.get("name"));
                        }
                    } else {
                        player.sendMessage(ChatColor.RED + "获取组队成员详细信息列表失败！");
                    }
                }).exceptionally(ex -> {
                    player.sendMessage(ChatColor.RED + "获取组队成员详细信息列表时出错: " + ex.getMessage());
                    return null;
                });
                break;
                
            case "partydetailsbyname":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "用法: /bfspigot partydetailsbyname <玩家名称>");
                    return true;
                }
                // 通过玩家名称获取组队成员详细信息列表
                getPartyMemberDetailListByName(args[1]).thenAccept(result -> {
                    if (result instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> partyDetails = (List<Map<String, Object>>) result;
                        if (partyDetails == null) {
                            player.sendMessage(ChatColor.RED + "找不到玩家 " + args[1] + " 的信息。");
                            return;
                        }
                        if (partyDetails.isEmpty()) {
                            player.sendMessage(ChatColor.RED + "该玩家当前不在任何组队中。");
                            return;
                        }
                        player.sendMessage(ChatColor.GOLD + "=== " + args[1] + " 的组队成员详细信息列表 (通过API) ===");
                        player.sendMessage(ChatColor.YELLOW + "成员数量: " + ChatColor.GRAY + partyDetails.size());
                        for (Map<String, Object> member : partyDetails) {
                            boolean isOnline = (boolean) member.get("online");
                            boolean isLeader = (boolean) member.get("leader");
                            String name = (String) member.get("name");
                            boolean isTarget = name.equalsIgnoreCase(args[1]);
                            
                            String status = isOnline ? ChatColor.GREEN + "在线" : ChatColor.RED + "离线";
                            String leaderTag = isLeader ? " " + ChatColor.GOLD + "[队长]" : "";
                            String targetTag = isTarget ? " " + ChatColor.GREEN + "(查询目标)" : "";
                            
                            player.sendMessage(ChatColor.GRAY + "- " + member.get("displayName") + targetTag + leaderTag + " " + ChatColor.GRAY + "[" + status + ChatColor.GRAY + "]");
                            player.sendMessage("  " + ChatColor.DARK_GRAY + "UUID: " + ChatColor.GRAY + member.get("uuid"));
                            player.sendMessage("  " + ChatColor.DARK_GRAY + "名称: " + ChatColor.GRAY + member.get("name"));
                        }
                    } else {
                        player.sendMessage(ChatColor.RED + "获取组队成员详细信息列表失败！");
                    }
                }).exceptionally(ex -> {
                    player.sendMessage(ChatColor.RED + "获取组队成员详细信息列表时出错: " + ex.getMessage());
                    return null;
                });
                break;
                
            case "check":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "用法: /bfspigot check <玩家名称>");
                    return true;
                }
                // 检查是否是好友
                areFriends(player, args[1]).thenAccept(result -> {
                    if (result instanceof Boolean) {
                        boolean areFriends = (boolean) result;
                        player.sendMessage(ChatColor.GOLD + "=== 好友关系检查 (通过API) ===");
                        player.sendMessage(ChatColor.YELLOW + "玩家: " + ChatColor.GRAY + args[1]);
                        player.sendMessage(ChatColor.YELLOW + "是否是好友: " + ChatColor.GRAY + (areFriends ? ChatColor.GREEN + "是" : ChatColor.RED + "否"));
                    } else if (result instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> response = (Map<String, Object>) result;
                        boolean success = (boolean) response.getOrDefault("success", false);
                        if (success) {
                            boolean areFriends = (boolean) response.get("data");
                            player.sendMessage(ChatColor.GOLD + "=== 好友关系检查 (通过API) ===");
                            player.sendMessage(ChatColor.YELLOW + "玩家: " + ChatColor.GRAY + args[1]);
                            player.sendMessage(ChatColor.YELLOW + "是否是好友: " + ChatColor.GRAY + (areFriends ? ChatColor.GREEN + "是" : ChatColor.RED + "否"));
                        } else {
                            String error = (String) response.getOrDefault("error", "未知错误");
                            player.sendMessage(ChatColor.RED + "检查好友关系失败: " + error);
                        }
                    } else {
                        player.sendMessage(ChatColor.RED + "检查好友关系失败！");
                    }
                }).exceptionally(ex -> {
                    player.sendMessage(ChatColor.RED + "检查好友关系时出错: " + ex.getMessage());
                    return null;
                });
                break;
                
            case "inparty":
                // 检查是否在组队中
                isInParty(player).thenAccept(result -> {
                    if (result instanceof Boolean) {
                        boolean isInParty = (boolean) result;
                        player.sendMessage(ChatColor.GOLD + "=== 组队状态检查 (通过API) ===");
                        player.sendMessage(ChatColor.YELLOW + "是否在组队中: " + ChatColor.GRAY + (isInParty ? ChatColor.GREEN + "是" : ChatColor.RED + "否"));
                    } else {
                        player.sendMessage(ChatColor.RED + "检查组队状态失败！");
                    }
                }).exceptionally(ex -> {
                    player.sendMessage(ChatColor.RED + "检查组队状态时出错: " + ex.getMessage());
                    return null;
                });
                break;
                
            case "isleader":
                // 检查是否是组队队长
                isPartyLeader(player).thenAccept(result -> {
                    if (result instanceof Boolean) {
                        boolean isLeader = (boolean) result;
                        player.sendMessage(ChatColor.GOLD + "=== 组队队长检查 (通过API) ===");
                        player.sendMessage(ChatColor.YELLOW + "是否是队长: " + ChatColor.GRAY + (isLeader ? ChatColor.GREEN + "是" : ChatColor.RED + "否"));
                    } else {
                        player.sendMessage(ChatColor.RED + "检查组队队长状态失败！");
                    }
                }).exceptionally(ex -> {
                    player.sendMessage(ChatColor.RED + "检查组队队长状态时出错: " + ex.getMessage());
                    return null;
                });
                break;
                
            case "info":
                player.sendMessage(ChatColor.GOLD + "=== BungeeFriends API Spigot端信息 ===");
                player.sendMessage(ChatColor.YELLOW + "版本: " + ChatColor.GRAY + "1.0.0");
                player.sendMessage(ChatColor.YELLOW + "作者: " + ChatColor.GRAY + "Trae AI");
                player.sendMessage(ChatColor.YELLOW + "描述: " + ChatColor.GRAY + "BungeeFriends API的Spigot端实现，用于与Bungee端通信");
                break;
                
            default:
                sendHelp(player);
                break;
        }

        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(ChatColor.BLUE + "----------------------------------------------------");
        player.sendMessage(ChatColor.YELLOW + "/bfspigot help " + ChatColor.GRAY + "- 显示帮助信息");
        player.sendMessage(ChatColor.YELLOW + "/bfspigot friends " + ChatColor.GRAY + "- 显示你的好友列表");
        player.sendMessage(ChatColor.YELLOW + "/bfspigot frienddetails " + ChatColor.GRAY + "- 显示你的好友详细信息列表");
        player.sendMessage(ChatColor.YELLOW + "/bfspigot frienddetailsbyname <玩家名> " + ChatColor.GRAY + "- 显示指定玩家的好友详细信息列表");
        player.sendMessage(ChatColor.YELLOW + "/bfspigot party " + ChatColor.GRAY + "- 显示你的组队信息");
        player.sendMessage(ChatColor.YELLOW + "/bfspigot partydetails " + ChatColor.GRAY + "- 显示你的组队成员详细信息列表");
        player.sendMessage(ChatColor.YELLOW + "/bfspigot partydetailsbyname <玩家名> " + ChatColor.GRAY + "- 显示指定玩家的组队成员详细信息列表");
        player.sendMessage(ChatColor.YELLOW + "/bfspigot check <玩家名> " + ChatColor.GRAY + "- 检查与指定玩家的好友关系");
        player.sendMessage(ChatColor.YELLOW + "/bfspigot inparty " + ChatColor.GRAY + "- 检查你是否在组队中");
        player.sendMessage(ChatColor.YELLOW + "/bfspigot isleader " + ChatColor.GRAY + "- 检查你是否是组队队长");
        player.sendMessage(ChatColor.YELLOW + "/bfspigot info " + ChatColor.GRAY + "- 显示API信息");
        player.sendMessage(ChatColor.BLUE + "----------------------------------------------------");
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals("BungeeFriends")) {
            return;
        }

        try {
            String jsonMessage = new String(message, StandardCharsets.UTF_8);
            JsonObject jsonObject = gson.fromJson(jsonMessage, JsonObject.class);
            String requestId = jsonObject.get("requestId").getAsString();
            handleResponse(requestId, jsonObject);
        } catch (Exception e) {
            getLogger().severe(ChatColor.RED + "处理插件消息时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 处理来自Bungee的响应（新版本）
     */
    private void handleResponse(String requestId, JsonObject jsonObject) {
        try {
            // 从请求ID中提取玩家UUID和请求类型
            String[] parts = requestId.split(":");
            if (parts.length != 2) {
                getLogger().warning("无效的请求ID格式: " + requestId);
                return;
            }
            
            UUID playerUUID = UUID.fromString(parts[0]);
            String requestType = parts[1];
            
            // 获取该玩家的待处理请求
            Map<String, CompletableFuture<Object>> playerRequests = pendingRequests.get(playerUUID);
            if (playerRequests == null) {
                getLogger().warning("收到了未请求的响应: " + requestType + " 对于玩家 " + playerUUID);
                return;
            }
            
            // 获取对应请求类型的Future
            CompletableFuture<Object> future = playerRequests.remove(requestType);
            if (future == null) {
                getLogger().warning("收到了未请求的响应类型: " + requestType + " 对于玩家 " + playerUUID);
                return;
            }
            
            // 如果玩家没有更多待处理请求，从map中移除
            if (playerRequests.isEmpty()) {
                pendingRequests.remove(playerUUID);
            }
            
            // 解析响应数据
            boolean success = jsonObject.has("success") ? jsonObject.get("success").getAsBoolean() : true;
            
            if (success) {
                // 将JsonElement转换为Java对象
                Object data = null;
                if (jsonObject.has("data")) {
                    JsonElement dataElement = jsonObject.get("data");
                    if (dataElement.isJsonArray()) {
                        Type listType = new TypeToken<List<Object>>(){}.getType();
                        data = gson.fromJson(dataElement, listType);
                    } else if (dataElement.isJsonObject()) {
                        Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
                        data = gson.fromJson(dataElement, mapType);
                    } else if (dataElement.isJsonPrimitive()) {
                        JsonPrimitive primitive = dataElement.getAsJsonPrimitive();
                        if (primitive.isBoolean()) {
                            data = primitive.getAsBoolean();
                        } else if (primitive.isNumber()) {
                            data = primitive.getAsNumber();
                        } else if (primitive.isString()) {
                            data = primitive.getAsString();
                        }
                    }
                }
                future.complete(data);
            } else {
                String error = jsonObject.has("error") ? jsonObject.get("error").getAsString() : "未知错误";
                future.completeExceptionally(new RuntimeException(error));
            }
        } catch (Exception e) {
            getLogger().severe("处理响应时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 处理来自Bungee的响应
     */
    private void handleResponse(UUID playerUUID, String requestType, Map<String, Object> response) {
        // 获取该玩家的待处理请求
        Map<String, CompletableFuture<Object>> playerRequests = pendingRequests.get(playerUUID);
        if (playerRequests == null) {
            getLogger().warning("收到了未请求的响应: " + requestType + " 对于玩家 " + playerUUID);
            return;
        }
        
        // 获取对应请求类型的Future
        CompletableFuture<Object> future = playerRequests.remove(requestType);
        if (future == null) {
            getLogger().warning("收到了未请求的响应类型: " + requestType + " 对于玩家 " + playerUUID);
            return;
        }
        
        // 如果玩家没有更多待处理请求，从map中移除
        if (playerRequests.isEmpty()) {
            pendingRequests.remove(playerUUID);
        }
        
        // 完成Future
        boolean success = (boolean) response.getOrDefault("success", true);
        if (success) {
            future.complete(response.get("data"));
        } else {
            String error = (String) response.getOrDefault("error", "未知错误");
            future.completeExceptionally(new RuntimeException(error));
        }
    }

    /**
     * 发送请求到Bungee
     */
    private CompletableFuture<Object> sendRequest(Player player, String requestType, String... args) {
        CompletableFuture<Object> future = new CompletableFuture<>();
        
        try {
            // 获取或创建该玩家的待处理请求map
            Map<String, CompletableFuture<Object>> playerRequests = pendingRequests.computeIfAbsent(
                    player.getUniqueId(), k -> new ConcurrentHashMap<>());
            
            // 存储Future
            playerRequests.put(requestType, future);
            
            // 创建请求数据
            Map<String, Object> requestData = new HashMap<>();
            requestData.put("requestType", requestType);
            
            // 生成请求ID (UUID:requestType)
            String requestId = player.getUniqueId().toString() + ":" + requestType;
            requestData.put("requestId", requestId);
            
            // 添加参数
            if (args.length > 0) {
                requestData.put("args", args);
            }
            
            // 转换为JSON
            String jsonRequest = gson.toJson(requestData);
            
            // 发送请求
            player.sendPluginMessage(this, "BungeeFriends", jsonRequest.getBytes(StandardCharsets.UTF_8));
            
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        
        return future;
    }

    // API方法

    /**
     * 获取玩家的好友列表
     */
    public CompletableFuture<Object> getFriendList(Player player) {
        return sendRequest(player, "getFriendList");
    }

    /**
     * 获取玩家的好友详细信息列表
     */
    public CompletableFuture<Object> getFriendDetailList(Player player) {
        return sendRequest(player, "getFriendDetailList");
    }

    /**
     * 通过玩家名称获取好友详细信息列表
     */
    public CompletableFuture<Object> getFriendDetailListByName(String playerName) {
        Player player = Bukkit.getOnlinePlayers().iterator().next();
        if (player == null) {
            CompletableFuture<Object> future = new CompletableFuture<>();
            future.completeExceptionally(new RuntimeException("没有在线玩家可以发送请求"));
            return future;
        }
        return sendRequest(player, "getFriendDetailListByName", playerName);
    }

    /**
     * 获取玩家的组队成员列表
     */
    public CompletableFuture<Object> getPartyMembers(Player player) {
        return sendRequest(player, "getPartyMembers");
    }

    /**
     * 获取玩家的组队成员详细信息列表
     */
    public CompletableFuture<Object> getPartyMemberDetailList(Player player) {
        return sendRequest(player, "getPartyMemberDetailList");
    }

    /**
     * 通过玩家名称获取组队成员详细信息列表
     */
    public CompletableFuture<Object> getPartyMemberDetailListByName(String playerName) {
        Player player = Bukkit.getOnlinePlayers().iterator().next();
        if (player == null) {
            CompletableFuture<Object> future = new CompletableFuture<>();
            future.completeExceptionally(new RuntimeException("没有在线玩家可以发送请求"));
            return future;
        }
        return sendRequest(player, "getPartyMemberDetailListByName", playerName);
    }

    /**
     * 检查两个玩家是否是好友
     */
    public CompletableFuture<Object> areFriends(Player player, String targetName) {
        return sendRequest(player, "areFriends", targetName);
    }

    /**
     * 检查玩家是否在组队中
     */
    public CompletableFuture<Object> isInParty(Player player) {
        return sendRequest(player, "isInParty");
    }

    /**
     * 检查玩家是否是组队队长
     */
    public CompletableFuture<Object> isPartyLeader(Player player) {
        return sendRequest(player, "isPartyLeader");
    }

    /**
     * 发送自定义请求到Bungee
     * 用于发送命令执行请求等自定义操作
     */
    public CompletableFuture<Object> sendCustomRequest(Player player, String requestType, String... args) {
        return sendRequest(player, requestType, args);
    }
    
    /**
     * 通过任意在线玩家发送自定义请求到Bungee
     * 当队长已经离线或切换服务器时使用
     * 不需要指定发送者Player对象
     */
    public CompletableFuture<Object> sendCustomRequestByName(String requestType, String... args) {
        // 获取任意一个在线玩家作为消息发送者
        Player sender = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (sender == null) {
            CompletableFuture<Object> future = new CompletableFuture<>();
            future.completeExceptionally(new RuntimeException("没有在线玩家可以发送请求"));
            return future;
        }
        
        // 使用这个玩家发送请求
        return sendRequest(sender, requestType, args);
    }
    
    /**
     * 通过UUID获取组队成员详细信息列表
     */
    public CompletableFuture<Object> getPartyMemberDetailListByUUID(String uuid) {
        // 获取任意一个在线玩家作为消息发送者
        Player sender = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (sender == null) {
            CompletableFuture<Object> future = new CompletableFuture<>();
            future.completeExceptionally(new RuntimeException("没有在线玩家可以发送请求"));
            return future;
        }
        
        // 使用这个玩家发送请求
        return sendRequest(sender, "getPartyMemberDetailListByUUID", uuid);
    }
}