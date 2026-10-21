package net.simplyrin.bungeefriends.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.config.Configuration;
import net.simplyrin.bungeefriends.Main;

/**
 * Created by SimplyRin on 2021/03/19.
 *
 * author: SimplyRin
 * license: LGPL v3
 * copyright: Copyright (c) 2021 SimplyRin
 */
@RequiredArgsConstructor
public class Priority {

	private final Main instance;

	private Map<Integer, String> map = new TreeMap<Integer, String>();

	public void addPriority(String noColorPrefix, int priority) {
		this.map.put(priority, noColorPrefix);
	}

	public List<Item> getPriorities() {
		List<Item> items = new ArrayList<>();

		for (Entry<Integer, String> entry : this.map.entrySet()) {
			Item item = new Item();
			item.priority = entry.getKey();
			item.noColorPrefix = entry.getValue();
			items.add(item);
		}

		return items;
	}

	public void update() {
		this.map.clear();

		Configuration config = this.instance.getConfigManager().getFriendListConfig();

		for (Object value : config.getList("Sort")) {
			if (!(value instanceof Map)) continue;
			Map<?, ?> item = (Map<?, ?>) value;
			Object rawPrefix = item.get("Prefix");
			Object rawPriority = item.get("Priority");
			if (rawPrefix == null || rawPriority == null) continue;
			try {
				String prefix = String.valueOf(rawPrefix);
				int priority = Integer.parseInt(String.valueOf(rawPriority));
				if (!prefix.trim().isEmpty() && priority >= 0) this.addPriority(prefix, priority);
			} catch (NumberFormatException ignored) { }
		}
	}

	@Getter
	public class Item {
		private int priority;
		private String noColorPrefix;
	}

}
