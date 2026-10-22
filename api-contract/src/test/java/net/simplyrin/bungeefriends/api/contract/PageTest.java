package net.simplyrin.bungeefriends.api.contract;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class PageTest {
	@Test
	public void copiesItemsAndReportsContinuation() {
		List<String> source = new ArrayList<>(Arrays.asList("a", "b"));
		Page<String> page = new Page<>(source, 0, 2, 3);
		source.clear();
		assertEquals(Arrays.asList("a", "b"), page.getItems());
		assertTrue(page.hasNext());
		try { page.getItems().add("c"); fail("Expected immutable items"); }
		catch (UnsupportedOperationException expected) { }
		assertFalse(new Page<>(Arrays.asList("c"), 2, 2, 3).hasNext());
	}
	@Test(expected = IllegalArgumentException.class)
	public void rejectsInvalidMetadata() { new Page<>(new ArrayList<String>(), -1, 10, 0); }
}
