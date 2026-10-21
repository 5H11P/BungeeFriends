package net.simplyrin.bungeefriends.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.TabExecutor;
import net.simplyrin.bungeefriends.Main;
import net.simplyrin.bungeefriends.exceptions.AlreadyAddedException;
import net.simplyrin.bungeefriends.exceptions.NotAddedException;
import net.simplyrin.bungeefriends.messages.MessageFrame;
import net.simplyrin.bungeefriends.messages.Messages;
import net.simplyrin.bungeefriends.tools.ThreadPool;
import net.simplyrin.bungeefriends.utils.FriendManager.FriendUtils;
import net.simplyrin.bungeefriends.utils.LanguageManager.LanguageUtils;
import net.simplyrin.bungeefriends.utils.PaginationMessage;

public class BlockCommand extends Command implements TabExecutor {
	private final Main plugin;

	public BlockCommand(Main plugin, String name) {
		super(name);
		this.plugin = plugin;
	}

	@Override
	public void execute(CommandSender sender, String[] args) {
		if (!(sender instanceof ProxiedPlayer)) {
			sender.sendMessage(Messages.INGAME_ONLY);
			return;
		}
		ThreadPool.run(() -> run((ProxiedPlayer) sender, args));
	}

	private void run(ProxiedPlayer player, String[] args) {
		LanguageUtils language = plugin.getLanguageManager().getPlayer(player);
		FriendUtils friends = plugin.getFriendManager().getPlayer(player);
		if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
			plugin.infoFramed(player, buildHelp(language), MessageFrame.MessageId.BLOCK_HELP);
			return;
		}
		String action = args[0].toLowerCase(Locale.ROOT);
		if ("list".equals(action)) {
			int page = 1;
			if (args.length > 1) {
				try { page = Integer.parseInt(args[1]); } catch (NumberFormatException ignored) { }
			}
			List<String> blocked = friends.getIgnoreList();
			if (blocked.isEmpty()) {
				plugin.infoFramed(player, language.getString("Ignore.Empty"), MessageFrame.MessageId.BLOCK_EMPTY);
				return;
			}
			int total = (blocked.size() + 7) / 8;
			page = Math.max(1, Math.min(page, total));
			plugin.infoFrame(player, MessageFrame.MessageId.BLOCK_LIST_PAGE);
			player.sendMessage(PaginationMessage.build(language, language.getString("Block.List-Page"), "/block list ", page, total));
			for (int index = (page - 1) * 8; index < Math.min(page * 8, blocked.size()); index++) {
				FriendUtils target = plugin.getFriendManager().getPlayer(UUID.fromString(blocked.get(index)));
				plugin.info(player, language.getString("Block.List-Format")
						.replace("%index", String.valueOf(index + 1))
						.replace("%targetDisplayName", target.getDisplayName()));
			}
			plugin.infoFrame(player, MessageFrame.MessageId.BLOCK_LIST_PAGE);
			return;
		}
		if ("removeall".equals(action)) {
			synchronized (plugin.getDataManager()) {
				plugin.set("Player." + player.getUniqueId() + ".IgnoreList", new ArrayList<String>());
				if (!plugin.isEnabledMySQL()) plugin.getDataManager().save();
			}
			plugin.infoFramed(player, language.getString("Block.RemoveAll-Done"), MessageFrame.MessageId.BLOCK_REMOVE_ALL_DONE);
			return;
		}
		if (!"add".equals(action) && !"remove".equals(action)) {
			String unknownArgument = language.getString("Block.Unknown-Argument").replace("%argument%", args[0]);
			plugin.infoFramed(player, buildUnknownArgumentMessage(unknownArgument, buildHelp(language)), MessageFrame.MessageId.BLOCK_COMMAND);
			return;
		}
		if (args.length < 2) {
			plugin.infoFramed(player, language.getString("Block." + ("add".equals(action) ? "Add" : "Remove")),
					"add".equals(action) ? MessageFrame.MessageId.BLOCK_ADD : MessageFrame.MessageId.BLOCK_REMOVE);
			return;
		}
		UUID targetId = plugin.getPlayerManager().getPlayerUniqueId(args[1]);
		if (targetId == null) {
			plugin.infoFramed(player, language.getString("Cant-Find").replace("%name", args[1]), MessageFrame.MessageId.PLAYER_NOT_FOUND);
			return;
		}
		if (targetId.equals(player.getUniqueId())) {
			plugin.infoFramed(player, language.getString("Block.Self"), MessageFrame.MessageId.BLOCK_SELF);
			return;
		}
		FriendUtils target = plugin.getFriendManager().getPlayer(targetId);
		try {
			if ("add".equals(action)) friends.addIgnore(targetId);
			else friends.removeIgnore(targetId);
			plugin.infoFramed(player, language.getString("Ignore." + ("add".equals(action) ? "Added" : "Removed"))
					.replace("%targetDisplayName", target.getDisplayName()),
					"add".equals(action) ? MessageFrame.MessageId.IGNORE_ADDED : MessageFrame.MessageId.IGNORE_NOT_ADDED);
		} catch (AlreadyAddedException e) {
			plugin.infoFramed(player, language.getString("Ignore.AlreadyAdded"), MessageFrame.MessageId.IGNORE_ALREADY_ADDED);
		} catch (NotAddedException e) {
			plugin.infoFramed(player, language.getString("Ignore.NotAdded"), MessageFrame.MessageId.IGNORE_NOT_ADDED);
		}
	}

	static String buildHelp(LanguageUtils language) {
		StringBuilder help = new StringBuilder();
		for (String key : new String[] {"Command", "Add", "Help", "List", "Remove", "Remove-All"}) {
			if (help.length() > 0) help.append('\n');
			help.append(language.getString("Block." + key));
		}
		return help.toString();
	}

	static String buildUnknownArgumentMessage(String unknownArgument, String help) {
		return unknownArgument + '\n' + help;
	}

	@Override
	public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
		List<String> matches = new ArrayList<>();
		if (plugin.getBoolean("Plugin.Disable-Tab-Complete") || args == null || args.length == 0) return matches;
		String input = args[args.length - 1].toLowerCase(Locale.ROOT);
		if (args.length == 1) {
			for (String choice : new String[] {"add", "help", "list", "remove", "removeall"})
				if (choice.startsWith(input)) matches.add(choice);
		} else if (args.length == 2 && (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("remove"))
				&& sender instanceof ProxiedPlayer && !input.isEmpty() && ((ProxiedPlayer) sender).getServer() != null) {
			String server = ((ProxiedPlayer) sender).getServer().getInfo().getName();
			for (ProxiedPlayer player : plugin.getProxy().getPlayers())
				if (player.getServer() != null && player.getServer().getInfo().getName().equals(server)
						&& player.getName().toLowerCase(Locale.ROOT).startsWith(input)) matches.add(player.getName());
		}
		return matches;
	}
}
