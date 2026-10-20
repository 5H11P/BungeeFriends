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

	static boolean canDelegate(PartyCommand partyCommand) {
		return partyCommand != null;
	}

	@Override
	public void execute(CommandSender sender, String[] args) {
		PartyCommand partyCommand = plugin.getPartyCommand();
		if (!canDelegate(partyCommand)) {
			plugin.getLogger().warning("Ignoring /pl because party support is not initialized");
			return;
		}
		ThreadPool.run(() -> partyCommand.async(sender, new String[] {"list"}));
	}
}
