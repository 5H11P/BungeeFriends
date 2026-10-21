package net.simplyrin.bungeefriends.commands;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BlockCommandTest {

	@Test
	public void unknownArgumentIsFollowedByTheHelpText() {
		assertEquals("未知参数\"bogus\"！\n/block help - 显示此帮助消息。",
				BlockCommand.buildUnknownArgumentMessage("未知参数\"bogus\"！", "/block help - 显示此帮助消息。"));
	}
}
