# Changelog

## 1.0.0-beta.1

Minecraft **26.3**, Fabric Loader 0.19.5. Jar: `spawnparty-26.3-fabric-1.0.0-beta.1.jar`.

26.2 worlds are not a supported upgrade. Create a new world.

### Added

- `max_party_size` (default `0` = unlimited; `1` = distributed solo spawning).
- `party_chat_prefix` (default `!p`) — type `!p hello` to speak in party chat only.
- Public API: `SpawnPartyEvents.AFTER_FIRST_ORIGIN_PLACEMENT`, `SpawnPartyAPI.isHolding` / `hasCompletedFirstPlacement`.
- `SpawnSetupExtension` + `SetupMenuHost` + locked `setupExtras` on parties; chunk wait while locating.
- Copy-paste setup extension API in README (`setSlot` / `fillFiller` / session extras / locate setters).
- Brown party color (dye swatch + `TextColor` RGB, not a vanilla `ChatFormatting` name).
- Ocean / water-surface party origins (no longer solid-land only).
- `/sp` alias for `/spawnparty` (including `/sp admin`).
- `SpawnSetupExtension.forwardBlocked` and `iconForLockedExtra`. Party creation asks for extension options before the party exists. After the first spawn those choices are read-only in the party menu.

### Fixed

- Opening the party menu no longer cosmetically wipes the hotbar (menu binds the real inventory read-only).
- Creating a party mid-game no longer teleports; only first-join hold exit places the player.
- Mid-game join preserves bed/anchor spawns (`forced=false`).
- First-join chest GUI clicks work: hold uses adventure + flight above build height (looking straight down) instead of spectator, which vanilla blocks for container clicks.
- Chat name/tag/password prompts keep the GUI closed (hold reopen skips while a prompt is pending).
- Party color pick uses a brush + 16 chat colors plus brown in the chest grid (no more short cycle list).
- Confirm Create no longer `closeContainer` before setup pages; missing extension pages fail back to Create; Escape on setup/status chests reopens the same page.
- Replacing the hold chest (Confirm Create → setup pages) no longer re-opens the previous filler chest mid-`openMenu`; `SetupPageFiller` is the extension fill hook.
- First-join Create/Join buttons are centered (Join was the only centered slot).
- Locate wait uses a clock; the chest closes when the player is placed at origin.
- Party data is stored per world (`data/spawnparty/`), not in global config (legacy `config/spawnparty-parties.json` migrates into the world on first load).
- Waterworld soft-bridge: Spawn Party calls Waterworld after first-origin placement (reliable); world-spawn island skipped when Spawn Party is present.
- `max_party_size=1` solo mode: no create/join name form or prefixes; other mods may still show setup pages; origin locate waits on chunks.

### Changed

- Party chat is prefix-based only; channel toggle (`/spawnparty chat`, `/pchat`, exact-match triggers) is removed.
- Global chat is no longer labeled `[Global]`; only party lines use `[Party]`.
- Config: `party_chat_commands` / `party_chat_triggers` replaced by `party_chat_prefix` (legacy triggers still load once if the new key is missing).
- Jar version string includes loader: `spawnparty-26.3-fabric-1.0.0-beta.1.jar`.
- Biome sampling uses the 26.3 biome resolver, so config and other mods' biome ids still work.
- First-join locate uses the same village probe as admin relocate: a village within 200 blocks of the candidate, otherwise the candidate. A world with no villages, including any overworld while Waterworld is installed, keeps the normal origin.
- First-join hold is no longer spectator at spawn+8.
- Party spawn locate samples biome noise first (respects `party_spawn_distance`), then generates a small chunk square instead of large `findClosestBiome3d` scans.
- `spawn_biome` is the SP-only fallback; extension biome/sampleY win when set. Distance stays Spawn Party's job.
- Party and admin chests are 9×6. Back is the lower-left corner and Forward (or Next page) is the lower-right. Choice clicks no longer advance setup pages; only Forward does. Back on the locate clock cancels the search before the party is created.

### Initial

- Server-side party create/join chest menus, shared origin spawns, distance enforcement.
- Party chat, chat/tab prefixes, OP admin GUI with pagination.
- Persistent `spawnparty.properties` + `spawnparty-parties.json`.
