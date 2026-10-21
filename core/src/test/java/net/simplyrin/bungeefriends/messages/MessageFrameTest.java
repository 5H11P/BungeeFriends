package net.simplyrin.bungeefriends.messages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class MessageFrameTest {
	@Test
	public void wrapsEachConfiguredLineWithOneOuterFrame() {
		assertEquals(Arrays.asList("-", "first", "second", "-"), MessageFrame.wrap("-", "first\nsecond"));
	}

	@Test
	public void disabledHyphenKeepsMessageLinesWithoutSeparators() {
		assertEquals(Arrays.asList("first", "second"), MessageFrame.wrap("", "first\nsecond"));
	}

	@Test
	public void ignoresEmptyMessages() {
		assertEquals(Collections.emptyList(), MessageFrame.wrap("-", ""));
	}

	@Test
	public void globalDisableOverridesMessageAndCategorySettings() {
		assertEquals(false, MessageFrame.shouldFrame(true, true, Boolean.TRUE));
	}

	@Test
	public void messageOverrideTakesPrecedenceOverCategory() {
		assertEquals(true, MessageFrame.shouldFrame(false, false, Boolean.TRUE));
		assertEquals(false, MessageFrame.shouldFrame(false, true, Boolean.FALSE));
	}

	@Test
	public void categoryIsUsedWhenMessageOverrideIsMissing() {
		assertEquals(true, MessageFrame.shouldFrame(false, true, null));
		assertEquals(false, MessageFrame.shouldFrame(false, false, null));
	}

	@Test
	public void messageIdsBelongToExpectedCategories() {
		assertEquals(MessageFrame.Category.PARTY_NOTIFICATIONS, MessageFrame.MessageId.PARTY_MUTE_DENIED.getCategory());
		assertEquals(MessageFrame.Category.LISTS, MessageFrame.MessageId.FRIEND_LIST_EMPTY.getCategory());
		assertEquals(MessageFrame.Category.WARP_NOTIFICATIONS, MessageFrame.MessageId.WARP_CONFIRM.getCategory());
	}

	@Test
	public void generatedGuideListsPrecedenceCategoriesAndMessageIds() throws Exception {
		Path file = Files.createTempFile("hyphen-guide-", ".md");
		try {
			MessageFrame.writeDocumentation(file.toFile());
			String doc = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
			assertTrue(doc.contains("Plugin.Enable-Hyphen"));
			assertTrue(doc.contains("PARTY_MUTE_DENIED"));
			assertTrue(doc.contains("WARP-NOTIFICATIONS"));
		} finally {
			Files.deleteIfExists(file);
		}
	}
}
