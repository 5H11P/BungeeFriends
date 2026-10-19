package net.simplyrin.bungeefriends.api.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Versioned messages compiled into both endpoint JARs. */
public final class Protocol {
	public static final String CHANNEL = "bungeefriends:api";
	public static final String WARP_CHANNEL = "bungeefriends:warp";
	public static final int VERSION = 1;
	public static final int MAX_BYTES = 30000;
	public static final byte REQUEST = 1;
	public static final byte RESPONSE = 2;
	public static final byte COMMAND = 3;
	public static final byte EVENT = 4;
	public static final byte OK = 0;
	public static final byte INVALID = 1;
	public static final byte DENIED = 2;
	public static final byte NOT_FOUND = 3;
	public static final byte ERROR = 4;
	public static final byte UNSUPPORTED = 5;
	private static final int MAX_VALUES = 256;
	private static final int MAX_VALUE_LENGTH = 512;

	private Protocol() { }

	public static final class Message {
		public final byte kind;
		public final UUID id;
		public final UUID actor;
		public final long issuedAt;
		public final String operation;
		public final byte status;
		public final List<String> values;

		public Message(byte kind, UUID id, UUID actor, String operation, byte status, List<String> values) {
			this(kind, id, actor, System.currentTimeMillis(), operation, status, values);
		}

		private Message(byte kind, UUID id, UUID actor, long issuedAt, String operation, byte status, List<String> values) {
			if (kind < REQUEST || kind > EVENT || id == null || actor == null || operation == null || values == null)
				throw new IllegalArgumentException("Invalid protocol message");
			this.kind = kind;
			this.id = id;
			this.actor = actor;
			this.issuedAt = issuedAt;
			this.operation = operation;
			this.status = status;
			this.values = Collections.unmodifiableList(new ArrayList<>(values));
		}
	}

	public static byte[] encode(Message message) {
		if (message.operation.length() > 64 || message.values.size() > MAX_VALUES)
			throw new IllegalArgumentException("Protocol message exceeds field limits");
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			DataOutputStream out = new DataOutputStream(bytes);
			out.writeByte(VERSION);
			out.writeByte(message.kind);
			writeUuid(out, message.id);
			writeUuid(out, message.actor);
			out.writeLong(message.issuedAt);
			out.writeUTF(message.operation);
			out.writeByte(message.status);
			out.writeShort(message.values.size());
			for (String value : message.values) {
				if (value == null || value.length() > MAX_VALUE_LENGTH)
					throw new IllegalArgumentException("Protocol value exceeds field limit");
				out.writeUTF(value);
			}
			out.flush();
			if (bytes.size() > MAX_BYTES) throw new IllegalArgumentException("Protocol packet is too large");
			return bytes.toByteArray();
		} catch (IOException e) {
			throw new IllegalArgumentException("Cannot encode protocol message", e);
		}
	}

	public static Message decode(byte[] bytes) {
		if (bytes == null || bytes.length > MAX_BYTES || bytes.length < 47)
			throw new IllegalArgumentException("Invalid protocol packet size");
		try {
			DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
			if (in.readUnsignedByte() != VERSION) throw new IllegalArgumentException("Unsupported protocol version");
			byte kind = in.readByte();
			UUID id = readUuid(in);
			UUID actor = readUuid(in);
			long issuedAt = in.readLong();
			String operation = in.readUTF();
			if (operation.length() > 64) throw new IllegalArgumentException("Operation is too long");
			byte status = in.readByte();
			int count = in.readUnsignedShort();
			if (count > MAX_VALUES) throw new IllegalArgumentException("Too many protocol values");
			List<String> values = new ArrayList<>(count);
			for (int index = 0; index < count; index++) {
				String value = in.readUTF();
				if (value.length() > MAX_VALUE_LENGTH) throw new IllegalArgumentException("Protocol value is too long");
				values.add(value);
			}
			if (in.available() != 0) throw new IllegalArgumentException("Trailing protocol data");
			return new Message(kind, id, actor, issuedAt, operation, status, values);
		} catch (IOException e) {
			throw new IllegalArgumentException("Malformed protocol packet", e);
		}
	}

	private static void writeUuid(DataOutputStream out, UUID value) throws IOException {
		out.writeLong(value.getMostSignificantBits());
		out.writeLong(value.getLeastSignificantBits());
	}

	private static UUID readUuid(DataInputStream in) throws IOException {
		return new UUID(in.readLong(), in.readLong());
	}

	public static boolean isFresh(Message message, long now) {
		return message.issuedAt <= now + 5000 && now - message.issuedAt <= 30000;
	}

	public static boolean allowedCommand(String command, List<String> allowedLabels) {
		if (command == null || allowedLabels == null || command.isEmpty() || command.length() > 256
				|| command.charAt(0) == '/') return false;
		for (int index = 0; index < command.length(); index++) {
			if (Character.isISOControl(command.charAt(index))) return false;
		}
		String label = command.split(" ", 2)[0];
		if (!label.matches("[A-Za-z0-9_:-]{1,64}")) return false;
		for (String allowed : allowedLabels) if (allowed != null && label.equalsIgnoreCase(allowed.trim())) return true;
		return false;
	}
}
