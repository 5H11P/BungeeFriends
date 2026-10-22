package net.simplyrin.bungeefriends.tools;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import net.simplyrin.bungeefriends.Main;

/** JDBC adapter for the new multi-table schema. Legacy data is not imported. */
public class MySQL {
	private static final String DATA = "bungeefriends_data";
	private final Main plugin;
	private final String username;
	private final String password;
	private String address;
	private String database;
	private String timezone;
	private boolean useSSL;
	private Connection connection;
	private Editor editor;

	public MySQL(Main plugin, String username, String password) {
		this.plugin = plugin;
		this.username = username;
		this.password = password;
	}

	MySQL(Connection db) throws SQLException {
		this.plugin = null;
		this.username = "";
		this.password = "";
		initialize(db);
		this.connection = db;
		this.editor = new Editor();
	}

	public MySQL setAddress(String value) { address = value; return this; }
	public MySQL setDatabase(String value) { database = value; return this; }
	public MySQL setTimezone(String value) { timezone = value; return this; }
	public MySQL setUseSSL(boolean value) { useSSL = value; return this; }

	public synchronized Editor connect() throws SQLException {
		if (connection != null && !connection.isClosed()) return editor;
		String url = "jdbc:mysql://" + address + "/" + database + "?useSSL=" + useSSL
				+ "&connectTimeout=5000&socketTimeout=10000&serverTimezone=" + timezone;
		Connection opened = DriverManager.getConnection(url, username, password);
		try {
			initialize(opened);
		} catch (SQLException e) {
			opened.close();
			throw e;
		}
		connection = opened;
		if (editor == null) editor = new Editor();
		return editor;
	}

	public synchronized void disconnect() throws SQLException {
		Connection closing = connection;
		connection = null;
		if (closing != null) closing.close();
	}

	private void initialize(Connection db) throws SQLException {
		try (Statement st = db.createStatement()) {
			st.executeUpdate("CREATE TABLE IF NOT EXISTS " + DATA + " (uuid varchar(36) PRIMARY KEY, "
					+ "name varchar(64), language varchar(32), prefix varchar(255), custom_prefix varchar(255), status varchar(32), "
					+ "toggle boolean, friends mediumtext, requests mediumtext, ignorelist mediumtext, "
					+ "last_online varchar(32), party_name varchar(64), party_current varchar(36), "
					+ "party_list mediumtext, party_requests mediumtext, party_toggle boolean, "
					+ "party_allinvite boolean, party_mute boolean, party_private boolean, "
					+ "updated_at timestamp NULL DEFAULT CURRENT_TIMESTAMP)");
			createRelation(st, "bungeefriends_friends", "friend_uuid");
			createRelation(st, "bungeefriends_ignore", "ignored_uuid");
			createRelation(st, "bungeefriends_friend_requests", "target_uuid");
			createRelation(st, "bungeefriends_party_members", "member_uuid");
			createRelation(st, "bungeefriends_party_moderators", "moderator_uuid");
			createRelation(st, "bungeefriends_party_requests", "invited_uuid");
			createRelation(st, "bungeefriends_best", "best_uuid");
			st.executeUpdate("CREATE TABLE IF NOT EXISTS bungeefriends_friend_prefix (viewer_uuid varchar(36) NOT NULL, "
					+ "target_uuid varchar(36) NOT NULL, custom_prefix varchar(255) NOT NULL, "
					+ "created_at timestamp NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (viewer_uuid, target_uuid))");
			st.executeUpdate("CREATE TABLE IF NOT EXISTS bungeefriends_friend_nickname (viewer_uuid varchar(36) NOT NULL, "
					+ "target_uuid varchar(36) NOT NULL, nickname varchar(255) NOT NULL, "
					+ "created_at timestamp NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (viewer_uuid, target_uuid))");
		}
		ensureIndex(db, DATA, "idx_bungeefriends_data_name", "name");
		ensureColumn(db, DATA, "status", "varchar(32)");
		ensureColumn(db, DATA, "friend_notifications", "boolean NOT NULL DEFAULT TRUE");
		for (String[] relation : RELATIONS) {
			ensureColumn(db, relation[0], "position_index", "int NOT NULL DEFAULT 0");
			ensureIndex(db, relation[0], "idx_" + relation[0] + "_target", relation[1]);
		}
		ensureColumn(db, "bungeefriends_friend_requests", "created_at", "bigint NULL");
		ensureColumn(db, "bungeefriends_friends", "created_at", "timestamp NULL DEFAULT CURRENT_TIMESTAMP");
		ensureColumn(db, "bungeefriends_party_requests", "created_at", "timestamp NULL DEFAULT CURRENT_TIMESTAMP");
		ensureIndex(db, "bungeefriends_friend_prefix", "idx_bungeefriends_friend_prefix_target", "target_uuid");
	}

	private static final String[][] RELATIONS = {
		{"bungeefriends_friends", "friend_uuid"}, {"bungeefriends_ignore", "ignored_uuid"},
		{"bungeefriends_friend_requests", "target_uuid"}, {"bungeefriends_party_members", "member_uuid"},
		{"bungeefriends_party_moderators", "moderator_uuid"},
		{"bungeefriends_party_requests", "invited_uuid"}, {"bungeefriends_best", "best_uuid"}
	};

	private void createRelation(Statement st, String table, String target) throws SQLException {
		String createdAt = "bungeefriends_friend_requests".equals(table)
				? "created_at bigint NULL" : "created_at timestamp NULL DEFAULT CURRENT_TIMESTAMP";
		st.executeUpdate("CREATE TABLE IF NOT EXISTS " + table + " (owner_uuid varchar(36) NOT NULL, "
				+ target + " varchar(36) NOT NULL, position_index int NOT NULL, " + createdAt
				+ ", PRIMARY KEY (owner_uuid, " + target + "))");
	}

	private synchronized void ensureConnected() throws SQLException {
		if (connection == null || connection.isClosed()) {
			disconnect();
			connect();
		}
	}

	private void ensureIndex(Connection db, String table, String index, String column) throws SQLException {
		try (ResultSet rs = db.getMetaData().getIndexInfo(null, null, table, false, false)) {
			while (rs.next()) {
				if (index.equalsIgnoreCase(rs.getString("INDEX_NAME"))
						|| (column.equalsIgnoreCase(rs.getString("COLUMN_NAME")) && rs.getShort("ORDINAL_POSITION") == 1)) return;
			}
		}
		try (Statement st = db.createStatement()) {
			st.executeUpdate("CREATE INDEX " + index + " ON " + table + " (" + column + ")");
		}
	}

	private void ensureColumn(Connection db, String table, String column, String definition) throws SQLException {
		try (ResultSet rs = db.getMetaData().getColumns(null, null, table, column)) {
			if (rs.next()) return;
		}
		try (Statement st = db.createStatement()) {
			st.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
		}
	}

	public final class Editor {
		private Editor() { }
		public MySQL getMySQL() { return MySQL.this; }

		public synchronized String get(String key) {
			Key k = parse(key);
			if (k.relation != null) return String.join(",&%$%&,", getList(key));
			try {
				ensureConnected();
				if (k.nameLookup) return queryOne("SELECT uuid FROM " + DATA + " WHERE LOWER(name) = ? ORDER BY uuid LIMIT 1", k.owner);
				return queryOne("SELECT " + k.column + " FROM " + DATA + " WHERE uuid = ?", k.owner, k.bool);
			} catch (SQLException e) {
				throw failure("read", key, e);
			}
		}

		public synchronized List<String> getList(String key) {
			Key k = parse(key);
			if (k.relation == null) return decodeList(get(key));
			try {
				ensureConnected();
			try (PreparedStatement ps = connection.prepareStatement("SELECT " + k.target + " FROM "
						+ k.relation + " WHERE owner_uuid = ? ORDER BY position_index, " + k.target)) {
				ps.setString(1, k.owner);
				List<String> result = new ArrayList<>();
				try (ResultSet rs = ps.executeQuery()) {
					while (rs.next()) result.add(rs.getString(1));
				}
				return result;
				}
			} catch (SQLException e) {
				throw failure("read", key, e);
			}
		}

		public synchronized boolean set(String key, String value) {
			Key k = parse(key);
			if (k.relation != null) return set(key, decodeList(value));
			if (k.nameLookup) throw new IllegalArgumentException("Name lookup is derived from player name: " + key);
			try {
				ensureConnected();
				try (PreparedStatement ps = connection.prepareStatement("INSERT INTO " + DATA + " (uuid, "
					+ k.column + ") VALUES (?, ?) ON DUPLICATE KEY UPDATE " + k.column + " = VALUES(" + k.column + ")")) {
				ps.setString(1, k.owner);
				if (k.bool && value != null) ps.setBoolean(2, Boolean.parseBoolean(value));
				else ps.setString(2, value);
				ps.executeUpdate();
				return true;
				}
			} catch (SQLException e) {
				throw failure("write", key, e);
			}
		}

		public synchronized boolean set(String key, List<String> values) {
			Key k = parse(key);
			if (k.relation == null) throw new IllegalArgumentException("Not a list key: " + key);
			Set<String> distinct = new LinkedHashSet<>();
			if (values != null) for (String value : values) distinct.add(UUID.fromString(value).toString());
			try {
				ensureConnected();
				transaction(() -> {
					try (PreparedStatement ps = connection.prepareStatement("DELETE FROM " + k.relation + " WHERE owner_uuid = ?")) {
						ps.setString(1, k.owner);
						ps.executeUpdate();
					}
					try (PreparedStatement ps = connection.prepareStatement("INSERT INTO " + k.relation
							+ " (owner_uuid, " + k.target + ", position_index) VALUES (?, ?, ?)")) {
						int index = 0;
						for (String value : distinct) {
							ps.setString(1, k.owner);
							ps.setString(2, value);
							ps.setInt(3, index++);
							ps.addBatch();
						}
						ps.executeBatch();
					}
				});
				return true;
			} catch (SQLException e) {
				throw failure("write", key, e);
			}
		}

		public synchronized void changeFriendship(UUID first, UUID second, boolean add) {
			if (first.equals(second)) throw new IllegalArgumentException("A player cannot friend itself");
			try {
				ensureConnected();
				transaction(() -> {
					changeFriend(first, second, add);
					changeFriend(second, first, add);
				});
			} catch (SQLException e) {
				throw failure("friendship", first + "/" + second, e);
			}
		}

		public synchronized void changeFriendRequest(UUID owner, UUID target, boolean add, long createdAt) {
			try {
				ensureConnected();
				int position = 0;
				if (add) {
					try (PreparedStatement order = connection.prepareStatement("SELECT COALESCE(MAX(position_index), -1) + 1"
							+ " FROM bungeefriends_friend_requests WHERE owner_uuid = ?")) {
						order.setString(1, owner.toString());
						try (ResultSet rs = order.executeQuery()) { rs.next(); position = rs.getInt(1); }
					}
				}
				String sql = add ? "INSERT INTO bungeefriends_friend_requests"
						+ " (owner_uuid, target_uuid, position_index, created_at) VALUES (?, ?, ?, ?)"
						+ " ON DUPLICATE KEY UPDATE created_at = VALUES(created_at)"
						: "DELETE FROM bungeefriends_friend_requests WHERE owner_uuid = ? AND target_uuid = ?";
				try (PreparedStatement ps = connection.prepareStatement(sql)) {
					ps.setString(1, owner.toString());
					ps.setString(2, target.toString());
					if (add) {
						ps.setInt(3, position);
						ps.setLong(4, createdAt);
					}
					ps.executeUpdate();
				}
			} catch (SQLException e) {
				throw failure("friend request", owner + "/" + target, e);
			}
		}

		public synchronized void changePartyRequest(UUID owner, UUID target, boolean add) {
			try {
				ensureConnected();
				int position = 0;
				if (add) {
					try (PreparedStatement order = connection.prepareStatement("SELECT COALESCE(MAX(position_index), -1) + 1"
							+ " FROM bungeefriends_party_requests WHERE owner_uuid = ?")) {
						order.setString(1, owner.toString());
						try (ResultSet rs = order.executeQuery()) { rs.next(); position = rs.getInt(1); }
					}
				}
				String sql = add ? "INSERT INTO bungeefriends_party_requests"
						+ " (owner_uuid, invited_uuid, position_index) VALUES (?, ?, ?)"
						+ " ON DUPLICATE KEY UPDATE created_at = CURRENT_TIMESTAMP"
						: "DELETE FROM bungeefriends_party_requests WHERE owner_uuid = ? AND invited_uuid = ?";
				try (PreparedStatement ps = connection.prepareStatement(sql)) {
					ps.setString(1, owner.toString());
					ps.setString(2, target.toString());
					if (add) ps.setInt(3, position);
					ps.executeUpdate();
				}
			} catch (SQLException e) {
				throw failure("party request", owner + "/" + target, e);
			}
		}

		public synchronized String getPartyRequestCreatedAt(UUID owner, UUID target) {
			return timestampValue("SELECT created_at FROM bungeefriends_party_requests"
					+ " WHERE owner_uuid = ? AND invited_uuid = ?", owner, target);
		}

		public synchronized String getFriendRequestCreatedAt(UUID owner, UUID target) {
			return relationValue("SELECT created_at FROM bungeefriends_friend_requests WHERE owner_uuid = ? AND target_uuid = ?",
					owner, target);
		}

		public synchronized String getFriendCreatedAt(UUID owner, UUID target) {
			return timestampValue("SELECT created_at FROM bungeefriends_friends WHERE owner_uuid = ? AND friend_uuid = ?",
					owner, target);
		}

		private String timestampValue(String sql, UUID owner, UUID target) {
			try {
				ensureConnected();
				try (PreparedStatement ps = connection.prepareStatement(sql)) {
					ps.setString(1, owner.toString());
					ps.setString(2, target.toString());
					try (ResultSet rs = ps.executeQuery()) {
						java.sql.Timestamp time = rs.next() ? rs.getTimestamp(1) : null;
						return time == null ? "" : String.valueOf(time.getTime());
					}
				}
			} catch (SQLException e) {
				throw failure("timestamp read", owner + "/" + target, e);
			}
		}

		public synchronized List<UUID> getFriendRequesters(UUID target) {
			try {
				ensureConnected();
				List<UUID> result = new ArrayList<>();
				try (PreparedStatement ps = connection.prepareStatement(
						"SELECT owner_uuid FROM bungeefriends_friend_requests WHERE target_uuid = ? ORDER BY created_at")) {
					ps.setString(1, target.toString());
					try (ResultSet rs = ps.executeQuery()) {
						while (rs.next()) result.add(UUID.fromString(rs.getString(1)));
					}
				}
				return result;
			} catch (SQLException e) {
				throw failure("friend requesters", target.toString(), e);
			}
		}

		public synchronized String getFriendPrefix(UUID viewer, UUID target) {
			return relationValue("SELECT custom_prefix FROM bungeefriends_friend_prefix WHERE viewer_uuid = ? AND target_uuid = ?",
					viewer, target);
		}

		public synchronized void setFriendPrefix(UUID viewer, UUID target, String prefix) {
			try {
				ensureConnected();
				try (PreparedStatement ps = connection.prepareStatement("INSERT INTO bungeefriends_friend_prefix"
						+ " (viewer_uuid, target_uuid, custom_prefix) VALUES (?, ?, ?)"
						+ " ON DUPLICATE KEY UPDATE custom_prefix = VALUES(custom_prefix)")) {
					ps.setString(1, viewer.toString());
					ps.setString(2, target.toString());
					ps.setString(3, prefix);
					ps.executeUpdate();
				}
			} catch (SQLException e) {
				throw failure("friend prefix", viewer + "/" + target, e);
			}
		}

		public synchronized void clearFriendPrefix(UUID viewer, UUID target) {
			deletePrefix(viewer, target);
		}

		public synchronized void clearAllFriendPrefixes(UUID viewer) {
			deletePrefix(viewer, null);
		}

		public synchronized String getFriendNickname(UUID viewer, UUID target) {
			return relationValue("SELECT nickname FROM bungeefriends_friend_nickname WHERE viewer_uuid = ? AND target_uuid = ?",
					viewer, target);
		}

		public synchronized void setFriendNickname(UUID viewer, UUID target, String nickname) {
			try {
				ensureConnected();
				try (PreparedStatement ps = connection.prepareStatement("INSERT INTO bungeefriends_friend_nickname"
						+ " (viewer_uuid, target_uuid, nickname) VALUES (?, ?, ?)"
						+ " ON DUPLICATE KEY UPDATE nickname = VALUES(nickname)")) {
					ps.setString(1, viewer.toString());
					ps.setString(2, target.toString());
					ps.setString(3, nickname);
					ps.executeUpdate();
				}
			} catch (SQLException e) {
				throw failure("friend nickname", viewer + "/" + target, e);
			}
		}

		public synchronized void clearFriendNickname(UUID viewer, UUID target) {
			deleteNickname(viewer, target);
		}

		public synchronized boolean isBestFriend(UUID viewer, UUID target) {
			try {
				ensureConnected();
				try (PreparedStatement ps = connection.prepareStatement("SELECT 1 FROM bungeefriends_best"
						+ " WHERE owner_uuid = ? AND best_uuid = ?")) {
					ps.setString(1, viewer.toString());
					ps.setString(2, target.toString());
					try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
				}
			} catch (SQLException e) {
				throw failure("best friend read", viewer + "/" + target, e);
			}
		}

		public synchronized void setBestFriend(UUID viewer, UUID target, boolean best) {
			try {
				ensureConnected();
				if (best) {
					try (PreparedStatement ps = connection.prepareStatement("INSERT INTO bungeefriends_best"
							+ " (owner_uuid, best_uuid, position_index) VALUES (?, ?, 0)"
							+ " ON DUPLICATE KEY UPDATE best_uuid = VALUES(best_uuid)")) {
						ps.setString(1, viewer.toString());
						ps.setString(2, target.toString());
						ps.executeUpdate();
					}
				} else {
					try (PreparedStatement ps = connection.prepareStatement("DELETE FROM bungeefriends_best"
							+ " WHERE owner_uuid = ? AND best_uuid = ?")) {
						ps.setString(1, viewer.toString());
						ps.setString(2, target.toString());
						ps.executeUpdate();
					}
				}
			} catch (SQLException e) {
				throw failure("best friend write", viewer + "/" + target, e);
			}
		}

		private void deletePrefix(UUID viewer, UUID target) {
			try {
				ensureConnected();
				String sql = "DELETE FROM bungeefriends_friend_prefix WHERE viewer_uuid = ?"
						+ (target == null ? "" : " AND target_uuid = ?");
				try (PreparedStatement ps = connection.prepareStatement(sql)) {
					ps.setString(1, viewer.toString());
					if (target != null) ps.setString(2, target.toString());
					ps.executeUpdate();
				}
			} catch (SQLException e) {
				throw failure("friend prefix removal", viewer.toString(), e);
			}
		}

		private void deleteNickname(UUID viewer, UUID target) {
			try {
				ensureConnected();
				String sql = "DELETE FROM bungeefriends_friend_nickname WHERE viewer_uuid = ?"
						+ (target == null ? "" : " AND target_uuid = ?");
				try (PreparedStatement ps = connection.prepareStatement(sql)) {
					ps.setString(1, viewer.toString());
					if (target != null) ps.setString(2, target.toString());
					ps.executeUpdate();
				}
			} catch (SQLException e) {
				throw failure("friend nickname removal", viewer.toString(), e);
			}
		}

		private String relationValue(String sql, UUID first, UUID second) {
			try {
				ensureConnected();
				try (PreparedStatement ps = connection.prepareStatement(sql)) {
					ps.setString(1, first.toString());
					ps.setString(2, second.toString());
					try (ResultSet rs = ps.executeQuery()) {
						return rs.next() && rs.getObject(1) != null ? rs.getString(1) : "";
					}
				}
			} catch (SQLException e) {
				throw failure("relation read", first + "/" + second, e);
			}
		}

		public synchronized void updateParty(UUID leader, List<UUID> participants, List<UUID> detached) {
			if (!participants.isEmpty() && !participants.contains(leader)) throw new IllegalArgumentException("Leader must be a participant");
			Set<UUID> active = new LinkedHashSet<>(participants);
			if (active.size() != participants.size()) throw new IllegalArgumentException("Duplicate party participant");
			for (UUID player : detached) {
				if (active.contains(player)) throw new IllegalArgumentException("Active player cannot be detached");
			}
			try {
				ensureConnected();
				transaction(() -> {
				for (UUID player : active) writePartyPlayer(player, leader.toString(), active);
				for (UUID player : new LinkedHashSet<>(detached)) writePartyPlayer(player, "NONE", java.util.Collections.emptySet());
				});
			} catch (SQLException e) {
				throw failure("party update", leader.toString(), e);
			}
		}

		public synchronized void acceptPartyRequest(UUID leader, List<UUID> participants, UUID invited) {
			if (!participants.contains(leader)) throw new IllegalArgumentException("Leader must be a participant");
			Set<UUID> active = new LinkedHashSet<>(participants);
			if (active.size() != participants.size()) throw new IllegalArgumentException("Duplicate party participant");
			if (!active.contains(invited)) throw new IllegalArgumentException("Invited player must be a participant");
			try {
				ensureConnected();
				transaction(() -> {
					for (UUID player : active) writePartyPlayer(player, leader.toString(), active);
					try (PreparedStatement ps = connection.prepareStatement(
							"DELETE FROM bungeefriends_party_requests WHERE owner_uuid = ? AND invited_uuid = ?")) {
						ps.setString(1, leader.toString());
						ps.setString(2, invited.toString());
						if (ps.executeUpdate() != 1) throw new SQLException("Party request was not found");
					}
				});
			} catch (SQLException e) {
				throw failure("party acceptance", leader + "/" + invited, e);
			}
		}

		private void writePartyPlayer(UUID player, String leader, Set<UUID> active) throws SQLException {
			try (PreparedStatement ps = connection.prepareStatement("INSERT INTO " + DATA
					+ " (uuid, party_current) VALUES (?, ?) ON DUPLICATE KEY UPDATE party_current = VALUES(party_current)")) {
				ps.setString(1, player.toString());
				ps.setString(2, leader);
				ps.executeUpdate();
			}
			try (PreparedStatement ps = connection.prepareStatement(
					"DELETE FROM bungeefriends_party_members WHERE owner_uuid = ?")) {
				ps.setString(1, player.toString());
				ps.executeUpdate();
			}
			try (PreparedStatement ps = connection.prepareStatement("INSERT INTO bungeefriends_party_members"
					+ " (owner_uuid, member_uuid, position_index) VALUES (?, ?, ?)")) {
				int position = 0;
				for (UUID member : active) {
					if (member.equals(UUID.fromString(leader))) continue;
					ps.setString(1, player.toString());
					ps.setString(2, member.toString());
					ps.setInt(3, position++);
					ps.addBatch();
				}
				ps.executeBatch();
			}
		}

		private void changeFriend(UUID owner, UUID target, boolean add) throws SQLException {
			int nextIndex = 0;
			if (add) {
				try (PreparedStatement order = connection.prepareStatement(
						"SELECT COALESCE(MAX(position_index), -1) + 1 FROM bungeefriends_friends WHERE owner_uuid = ?")) {
					order.setString(1, owner.toString());
					try (ResultSet rs = order.executeQuery()) {
						rs.next();
						nextIndex = rs.getInt(1);
					}
				}
			}
			String sql = add ? "INSERT INTO bungeefriends_friends (owner_uuid, friend_uuid, position_index) "
					+ "VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE friend_uuid = VALUES(friend_uuid)"
					: "DELETE FROM bungeefriends_friends WHERE owner_uuid = ? AND friend_uuid = ?";
			try (PreparedStatement ps = connection.prepareStatement(sql)) {
				ps.setString(1, owner.toString());
				ps.setString(2, target.toString());
				if (add) ps.setInt(3, nextIndex);
				ps.executeUpdate();
			}
		}

		private void transaction(SqlAction action) throws SQLException {
			boolean autoCommit = connection.getAutoCommit();
			connection.setAutoCommit(false);
			try {
				action.run();
				connection.commit();
			} catch (SQLException | RuntimeException e) {
				try { connection.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
				throw e;
			} finally {
				connection.setAutoCommit(autoCommit);
			}
		}

		private String queryOne(String sql, String parameter) throws SQLException {
			return queryOne(sql, parameter, false);
		}

		private String queryOne(String sql, String parameter, boolean bool) throws SQLException {
			try (PreparedStatement ps = connection.prepareStatement(sql)) {
				ps.setString(1, parameter);
				try (ResultSet rs = ps.executeQuery()) {
					if (!rs.next()) return "";
					Object raw = rs.getObject(1);
					if (bool && raw != null) return String.valueOf(rs.getBoolean(1));
					return raw == null ? "" : raw.toString();
				}
			}
		}
	}

	private interface SqlAction { void run() throws SQLException; }

	private IllegalStateException failure(String operation, String key, SQLException cause) {
		if (plugin != null) plugin.getLogger().severe("MySQL " + operation + " failed for " + key + ": " + cause.getMessage());
		if (cause.getSQLState() != null && cause.getSQLState().startsWith("08")) {
			try { disconnect(); } catch (SQLException closeFailure) { cause.addSuppressed(closeFailure); }
		}
		return new IllegalStateException("MySQL " + operation + " failed for " + key, cause);
	}

	private static List<String> decodeList(String value) {
		List<String> result = new ArrayList<>();
		if (value == null || value.isEmpty() || "[]".equals(value)) return result;
		for (String part : value.split(java.util.regex.Pattern.quote(",&%$%&,"))) result.add(part);
		return result;
	}

	private static Key parse(String key) {
		if (key.startsWith("Name.")) return new Key(key.substring(5).toLowerCase(Locale.ROOT), null, null, null, false, true);
		if (key.startsWith("UUID.")) return field(uuid(key.substring(5)), "name", false);
		String[] parts = key.split("\\.", 3);
		if (parts.length != 3 || (!"Player".equals(parts[0]) && !"Parties".equals(parts[0])))
			throw new IllegalArgumentException("Unknown storage key: " + key);
		String owner = uuid(parts[1]);
		boolean party = "Parties".equals(parts[0]);
		switch (parts[2]) {
		case "Friends": if (!party) return relation(owner, "bungeefriends_friends", "friend_uuid"); break;
		case "IgnoreList": if (!party) return relation(owner, "bungeefriends_ignore", "ignored_uuid"); break;
		case "Requests": return relation(owner, party ? "bungeefriends_party_requests" : "bungeefriends_friend_requests",
				party ? "invited_uuid" : "target_uuid");
		case "BestFriends": if (!party) return relation(owner, "bungeefriends_best", "best_uuid"); break;
		case "Party-List": if (party) return relation(owner, "bungeefriends_party_members", "member_uuid"); break;
		case "Moderators": if (party) return relation(owner, "bungeefriends_party_moderators", "moderator_uuid"); break;
		case "Name": return field(owner, party ? "party_name" : "name", false);
		case "Language": if (!party) return field(owner, "language", false); break;
		case "Prefix": if (!party) return field(owner, "prefix", false); break;
		case "CustomPrefix": if (!party) return field(owner, "custom_prefix", false); break;
		case "Status": if (!party) return field(owner, "status", false); break;
		case "Toggle": return field(owner, party ? "party_toggle" : "toggle", true);
		case "FriendNotifications": if (!party) return field(owner, "friend_notifications", true); break;
		case "LastOnline": if (!party) return field(owner, "last_online", false); break;
		case "Currently-Joined-Party": if (party) return field(owner, "party_current", false); break;
		case "AllInvite": if (party) return field(owner, "party_allinvite", true); break;
		case "Mute": if (party) return field(owner, "party_mute", true); break;
		case "Private": if (party) return field(owner, "party_private", true); break;
		default: break;
		}
		throw new IllegalArgumentException("Unknown storage key: " + key);
	}

	private static Key field(String owner, String column, boolean bool) { return new Key(owner, column, null, null, bool, false); }
	private static Key relation(String owner, String table, String target) { return new Key(owner, null, table, target, false, false); }
	private static String uuid(String value) { return UUID.fromString(value).toString(); }

	private static final class Key {
		final String owner, column, relation, target;
		final boolean bool, nameLookup;
		Key(String owner, String column, String relation, String target, boolean bool, boolean nameLookup) {
			this.owner = owner; this.column = column; this.relation = relation; this.target = target;
			this.bool = bool; this.nameLookup = nameLookup;
		}
	}
}
