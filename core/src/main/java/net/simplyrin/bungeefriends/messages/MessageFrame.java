package net.simplyrin.bungeefriends.messages;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MessageFrame {
	private MessageFrame() { }

	public enum Category {
		COMMAND_RESULTS,
		LISTS,
		PARTY_NOTIFICATIONS,
		WARP_NOTIFICATIONS;

		public String configKey() { return this.name().replace('_', '-'); }
	}

	public enum MessageId {
		BLOCK_HELP(Category.COMMAND_RESULTS), BLOCK_EMPTY(Category.LISTS), BLOCK_REMOVE_ALL_DONE(Category.COMMAND_RESULTS),
		BLOCK_COMMAND(Category.COMMAND_RESULTS), BLOCK_ADD(Category.COMMAND_RESULTS), BLOCK_REMOVE(Category.COMMAND_RESULTS),
		PLAYER_NOT_FOUND(Category.COMMAND_RESULTS), BLOCK_SELF(Category.COMMAND_RESULTS), IGNORE_ADDED(Category.COMMAND_RESULTS),
		IGNORE_ALREADY_ADDED(Category.COMMAND_RESULTS), IGNORE_NOT_ADDED(Category.COMMAND_RESULTS),
		FRIEND_REMOVE_SELF(Category.COMMAND_RESULTS), FRIEND_LIST_EMPTY(Category.LISTS), IGNORE_LIST_EMPTY(Category.LISTS),
		FRIEND_REQUEST_SENT(Category.COMMAND_RESULTS), CHAT_NEED_PARTY(Category.COMMAND_RESULTS), CHAT_NO_PERMISSION(Category.COMMAND_RESULTS),
		CHAT_UPDATED(Category.COMMAND_RESULTS), PARTY_INVITE_SENT(Category.PARTY_NOTIFICATIONS), PARTY_INVITE_NOTICE(Category.PARTY_NOTIFICATIONS),
		PARTY_INVITE_RECEIVED(Category.PARTY_NOTIFICATIONS), PARTY_INVITE_ERROR(Category.PARTY_NOTIFICATIONS),
		PARTY_LOGOUT_LEADER_DISCONNECTED(Category.PARTY_NOTIFICATIONS),
		PARTY_LEADER_CHANGED(Category.PARTY_NOTIFICATIONS), PARTY_DISBANDED(Category.PARTY_NOTIFICATIONS),
		PARTY_CONNECTION_REJOIN(Category.PARTY_NOTIFICATIONS), PARTY_CONNECTION_OFFLINE(Category.PARTY_NOTIFICATIONS),
		PARTY_CONNECTION_OFFLINE_NO_KICK(Category.PARTY_NOTIFICATIONS), PARTY_MEMBER_REMOVED(Category.PARTY_NOTIFICATIONS),
		PARTY_MUTE_DENIED(Category.PARTY_NOTIFICATIONS), PARTY_BROADCAST(Category.PARTY_NOTIFICATIONS),
		NO_JOINED_PARTY(Category.COMMAND_RESULTS), PARTY_CHAT_USAGE(Category.COMMAND_RESULTS),
		TELL_CANT_SEND_SELF(Category.COMMAND_RESULTS), TELL_MUST_BE_FRIENDS(Category.COMMAND_RESULTS), TELL_OFFLINE(Category.COMMAND_RESULTS),
		REPLY_TARGET_NONE(Category.COMMAND_RESULTS),
		FRIEND_REMOVE_NOT_FRIEND(Category.COMMAND_RESULTS), FRIEND_REMOVE_DONE(Category.COMMAND_RESULTS),
		FRIEND_REMOVE_TARGET(Category.COMMAND_RESULTS), FRIEND_REMOVE_ALL_DONE(Category.COMMAND_RESULTS),
		FRIEND_ACCEPT_NO_REQUEST(Category.COMMAND_RESULTS), FRIEND_ACCEPTED(Category.COMMAND_RESULTS),
		FRIEND_ACCEPT_NOTICE(Category.COMMAND_RESULTS), FRIEND_DENY_NOT_FRIEND(Category.COMMAND_RESULTS),
		FRIEND_DENIED(Category.COMMAND_RESULTS), FRIEND_REQUESTS_LIST(Category.LISTS), FRIEND_LIST_PAGE(Category.LISTS),
		IGNORE_REMOVE_USAGE(Category.COMMAND_RESULTS), IGNORE_LIST(Category.LISTS), FRIEND_ALREADY_ADDED(Category.COMMAND_RESULTS),
		FRIEND_ADD_SELF(Category.COMMAND_RESULTS), FRIEND_FORCE_ADD(Category.COMMAND_RESULTS),
		FRIEND_FORCE_ADD_NOTICE(Category.PARTY_NOTIFICATIONS), FRIEND_PREFIX_SET(Category.COMMAND_RESULTS),
		FRIEND_PREFIX_CURRENT(Category.COMMAND_RESULTS), FRIEND_HELP(Category.COMMAND_RESULTS),
		FRIEND_REQUEST_ALREADY_SENT(Category.COMMAND_RESULTS), FRIEND_REQUEST_RECEIVED(Category.COMMAND_RESULTS),
		FRIEND_REQUEST_EXPIRED_SELF(Category.COMMAND_RESULTS), FRIEND_REQUEST_EXPIRED_TARGET(Category.COMMAND_RESULTS),
		FRIEND_ADD_IGNORED(Category.COMMAND_RESULTS), FRIEND_SLOT_LIMIT(Category.COMMAND_RESULTS),
		FRIEND_REQUEST_DENIED(Category.COMMAND_RESULTS), FRIEND_ADD_ALREADY_FRIEND(Category.COMMAND_RESULTS),
		FRIEND_FORCE_ADD_SELF(Category.COMMAND_RESULTS), FRIEND_FORCE_ADD_TARGET(Category.COMMAND_RESULTS),
		FRIEND_ADD_ALREADY_SENT(Category.COMMAND_RESULTS),
		PARTY_INVITE_USAGE(Category.COMMAND_RESULTS), PARTY_LEAVE_NEED_DISBAND(Category.COMMAND_RESULTS),
		PARTY_LEFT(Category.PARTY_NOTIFICATIONS), PARTY_PLAYER_LEFT(Category.PARTY_NOTIFICATIONS), PARTY_LIST(Category.LISTS),
		PARTY_REMOVE_ME(Category.PARTY_NOTIFICATIONS), PARTY_REMOVE_USAGE(Category.COMMAND_RESULTS),
		PARTY_MUST_LEADER(Category.COMMAND_RESULTS), PARTY_PROMOTED(Category.PARTY_NOTIFICATIONS),
		PARTY_PROMOTE_USAGE(Category.COMMAND_RESULTS), PARTY_HIJACK_NOT_JOINED(Category.COMMAND_RESULTS),
		PARTY_HIJACK_OTHER(Category.PARTY_NOTIFICATIONS), PARTY_HIJACK_YOU(Category.PARTY_NOTIFICATIONS),
		PARTY_HIJACK_USAGE(Category.COMMAND_RESULTS), PARTY_ACCEPTED(Category.PARTY_NOTIFICATIONS),
		PARTY_ACCEPT_USAGE(Category.COMMAND_RESULTS), PARTY_TOGGLE(Category.COMMAND_RESULTS),
		PARTY_KICKOFFLINE_ALL_ONLINE(Category.COMMAND_RESULTS), PARTY_INVITE_EXPIRED_SELF(Category.COMMAND_RESULTS),
		PARTY_INVITE_EXPIRED_TARGET(Category.PARTY_NOTIFICATIONS), PARTY_ALL_LEFT(Category.PARTY_NOTIFICATIONS),
		CHAT_COMMAND_MESSAGES(Category.COMMAND_RESULTS), BLOCK_LIST_PAGE(Category.LISTS),
		CONSOLE_PREFIX(Category.COMMAND_RESULTS), CONSOLE_LANGUAGE(Category.COMMAND_RESULTS), CONSOLE_HELP(Category.COMMAND_RESULTS),
		WARP_CONFIRM(Category.WARP_NOTIFICATIONS), WARP_SUMMON(Category.WARP_NOTIFICATIONS), WARP_SENDING(Category.WARP_NOTIFICATIONS),
		STATUS_DISABLED(Category.COMMAND_RESULTS), STATUS_USAGE(Category.COMMAND_RESULTS);

		private final Category category;

		MessageId(Category category) { this.category = category; }
		public Category getCategory() { return category; }
	}

	public static boolean shouldFrame(boolean globallyDisabled, boolean categoryEnabled, Boolean messageOverride) {
		if (globallyDisabled) return false;
		return messageOverride != null ? messageOverride.booleanValue() : categoryEnabled;
	}

	public static void writeDocumentation(File file) throws IOException {
		StringBuilder doc = new StringBuilder();
		doc.append("# Hyphen Configuration\n\n")
			.append("Generated by BungeeFriends at startup. Changes to this file are replaced on the next start.\n\n")
			.append("## Precedence\n\n")
			.append("1. `Plugin.Disable-Hyphen: true` disables every frame.\n")
			.append("2. `Plugin.Hyphen.Messages.<ID>` overrides its category when set to `true` or `false`.\n")
			.append("3. Otherwise, `Plugin.Hyphen.Categories.<Category>` is used.\n\n")
			.append("Chat message bodies and Action Bar messages are not framed.\n\n")
			.append("## Categories\n\n")
			.append("| Config key | Default |\n| --- | --- |\n");
		for (Category category : Category.values()) {
			doc.append("| `").append(category.configKey()).append("` | `true` |\n");
		}
		doc.append("\n## Message IDs\n\n")
			.append("Set an individual override under `Plugin.Hyphen.Messages`. Example: `PARTY_MUTE_DENIED: false`.\n\n")
			.append("| ID | Category |\n| --- | --- |\n");
		for (MessageId id : MessageId.values()) {
			doc.append("| `").append(id.name()).append("` | `")
				.append(id.getCategory().configKey()).append("` |\n");
		}
		File parent = file.getParentFile();
		if (parent != null && !parent.exists() && !parent.mkdirs())
			throw new IOException("Could not create directory " + parent);
		Files.write(file.toPath(), doc.toString().getBytes(StandardCharsets.UTF_8));
	}

	public static List<String> wrap(String separator, String message) {
		if (message == null || message.isEmpty()) return Collections.emptyList();
		String[] messageLines = message.split("\\n", -1);
		boolean framed = separator != null && !separator.isEmpty();
		List<String> lines = new ArrayList<>(messageLines.length + (framed ? 2 : 0));
		if (framed) lines.add(separator);
		Collections.addAll(lines, messageLines);
		if (framed) lines.add(separator);
		return lines;
	}
}
