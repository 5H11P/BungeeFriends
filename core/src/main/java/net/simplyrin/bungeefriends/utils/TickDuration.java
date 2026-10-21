package net.simplyrin.bungeefriends.utils;

import java.util.ArrayList;
import java.util.List;

import net.simplyrin.bungeefriends.utils.LanguageManager.LanguageUtils;

public final class TickDuration {
	public static final long MILLIS_PER_TICK = 50L;

	private TickDuration() { }

	public static long toMillis(long ticks) {
		if (ticks < 0) throw new IllegalArgumentException("ticks must not be negative");
		return Math.multiplyExact(ticks, MILLIS_PER_TICK);
	}

	public static String format(long ticks, LanguageUtils language) {
		return format(ticks,
				language.getString("Duration-Units.Day"),
				language.getString("Duration-Units.Hour"),
				language.getString("Duration-Units.Minute"),
				language.getString("Duration-Units.Second"),
				language.getString("Duration-Units.Millisecond"),
				language.getString("Duration-Units.Separator"));
	}

	static String format(long ticks, String day, String hour, String minute, String second, String millisecond, String separator) {
		long remaining = toMillis(ticks);
		long[] sizes = {86_400_000L, 3_600_000L, 60_000L, 1_000L, 1L};
		String[] units = {day, hour, minute, second, millisecond};
		List<String> parts = new ArrayList<>();
		for (int index = 0; index < sizes.length; index++) {
			long amount = remaining / sizes[index];
			remaining %= sizes[index];
			if (amount > 0) parts.add(amount + separator + units[index]);
		}
		return parts.isEmpty() ? "0" + separator + millisecond : String.join(separator, parts);
	}
}
