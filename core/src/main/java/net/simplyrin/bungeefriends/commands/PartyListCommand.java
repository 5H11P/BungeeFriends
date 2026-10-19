package net.simplyrin.bungeefriends.commands;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.plugin.Command;
import net.simplyrin.bungeefriends.Main;
import net.simplyrin.bungeefriends.tools.ThreadPool;

public class PartyListCommand extends Command {
	private final Main plugin;

	public PartyListCommand(Main plugin) {
		super("pl");
		this.plugin = plugin;
	}

	@Override
	public void execute(CommandSender sender, String[] args) {
		ThreadPool.run(() -> plugin.getPartyCommand().async(sender, new String[] {"list"}));
	}
}
