package net.simplyrin.bungeefriends.commands;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.google.common.base.Charsets;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.config.Configuration;
import net.simplyrin.bungeefriends.Main;
import net.simplyrin.bungeefriends.messages.MessageFrame;
import net.simplyrin.bungeefriends.messages.Messages;
import net.simplyrin.bungeefriends.tools.Config;
import net.simplyrin.bungeefriends.tools.ThreadPool;
import net.simplyrin.bungeefriends.utils.FriendManager.FriendUtils;
import net.simplyrin.bungeefriends.utils.LanguageManager.LanguageUtils;

/**
 * Created by SimplyRin on 2021/02/12.
 *
 * author: SimplyRin
 * license: LGPL v3
 * copyright: Copyright (c) 2021 SimplyRin
 */
public class FriendConsoleCommand extends Command {

	private Main plugin;

	public FriendConsoleCommand(Main plugin, String cmd) {
		super(cmd, null);
		this.plugin = plugin;
	}

	@Override
	public void execute(CommandSender sender, String[] args) {
		ThreadPool.run(() -> this.async(sender, args));
	}

	public void async(CommandSender sender, String[] args) {
		if (sender instanceof ProxiedPlayer) {
			this.plugin.info(Messages.CONSOLE_ONLY);
			return;
		}

		LanguageUtils langUtils = this.plugin.getLanguageManager().getPlayer(this.plugin.getConsoleUniqueId());

		if (args.length > 0) {
			if (args[0].equalsIgnoreCase("prefix")) {
				if (args.length > 1) {
					UUID target = this.plugin.getPlayerManager().getPlayerUniqueId(args[1]);
					if (target == null) {
						this.plugin.infoFramedConsole(langUtils.getString("Cant-Find").replace("%name", args[1]), MessageFrame.MessageId.PLAYER_NOT_FOUND);
						return;
					}
					if (args.length > 2) {
						FriendUtils targetFriends = this.plugin.getFriendManager().getPlayer(target);
						String prefix = "";
						for (int i = 2; i < args.length; i++) {
							prefix = prefix + args[i] + " ";
						}

						if (prefix.trim().length() <= 2) {
							prefix = prefix.trim();
						}
						String displayName = targetFriends.getDisplayName();
						targetFriends.setPrefix(prefix);

						this.plugin.info(langUtils.getString("Prefix.To").replace("%targetDisplayName", displayName)
								.replace("%prefix", ChatColor.translateAlternateColorCodes('&', prefix.trim())));
						return;
					}
				}
				this.plugin.infoFramedConsole(langUtils.getString("Prefix.Usage").replace("/friend", "/friendconsole"), MessageFrame.MessageId.CONSOLE_PREFIX);
				return;
			}

			if (args[0].equalsIgnoreCase("reload")) {
				if (args.length > 1) {
					if (args[1].equalsIgnoreCase("all")) {
						this.plugin.getConfigManager().reload();
						this.plugin.info(langUtils.getString("Reload.Config"));

						this.plugin.reloadLanguageManager();
						this.plugin.info(langUtils.getString("Reload.Lang"));
						return;
					}
					if (args[1].equalsIgnoreCase("config")) {
						this.plugin.getConfigManager().reload();
						this.plugin.info(langUtils.getString("Reload.Config"));
						return;
					}
					if (args[1].equalsIgnoreCase("lang")) {
						this.plugin.reloadLanguageManager();
						this.plugin.info(langUtils.getString("Reload.Lang"));
						return;
					}
				}

				this.plugin.info(langUtils.getString("Reload.Usage"));
				return;
			}

			if (args[0].equalsIgnoreCase("lang") || args[0].equalsIgnoreCase("language")) {
				File folder = this.plugin.getDataFolder();
				if (!folder.exists()) {
					folder.mkdir();
				}

				File languageFolder = new File(folder, "Language");
				if (!languageFolder.exists()) {
					languageFolder.mkdir();
				}

				List<String> availableList = new ArrayList<>();
				String available = "";
				File[] languages = languageFolder.listFiles();
				for (File languageFile : languages) {
					Configuration langConfig = Config.getConfig(languageFile, Charsets.UTF_8);
					if (langConfig.getString("Language").length() > 1) {
						availableList.add(languageFile.getName().toLowerCase().replace(".yml", ""));
						available += langConfig.getString("Language") + ", ";
					}
				}

				if (args.length > 1) {
					if (availableList.contains(args[1].toLowerCase())) {
						langUtils.setLanguage(args[1].toLowerCase());
						this.plugin.infoFramedConsole(langUtils.getString("Lang.Update").replace("%lang", langUtils.getLanguage().replace("/friend", "/friendconsole")),
								MessageFrame.MessageId.CONSOLE_LANGUAGE);
						return;
					}
				}

				this.plugin.infoFrameConsole(MessageFrame.MessageId.CONSOLE_LANGUAGE);
				this.plugin.info(langUtils.getString("Lang.Usage").replace("/friend", "/friendconsole"));
				this.plugin.info(langUtils.getString("Lang.Available").replace("/friend", "/friendconsole") + " <" + available.substring(0, available.length() - 1) + ">");
				this.plugin.infoFrameConsole(MessageFrame.MessageId.CONSOLE_LANGUAGE);
				return;
			}
		}

		this.plugin.infoFrameConsole(MessageFrame.MessageId.CONSOLE_HELP);
		this.plugin.info(langUtils.getString("Help.Lang").replace("/friend", "/friendconsole"));
		this.plugin.info(langUtils.getString("Help.Prefix").replace("/friend", "/friendconsole"));
		this.plugin.info(langUtils.getString("Help.Reload").replace("/friend", "/friendconsole"));
		this.plugin.infoFrameConsole(MessageFrame.MessageId.CONSOLE_HELP);
		return;
	}

}
