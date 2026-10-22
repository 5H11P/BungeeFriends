package net.simplyrin.bungeefriends.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;

public class PaginationMessageTest {

	@Test
	public void pageButtonsOnlyTargetExistingPages() {
		TextComponent first = PaginationMessage.build(key -> key, "%previousPage% page %currentPage%/%maxPage% %nextPage%", "/friend list ", 1, 3);
		assertEquals("/friend list 2", clicks(first).get(0));
		assertEquals(1, clicks(first).size());
		TextComponent middle = PaginationMessage.build(key -> key, "%previousPage% page %currentPage%/%maxPage% %nextPage%", "/friend list ", 2, 3);
		assertEquals("/friend list 1", clicks(middle).get(0));
		assertEquals("/friend list 3", clicks(middle).get(1));
		TextComponent only = PaginationMessage.build(key -> key, "page %currentPage%/%maxPage%", "/block list ", 1, 1);
		assertNull(only.getClickEvent());
		assertEquals(0, clicks(only).size());
		TextComponent block = PaginationMessage.build(key -> key, "page %currentPage%/%maxPage%", "/block list ", 2, 3);
		assertEquals("/block list 1", clicks(block).get(0));
		assertEquals("/block list 3", clicks(block).get(1));
	}

	private List<String> clicks(BaseComponent component) {
		List<String> values = new ArrayList<>();
		if (component.getClickEvent() != null) values.add(component.getClickEvent().getValue());
		if (component.getExtra() != null)
			for (BaseComponent child : component.getExtra()) values.addAll(clicks(child));
		return values;
	}
}
