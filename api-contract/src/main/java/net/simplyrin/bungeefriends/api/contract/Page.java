package net.simplyrin.bungeefriends.api.contract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class Page<T> {
    private final List<T> items;
    private final int offset;
    private final int limit;
    private final int total;

    public Page(List<T> items, int offset, int limit, int total) {
        if (offset < 0 || limit < 1 || total < 0) throw new IllegalArgumentException("Invalid page metadata");
        this.items = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(items, "items")));
        this.offset = offset;
        this.limit = limit;
        this.total = total;
    }
    public List<T> getItems() { return items; }
    public int getOffset() { return offset; }
    public int getLimit() { return limit; }
    public int getTotal() { return total; }
    public boolean hasNext() { return (long) offset + items.size() < total; }
}
