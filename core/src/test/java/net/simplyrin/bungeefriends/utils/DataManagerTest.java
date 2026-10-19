package net.simplyrin.bungeefriends.utils;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.UUID;

import net.md_5.bungee.config.Configuration;
import org.junit.Test;

public class DataManagerTest {
	@Test
	public void existingPartySurvivesYamlReload() throws Exception {
		File directory = Files.createTempDirectory(new File("core/target").toPath(), "bungeefriends-yaml-").toFile();
		File file = new File(directory, "data.yml");
		String leader = UUID.randomUUID().toString();
		String member = UUID.randomUUID().toString();
		Configuration data = new Configuration();
		data.set("Parties." + leader + ".Currently-Joined-Party", leader);
		data.set("Parties." + leader + ".Party-List", Arrays.asList(member));
		data.set("Parties." + member + ".Currently-Joined-Party", leader);
		DataManager.persist(data, file);
		Configuration restarted = DataManager.loadData(file);
		assertEquals(leader, restarted.getString("Parties." + member + ".Currently-Joined-Party"));
		assertEquals(Arrays.asList(member), restarted.getStringList("Parties." + leader + ".Party-List"));
		DataManager.persist(restarted, file);
		assertEquals(Arrays.asList(member), DataManager.loadData(file).getStringList("Parties." + leader + ".Party-List"));
	}

	@Test
	public void friendAndPartyDetailsSurviveYamlReload() throws Exception {
		File directory = Files.createTempDirectory(new File("core/target").toPath(), "bungeefriends-yaml-").toFile();
		File file = new File(directory, "data.yml");
		String owner = UUID.randomUUID().toString();
		String target = UUID.randomUUID().toString();
		Configuration data = new Configuration();
		data.set("Player." + owner + ".Requests", Arrays.asList(target));
		data.set("Player." + owner + ".RequestSentAt." + target, "123456789");
		data.set("Player." + owner + ".FriendPrefix." + target, "&aFriend ");
		data.set("Player." + owner + ".FriendSince." + target, "123456700");
		data.set("Parties." + owner + ".Requests", Arrays.asList(target));
		data.set("Parties." + owner + ".RequestSentAt." + target, "123456789");
		DataManager.persist(data, file);
		Configuration restarted = DataManager.loadData(file);
		assertEquals(Arrays.asList(target), restarted.getStringList("Player." + owner + ".Requests"));
		assertEquals("123456789", restarted.getString("Player." + owner + ".RequestSentAt." + target));
		assertEquals("&aFriend ", restarted.getString("Player." + owner + ".FriendPrefix." + target));
		assertEquals("123456700", restarted.getString("Player." + owner + ".FriendSince." + target));
		assertEquals(Arrays.asList(target), restarted.getStringList("Parties." + owner + ".Requests"));
		assertEquals("123456789", restarted.getString("Parties." + owner + ".RequestSentAt." + target));
	}
}
