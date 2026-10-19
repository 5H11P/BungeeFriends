package net.simplyrin.bungeefriends.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import org.junit.Test;

public class MySQLTest {
	private static final String A = "00000000-0000-0000-0000-000000000001";
	private static final String B = "00000000-0000-0000-0000-000000000002";
	private static final String C = "00000000-0000-0000-0000-000000000003";

	private Connection open(String name) throws Exception {
		return DriverManager.getConnection("jdbc:h2:mem:" + name + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
	}

	@Test
	public void fieldsAndRelationsSurviveRestart() throws Exception {
		String database = "mapping_" + UUID.randomUUID().toString().replace("-", "");
		try (Connection db = open(database)) {
			MySQL.Editor editor = new MySQL(db).connect();
			assertEquals("", editor.get("Player." + A + ".Name"));
			assertEquals(Collections.emptyList(), editor.getList("Player." + A + ".Friends"));
			editor.set("Player." + A + ".Name", "O'Reilly");
			editor.set("Player." + A + ".Prefix", "null");
			assertEquals("null", editor.get("Player." + A + ".Prefix"));
			editor.set("Player." + A + ".Prefix", (String) null);
			editor.set("Player." + A + ".Toggle", "true");
			editor.set("Player." + A + ".Status", "Away");
			editor.set("Player." + A + ".Friends", Arrays.asList(B, C, B));
			editor.set("Player." + A + ".Requests", Collections.singletonList(C));
			editor.set("Parties." + A + ".Party-List", Collections.singletonList(B));
			editor.set("Parties." + A + ".Currently-Joined-Party", A);
			assertEquals(A, editor.get("Name.o'reilly"));
			assertEquals("true", editor.get("Player." + A + ".Toggle"));
		}
		try (Connection db = open(database)) {
			MySQL.Editor editor = new MySQL(db).connect();
			assertEquals("O'Reilly", editor.get("UUID." + A));
			assertEquals("", editor.get("Player." + A + ".Prefix"));
			assertEquals("Away", editor.get("Player." + A + ".Status"));
			assertEquals(Arrays.asList(B, C), editor.getList("Player." + A + ".Friends"));
			assertEquals(Collections.singletonList(C), editor.getList("Player." + A + ".Requests"));
			assertEquals(Collections.singletonList(B), editor.getList("Parties." + A + ".Party-List"));
			assertEquals(A, editor.get("Parties." + A + ".Currently-Joined-Party"));
		}
	}

	@Test
	public void disconnectedReadFailsExplicitly() throws Exception {
		Connection db = open("disconnect_" + UUID.randomUUID().toString().replace("-", ""));
		MySQL.Editor editor = new MySQL(db).connect();
		db.close();
		try {
			editor.get("Player." + A + ".Name");
			fail("Expected a disconnected read to fail");
		} catch (IllegalStateException expected) {
			assertTrue(expected.getCause() != null);
		}
	}

	@Test
	public void friendshipChangesBothSidesAndListReplacementRollsBack() throws Exception {
		try (Connection db = open("transaction_" + UUID.randomUUID().toString().replace("-", ""))) {
			MySQL.Editor editor = new MySQL(db).connect();
			editor.changeFriendship(UUID.fromString(A), UUID.fromString(B), true);
			editor.changeFriendship(UUID.fromString(A), UUID.fromString(B), true);
			assertTrue(Long.parseLong(editor.getFriendCreatedAt(UUID.fromString(A), UUID.fromString(B))) > 0);
			assertEquals(Collections.singletonList(B), editor.getList("Player." + A + ".Friends"));
			assertEquals(Collections.singletonList(A), editor.getList("Player." + B + ".Friends"));
			try (Statement st = db.createStatement()) {
				st.executeUpdate("ALTER TABLE bungeefriends_friends ADD CONSTRAINT reject_c CHECK (friend_uuid <> '" + C + "')");
			}
			try {
				editor.set("Player." + A + ".Friends", Collections.singletonList(C));
				fail("Expected a failed batch to propagate");
			} catch (IllegalStateException expected) {
				assertTrue(expected.getCause() != null);
			}
			assertEquals(Collections.singletonList(B), editor.getList("Player." + A + ".Friends"));
			editor.changeFriendship(UUID.fromString(A), UUID.fromString(B), false);
			assertEquals(Collections.emptyList(), editor.getList("Player." + A + ".Friends"));
			assertEquals(Collections.emptyList(), editor.getList("Player." + B + ".Friends"));
		}
	}

	@Test
	public void upgradesExistingRelationTable() throws Exception {
		try (Connection db = open("upgrade_" + UUID.randomUUID().toString().replace("-", ""))) {
			try (Statement st = db.createStatement()) {
				st.executeUpdate("CREATE TABLE bungeefriends_friends (owner_uuid varchar(36) NOT NULL, "
						+ "friend_uuid varchar(36) NOT NULL, PRIMARY KEY (owner_uuid, friend_uuid))");
				st.executeUpdate("INSERT INTO bungeefriends_friends VALUES ('" + A + "', '" + B + "')");
			}
			MySQL.Editor editor = new MySQL(db).connect();
			assertEquals(Collections.singletonList(B), editor.getList("Player." + A + ".Friends"));
			editor.set("Player." + A + ".Friends", Collections.singletonList(C));
			assertEquals(Collections.singletonList(C), editor.getList("Player." + A + ".Friends"));
		}
	}

	@Test
	public void upgradesExistingPlayerTableWithStatus() throws Exception {
		try (Connection db = open("status_upgrade_" + UUID.randomUUID().toString().replace("-", ""))) {
			try (Statement st = db.createStatement()) {
				st.executeUpdate("CREATE TABLE bungeefriends_data (uuid varchar(36) PRIMARY KEY, name varchar(64))");
				st.executeUpdate("INSERT INTO bungeefriends_data (uuid, name) VALUES ('" + A + "', 'Alice')");
			}
			MySQL.Editor editor = new MySQL(db).connect();
			assertEquals("Alice", editor.get("Player." + A + ".Name"));
			assertEquals("", editor.get("Player." + A + ".Status"));
			editor.set("Player." + A + ".Status", "Busy");
			assertEquals("Busy", editor.get("Player." + A + ".Status"));
		}
	}

	@Test
	public void partyUpdatesAreConsistentAndRollbackOnFailure() throws Exception {
		try (Connection db = open("party_" + UUID.randomUUID().toString().replace("-", ""))) {
			MySQL.Editor editor = new MySQL(db).connect();
			editor.updateParty(UUID.fromString(A), Arrays.asList(UUID.fromString(A), UUID.fromString(B)), Collections.emptyList());
			assertEquals(A, editor.get("Parties." + B + ".Currently-Joined-Party"));
			assertEquals(Collections.singletonList(B), editor.getList("Parties." + A + ".Party-List"));
			editor.updateParty(UUID.fromString(B), Arrays.asList(UUID.fromString(B), UUID.fromString(A)), Collections.emptyList());
			assertEquals(B, editor.get("Parties." + A + ".Currently-Joined-Party"));
			assertEquals(Collections.singletonList(A), editor.getList("Parties." + B + ".Party-List"));
			try (Statement st = db.createStatement()) {
				st.executeUpdate("ALTER TABLE bungeefriends_party_members ADD CONSTRAINT reject_c CHECK (member_uuid <> '" + C + "')");
			}
			try {
				editor.updateParty(UUID.fromString(B), Arrays.asList(UUID.fromString(B), UUID.fromString(A), UUID.fromString(C)), Collections.emptyList());
				fail("Expected failed party update");
			} catch (IllegalStateException expected) {
				assertTrue(expected.getCause() != null);
			}
			assertEquals(B, editor.get("Parties." + A + ".Currently-Joined-Party"));
			assertEquals("", editor.get("Parties." + C + ".Currently-Joined-Party"));
			assertEquals(Collections.singletonList(A), editor.getList("Parties." + B + ".Party-List"));
			editor.updateParty(UUID.fromString(B), Collections.singletonList(UUID.fromString(B)), Collections.singletonList(UUID.fromString(A)));
			assertEquals("NONE", editor.get("Parties." + A + ".Currently-Joined-Party"));
			assertEquals(Collections.emptyList(), editor.getList("Parties." + A + ".Party-List"));
		}
	}

	@Test
	public void requestTimesAndPrivatePrefixesSurviveRestart() throws Exception {
		String database = "friend_details_" + UUID.randomUUID().toString().replace("-", "");
		try (Connection db = open(database)) {
			MySQL.Editor editor = new MySQL(db).connect();
			editor.changeFriendRequest(UUID.fromString(A), UUID.fromString(B), true, 123456789L);
			editor.setFriendPrefix(UUID.fromString(A), UUID.fromString(B), "&cFriend ");
		}
		try (Connection db = open(database)) {
			MySQL.Editor editor = new MySQL(db).connect();
			assertEquals("123456789", editor.getFriendRequestCreatedAt(UUID.fromString(A), UUID.fromString(B)));
			assertEquals(Collections.singletonList(UUID.fromString(A)), editor.getFriendRequesters(UUID.fromString(B)));
			assertEquals("&cFriend ", editor.getFriendPrefix(UUID.fromString(A), UUID.fromString(B)));
			assertEquals("", editor.getFriendPrefix(UUID.fromString(B), UUID.fromString(A)));
			editor.changeFriendRequest(UUID.fromString(A), UUID.fromString(B), false, 0);
			editor.clearFriendPrefix(UUID.fromString(A), UUID.fromString(B));
			assertEquals("", editor.getFriendRequestCreatedAt(UUID.fromString(A), UUID.fromString(B)));
			assertEquals("", editor.getFriendPrefix(UUID.fromString(A), UUID.fromString(B)));
		}
	}

	@Test
	public void partyInvitationTimeSurvivesRestart() throws Exception {
		String database = "party_request_" + UUID.randomUUID().toString().replace("-", "");
		try (Connection db = open(database)) {
			new MySQL(db).connect().changePartyRequest(UUID.fromString(A), UUID.fromString(B), true);
		}
		try (Connection db = open(database)) {
			MySQL.Editor editor = new MySQL(db).connect();
			assertTrue(Long.parseLong(editor.getPartyRequestCreatedAt(UUID.fromString(A), UUID.fromString(B))) > 0);
			editor.changePartyRequest(UUID.fromString(A), UUID.fromString(B), false);
			assertEquals("", editor.getPartyRequestCreatedAt(UUID.fromString(A), UUID.fromString(B)));
		}
	}
}
