package spawnparty.gui;

import java.util.List;

public final class Pagination {

	public static final int PAGE_SIZE = 21;

	private Pagination() {
	}

	public static int pageCount(int total) {
		if (total <= 0) return 1;
		return (total + PAGE_SIZE - 1) / PAGE_SIZE;
	}

	public static <T> List<T> page(List<T> items, int page) {
		int count = pageCount(items.size());
		int safe = Math.max(0, Math.min(page, count - 1));
		int from = safe * PAGE_SIZE;
		int to = Math.min(items.size(), from + PAGE_SIZE);
		if (from >= items.size()) return List.of();
		return items.subList(from, to);
	}
}
