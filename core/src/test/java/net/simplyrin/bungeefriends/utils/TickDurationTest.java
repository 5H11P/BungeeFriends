package net.simplyrin.bungeefriends.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import net.md_5.bungee.config.Configuration;
import net.simplyrin.bungeefriends.tools.Config;

public class TickDurationTest {
	@Test
	public void convertsTicksAtFixedFiftyMilliseconds() {
		assertEquals(1000L, TickDuration.toMillis(20));
		assertEquals(500L, TickDuration.toMillis(10));
	}

	@Test
	public void formatsEnglishAndChineseDurations() {
		Configuration english = Config.getConfig(getClass().getResourceAsStream("/languages/English.yml"));
		Configuration chinese = Config.getConfig(getClass().getResourceAsStream("/languages/Chinese.yml"));
		assertEquals("3 min 20 s", format(english, 4000));
		assertEquals("1 s 500 ms", format(english, 30));
		assertEquals("3分20秒", format(chinese, 4000));
		assertEquals("500毫秒", format(chinese, 10));
	}

	private String format(Configuration config, long ticks) {
		return TickDuration.format(ticks, config.getString("Duration-Units.Day"),
				config.getString("Duration-Units.Hour"), config.getString("Duration-Units.Minute"),
				config.getString("Duration-Units.Second"), config.getString("Duration-Units.Millisecond"),
				config.getString("Duration-Units.Separator"));
	}
}
