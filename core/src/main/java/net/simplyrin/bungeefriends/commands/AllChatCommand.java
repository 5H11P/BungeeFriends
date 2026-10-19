package net.simplyrin.bungeefriends.commands;

import java.util.Arrays;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;
import net.simplyrin.bungeefriends.Main;
import net.simplyrin.bungeefriends.messages.Messages;
import net.simplyrin.bungeefriends.tools.ThreadPool;

public class AllChatCommand extends Command {
	private final Main plugin;

	public AllChatCommand(Main plugin, String name) {
		super(name);
		this.plugin = plugin;
	}

	@Override
	public void execute(CommandSender sender, String[] args) {
		ThreadPool.run(() -> {
			if (!(sender instanceof ProxiedPlayer)) {
				plugin.info(Messages.INGAME_ONLY);
				return;
			}
			ProxiedPlayer player = (ProxiedPlayer) sender;
			ChatCommand chat = plugin.getChatCommand();
			if (chat == null) return;
			if (args.length == 0 || (args.length == 1 && args[0].equalsIgnoreCase("ac"))) {
				chat.getChannels().remove(player.getUniqueId());
				plugin.info(player, plugin.getLanguageManager().getPlayer(player).getString("Chat.Update")
						.replace("%CHANNEL%", plugin.getLanguageManager().getPlayer(player).getString("Chat.Channels.All")));
				return;
			}
			chat.sendGlobal(player, String.join(" ", Arrays.asList(args)));
		});
	}
}
