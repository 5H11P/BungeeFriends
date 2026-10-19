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
			for (String key : new String[] {"Command", "add", "help", "list", "remove", "removeall"})
				plugin.info(player, language.getString("Block." + key));
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
				plugin.info(player, language.getString("Ignore.Havent.One"));
				return;
			}
			int total = (blocked.size() + 7) / 8;
			page = Math.max(1, Math.min(page, total));
			player.sendMessage(PaginationMessage.build(language, language.getString("Block.List-Page"), "/block list ", page, total));
			for (int index = (page - 1) * 8; index < Math.min(page * 8, blocked.size()); index++) {
				FriendUtils target = plugin.getFriendManager().getPlayer(UUID.fromString(blocked.get(index)));
				plugin.info(player, language.getString("Block.List-Format")
						.replace("%index", String.valueOf(index + 1))
						.replace("%targetDisplayName", target.getDisplayName()));
			}
			return;
		}
		if ("removeall".equals(action)) {
			plugin.set("Player." + player.getUniqueId() + ".IgnoreList", new ArrayList<String>());
			plugin.info(player, language.getString("Block.RemoveAll-Done"));
			return;
		}
		if (!"add".equals(action) && !"remove".equals(action)) {
			plugin.info(player, language.getString("Block.Command"));
			return;
		}
		if (args.length < 2) {
			plugin.info(player, language.getString("Block." + action));
			return;
		}
		UUID targetId = plugin.getPlayerManager().getPlayerUniqueId(args[1]);
		if (targetId == null) {
			plugin.info(player, language.getString("Cant-Find").replace("%name", args[1]));
			return;
		}
		if (targetId.equals(player.getUniqueId())) {
			plugin.info(player, language.getString("Block.Self"));
			return;
		}
		FriendUtils target = plugin.getFriendManager().getPlayer(targetId);
		try {
			if ("add".equals(action)) friends.addIgnore(targetId);
			else friends.removeIgnore(targetId);
			plugin.info(player, language.getString("Ignore." + ("add".equals(action) ? "Added" : "Removed"))
					.replace("%targetDisplayName", target.getDisplayName()));
		} catch (AlreadyAddedException e) {
			plugin.info(player, language.getString("Ignore.AlreadyAdded"));
		} catch (NotAddedException e) {
			plugin.info(player, language.getString("Ignore.NotAdded"));
		}
	}

	@Override
	public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
		List<String> matches = new ArrayList<>();
		if (plugin.getBoolean("Plugin.Disable-TabComplete") || args.length != 1) return matches;
		for (String choice : new String[] {"add", "help", "list", "remove", "removeall"})
			if (choice.startsWith(args[0].toLowerCase(Locale.ROOT))) matches.add(choice);
		return matches;
	}
}
