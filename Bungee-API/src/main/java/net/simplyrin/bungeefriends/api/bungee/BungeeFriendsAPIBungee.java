package net.simplyrin.bungeefriends.api.bungee;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;
import net.simplyrin.bungeefriends.Main;
import net.simplyrin.bungeefriends.api.BungeeFriendsAPI;
import net.simplyrin.bungeefriends.api.BungeeFriendsAPI.FriendInfo;
import net.simplyrin.bungeefriends.api.BungeeFriendsAPI.PartyMemberInfo;
import net.simplyrin.bungeefriends.utils.FriendManager.FriendUtils;
import net.simplyrin.bungeefriends.utils.PartyManager.PartyUtils;

import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.UUID;

/**
 * BungeeFriends API Bungee端插件
 * 用于处理来自Spigot服务器的API请求
 */
public class BungeeFriendsAPIBungee extends Plugin implements Listener {

    private static BungeeFriendsAPIBungee instance;
    private BungeeFriendsAPI api;
    private Gson gson;
    
    private static final String CHANNEL = "BungeeFriends";
    
    @Override
    public void onEnable() {
        instance = this;
        this.api = BungeeFriendsAPI.getInstance();
        this.gson = new Gson();
        
        // 注册插件消息通道
        getProxy().registerChannel("BungeeFriends");
        
        // 注册事件监听器
        getProxy().getPluginManager().registerListener(this, this);
        
        getLogger().info("BungeeFriendsAPI-Bungee 已启用!");
    }

    @Override
    public void onDisable() {
        // 注销插件消息通道
        getProxy().unregisterChannel("BungeeFriends");
        
        getLogger().info("BungeeFriendsAPI-Bungee 已禁用!");
    }

    @EventHandler
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getTag().equals("BungeeFriends")) {
            return;
        }
        
        // 确保消息来自服务器
        if (!(event.getSender() instanceof Server)) {
            return;
        }
        
        // 获取接收者（玩家）
        if (!(event.getReceiver() instanceof ProxiedPlayer)) {
            return;
        }
        
        ProxiedPlayer player = (ProxiedPlayer) event.getReceiver();
        
        // 处理消息
        try {
            // 读取JSON请求
            String jsonRequest = new String(event.getData(), "UTF-8");
            Map<String, Object> request = gson.fromJson(jsonRequest, new TypeToken<Map<String, Object>>(){}.getType());
            
            // 获取请求类型和请求ID
            String requestType = (String) request.get("requestType");
            String requestId = (String) request.get("requestId");
            
            // 获取参数
            String[] args = new String[0];
            if (request.containsKey("args")) {
                @SuppressWarnings("unchecked")
                List<String> argsList = (List<String>) request.get("args");
                args = argsList.toArray(new String[0]);
            }
            
            // 处理请求并发送响应
            handleRequest(player, requestType, requestId, args);
            
        } catch (Exception e) {
            getLogger().severe("处理插件消息时出错: " + e.getMessage());
            e.printStackTrace();
        }
        
        // 阻止消息继续传递
        event.setCancelled(true);
    }

    /**
     * 处理来自Spigot服务器的API请求
     */
    private void handleRequest(ProxiedPlayer player, String requestType, String requestId, String[] args) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("requestId", requestId);
            response.put("success", true);
            
            switch (requestType) {
                case "getFriendList": {
                    // 获取玩家的好友列表
                    FriendUtils friendUtils = api.getFriendUtils(player);
                    List<String> friends = friendUtils.getFriends();
                    response.put("data", friends);
                    break;
                }
                
                case "getFriendDetailList": {
                    // 获取玩家的好友详细信息列表
                    List<FriendInfo> friendDetails = api.getFriendDetailList(player);
                    response.put("data", friendDetails);
                    break;
                }
                
                case "getFriendDetailListByName": {
                    // 通过玩家名称获取好友详细信息列表
                    if (args.length > 0) {
                        String targetName = args[0];
                        List<FriendInfo> friendDetails = api.getFriendDetailListByName(targetName);
                        response.put("data", friendDetails);
                    } else {
                        response.put("success", false);
                        response.put("error", "缺少目标玩家名称参数");
                    }
                    break;
                }
                
                case "getPartyMembers": {
                    // 获取玩家的组队成员列表
                    PartyUtils partyUtils = api.getPartyUtils(player);
                    if (partyUtils.isJoinedParty()) {
                        try {
                            PartyUtils leader = partyUtils.getPartyLeader();
                            List<String> members = leader.getParties();
                            response.put("data", members);
                        } catch (Exception e) {
                            response.put("success", false);
                            response.put("error", e.getMessage());
                        }
                    } else {
                        response.put("success", false);
                        response.put("error", "玩家不在任何组队中");
                    }
                    break;
                }
                
                case "getPartyMemberDetailList": {
                    // 获取玩家的组队成员详细信息列表
                    List<PartyMemberInfo> partyDetails = api.getPartyMemberDetailList(player);
                    response.put("data", partyDetails);
                    break;
                }
                
                case "getPartyMemberDetailListByName": {
                    // 通过玩家名称获取组队成员详细信息列表
                    if (args.length > 0) {
                        String targetName = args[0];
                        List<PartyMemberInfo> partyDetails = api.getPartyMemberDetailListByName(targetName);
                        response.put("data", partyDetails);
                    } else {
                        response.put("success", false);
                        response.put("error", "缺少目标玩家名称参数");
                    }
                    break;
                }
                
                case "getPartyMemberDetailListByUUID": {
                    // 通过玩家UUID获取组队成员详细信息列表
                    if (args.length > 0) {
                        String targetUUID = args[0];
                        try {
                            UUID uuid = UUID.fromString(targetUUID);
                            List<PartyMemberInfo> partyDetails = api.getPartyMemberDetailList(uuid);
                            response.put("data", partyDetails);
                        } catch (IllegalArgumentException e) {
                            response.put("success", false);
                            response.put("error", "无效的UUID格式: " + targetUUID);
                        }
                    } else {
                        response.put("success", false);
                        response.put("error", "缺少目标玩家UUID参数");
                    }
                    break;
                }
                
                case "areFriends": {
                    // 检查两个玩家是否是好友
                    if (args.length > 0) {
                        String targetName = args[0];
                        // 尝试获取在线玩家
                        ProxiedPlayer target = getProxy().getPlayer(targetName);
                        if (target != null) {
                            // 目标玩家在线，直接使用玩家对象
                            boolean areFriends = api.areFriends(player, target);
                            response.put("data", areFriends);
                        } else {
                            // 目标玩家不在线，尝试通过名称获取UUID
                            String targetUUID = null;
                            // 首先检查是否有缓存的UUID
                            try {
                                targetUUID = net.simplyrin.bungeefriends.Main.getInstance().getString("Name." + targetName.toLowerCase());
                            } catch (Exception e) {
                                // 忽略异常，继续尝试其他方法
                            }
                            
                            if (targetUUID != null && !targetUUID.isEmpty()) {
                                // 找到了UUID，使用UUID检查好友关系
                                boolean areFriends = api.areFriends(player.getUniqueId(), UUID.fromString(targetUUID));
                                response.put("data", areFriends);
                            } else {
                                // 无法找到玩家UUID
                                response.put("success", false);
                                response.put("error", "无法找到目标玩家的UUID");
                            }
                        }
                    } else {
                        response.put("success", false);
                        response.put("error", "缺少目标玩家名称参数");
                    }
                    break;
                }
                
                case "isInParty": {
                    // 检查玩家是否在组队中
                    boolean isInParty = api.isInParty(player);
                    response.put("data", isInParty);
                    break;
                }
                
                case "isPartyLeader": {
                    // 检查玩家是否是组队队长
                    boolean isPartyLeader = api.isPartyLeader(player);
                    response.put("data", isPartyLeader);
                    break;
                }
                
                case "executeCommand": {
                    // 处理命令执行请求
                    if (args.length >= 2) {
                        String targetPlayerName = args[0];
                        String command = args[1];
                        
                        // 获取目标玩家
                        ProxiedPlayer targetPlayer = getProxy().getPlayer(targetPlayerName);
                        if (targetPlayer != null && targetPlayer.isConnected()) {
                            // 修改：不再检查发送请求的玩家是否是队长，只检查目标玩家是否在队伍中
                            // 这样即使队长已经切换服务器，命令仍然可以执行
                            PartyUtils targetPartyUtils = api.getPartyUtils(targetPlayer);
                            if (targetPartyUtils.isJoinedParty()) {
                                // 目标玩家是队员，让他执行命令
                                targetPlayer.chat("/" + command);
                                response.put("data", true);
                                getLogger().info("队员 " + targetPlayerName + " 执行命令: " + command + "（由队长请求）");
                            } else {
                                response.put("success", false);
                                response.put("error", "目标玩家不在任何组队中");
                            }
                        } else {
                            response.put("success", false);
                            response.put("error", "目标玩家不在线");
                        }
                    } else {
                        response.put("success", false);
                        response.put("error", "缺少目标玩家名称或命令参数");
                    }
                    break;
                }
                
                default:
                    response.put("success", false);
                    response.put("error", "未知的请求类型: " + requestType);
                    break;
            }
            
            // 发送响应
            sendResponse(player, response);
            
        } catch (Exception e) {
            getLogger().severe("处理请求时出错: " + e.getMessage());
            e.printStackTrace();
            
            // 发送错误响应
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("requestId", requestId);
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            sendResponse(player, errorResponse);
        }
    }

    /**
     * 发送响应到Spigot服务器
     */
    private void sendResponse(ProxiedPlayer player, Map<String, Object> response) {
        try {
            // 转换为JSON
            String responseJson = gson.toJson(response);
            
            // 检查玩家是否仍然在线并且有服务器连接
            if (player != null && player.isConnected() && player.getServer() != null) {
                // 发送响应
                player.getServer().sendData("BungeeFriends", responseJson.getBytes("UTF-8"));
            } else {
                // 玩家已离线或切换服务器，记录日志
                getLogger().info("无法发送响应：玩家 " + (player != null ? player.getName() : "未知") + " 已离线或切换服务器");
            }
            
        } catch (Exception e) {
            getLogger().severe("发送响应时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 获取插件实例
     */
    public static BungeeFriendsAPIBungee getInstance() {
        return instance;
    }
}