package spawnparty.api;

/**
 * Fills a hosted setup chest. Prefer this over {@code Consumer<SetupPage>} —
 * other mods can implement or proxy it without JDK functional-interface tricks.
 */
@FunctionalInterface
public interface SetupPageFiller {

	void fill(SetupPage page);
}
