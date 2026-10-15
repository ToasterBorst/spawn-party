# Spawn Party

Multiplayer, but make it a party. Shared spawn origins, chest menus with vanilla item icons, light social tools. Server-side — clients do not need the mod installed.


## Features

- **Party origins** — Create or join a party; your personal spawn origin becomes that party's pad (stand-in for world spawn). Beds and death work like vanilla; no bed means party origin, including End return.
- **Spread-out spawns** — Configurable minimum distance between different parties' origins (default 3000). Dry land or ocean/water-surface pads are valid.
- **Max party size** — Optional hard cap (`max_party_size`; `0` = unlimited). **`1` = dispersed solo spawning** (no create/join name form or chat prefixes). Other mods can still show first-join setup pages; otherwise the player is held briefly while a spaced origin is found.
- **Biome / village targeting** — Candidates spread out from world spawn. An optional biome filter keeps only matching columns. When `prefer_village` is on, a village within 200 blocks of that candidate is used if it is far enough from other parties; otherwise the candidate stands. A world with no villages, including any overworld while Waterworld is installed, uses the normal origin.
- **First-join menu** — When `max_party_size` is not 1, new players are held in adventure mode (flight, invulnerable) above build height looking straight down, with a chest GUI until they create or join a party. Creating a party is a wizard: name and access, then any extra setup pages, then Forward. Spawn locate waits for chunks (no server freeze) and Back cancels it before the party exists. After the first spawn, those extra choices are locked in the party menu.
- **Open / closed / password parties** — Leaders manage access from the menu.
- **Party chat** — Prefix a line with a configurable shorthand (default `!p`) to speak to your party only, e.g. `!p meet at the river`.
- **Chat prompts** — Name / tag / password prompts close the chest GUI so you can type in chat (the first-join hold will not re-open the menu until you finish or cancel).
- **Color brush** — Create/Manage use a brush button; 16 chat colors plus brown appear as dyes in the chest grid.
- **Chat & tab prefixes** — Optional `[TAG]` or `[Name]` on chat and the tab list. Party-only lines are labeled `[Party]`.
- **OP admin GUI** — Paginated list/edit/delete/relocate for moderation (`/spawnparty admin` or `/sp admin`).
- **First-spawn API** — `AFTER_FIRST_ORIGIN_PLACEMENT` for starter kits; `SpawnSetupExtension` for extra first-origin pages, locate filters, and locked per-party extras. Waterworld is one integrator (`suggests` only); any mod can register.

All settings live in `config/spawnparty.properties`. Party membership and origins are stored **per world** in `saves/<world>/data/spawnparty/spawnparty-parties.json` (dedicated: world `data/spawnparty/`).


## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft **26.3**.
2. Drop `spawnparty-26.3-fabric-1.0.0-beta.1.jar` in the server (or singleplayer) `mods/` folder with Fabric API.
3. Clients do **not** need the mod.
4. Create a **new** world. Worlds from 26.2 are not a supported upgrade.

Artifact name includes the loader: `spawnparty-26.3-fabric-1.0.0-beta.1.jar`.


## Commands

| Command | Who | Effect |
|---------|-----|--------|
| `/spawnparty` or `/sp` | Players | Open party menu |
| `/spawnparty admin` or `/sp admin` | OP (level 2+) | Admin party browser |


## Configuration

Config file: `config/spawnparty.properties` (created on first launch).

### Party Spawn

| Key | Default | Effect |
|-----|---------|--------|
| `party_spawn_distance` | `3000` | Minimum blocks between different parties' spawn origins |
| `spawn_biome` | *(empty)* | Optional biome id (e.g. `minecraft:plains`). Empty = any biome. Used only when no setup extension set a biome; **distance is always Spawn Party's job**. |
| `prefer_village` | `true` | Look for a village within 200 blocks of a candidate (see matrix) |
| `max_party_size` | `0` | `0` = unlimited; `>=1` = hard cap. **`1` = solo dispersed mode**: no party menu, no prefixes; each new player auto-receives a spaced origin |

**Spawn matrix**

| `spawn_biome` | `prefer_village` | Behavior |
|---------------|------------------|----------|
| set | true | Candidate in that biome. If a village is within 200 blocks and far enough from other parties, use the village; otherwise the candidate |
| set | false | Safe surface at a candidate in that biome |
| empty | true | Candidate outward from world spawn. Village within 200 blocks if one exists and is far enough; otherwise the candidate |
| empty | false | Safe spot searching outward from world spawn |

This is a 200-block probe around the candidate, not a search for the closest village anywhere in the world. A world with no villages, including any overworld while Waterworld is installed (that mod replaces the village structure set with an empty one), uses the normal origin.

Ocean / water-surface origins are allowed (compatible with Waterworld raft starts).

When another mod registers a `SpawnSetupExtension` and sets biome / sample Y on `SpawnLocateRequest`, that wins. `spawn_biome` remains the Spawn Party-only fallback (SP-only servers, or extensions that leave biome empty). Spawn Party does not ship a biome picker of its own.

### Chat & Tab

| Key | Default | Effect |
|-----|---------|--------|
| `party_chat_enabled` | `true` | Allow party chat via `party_chat_prefix` |
| `party_chat_prefix` | `!p` | Shorthand to send a line to party chat only (e.g. `!p hello`). Comma-separated for aliases. Empty = off |
| `global_chat_prefix` | `true` | Prefix party tag or name on global chat |
| `chat_prefix_mode` | `tag` | `tag` or `name` |
| `tab_list_prefix` | `true` | Same prefix on the tab list |

Chat formats:

- Party: `[Party] [TAG] Name: msg`
- Global: `<[TAG] Name> msg` (no `[Global]` label)

### Holding

| Key | Default | Effect |
|-----|---------|--------|
| `holding_yaw_toward_sunrise` | `true` | Hold yaw toward +X (sunrise); pitch is always straight down |

First-join hold uses **adventure + flight** above the dimension build limit (not spectator — vanilla blocks chest clicks in spectator). The first chest centers Create / Join. Locate wait shows a **clock** and closes when you spawn. The chest window title is e.g. **Spawn Party**; vanilla also draws a secondary **Inventory** label above the lower player-inventory rows.

Compatible with common Fabric chat mods in spirit: keep surfaces small. If another mod also prefixes names, disable one side to avoid doubles.


## Integrating first-join rewards

Fabric `JOIN` listeners run while Spawn Party still holds the player above the world (or, in solo mode, before auto-placement finishes). `PLAY_TIME` keeps ticking during a hold, so kits that key off `JOIN` + `PLAY_TIME == 0` either fire at the wrong place or never again.

**Do this instead:**

1. Soft-detect Spawn Party (`FabricLoader.isModLoaded("spawnparty")`).
2. Skip location-sensitive JOIN kits while `SpawnPartyAPI.isHolding(uuid)` **or** until `SpawnPartyAPI.hasCompletedFirstPlacement(uuid)`.
3. Listen to `SpawnPartyEvents.AFTER_FIRST_ORIGIN_PLACEMENT` and grant kits there (player, party, level, origin, yaw, pitch).
4. Optional: implement `SpawnSetupExtension` and `SpawnPartyAPI.registerSetupExtension(...)`. See **Setup extension API** below.

`contributeLocate` is independent of `wantsSelection`: a config-only biome still filters locate.

Solo (`max_party_size=1`): if any extension wants selection, the player is held and shown those pages (no party name form). The first page has no Back. Forward on the last page places them as a 1-person party. If none want a page, they are held only while origin chunks generate.

Spawn Party does **not** depend on Waterworld. Waterworld optionally soft-bridges (`suggests` only).


## Setup extension API

Register from your mod init (soft-detect with `FabricLoader.isModLoaded("spawnparty")` if you do not compile against Spawn Party). Distance between origins is Spawn Party's job. Biome and sample Y come from extensions when present.

`SetupMenuHost` opens a 9×6 chest. Prefer `SetupPageFiller` (or `SpawnPartyAPI.openSetupPage`) over `Consumer<SetupPage>`. It already fills gray glass; `fillFiller()` clears slots **and** click handlers. Content is slots 0–44. While a wizard is active the host paints **Back** (`NAV_BACK` 45, lower left) and **Forward** (`NAV_CONFIRM` 53, lower right) after your fill and overwrites those slots. `NAV_PREV` 47, `NAV_INFO` 49, and `NAV_NEXT` 51 stay free for your own controls. Choice clicks must `session.put` the value and may reopen the page to show it. They must not advance the wizard. Calling `done` does not change pages. Return a message from `forwardBlocked` to keep the player here until a choice is stored; return null to allow Forward. Forward is **Next** until the last page, then **Create Party** (or **Continue** in solo). The party is created only after that last Forward, once a spawn is found. Back on the locate clock cancels the search and returns to the last page. Setup/status chests do not close into empty hold (Escape reopens the same page). Do not call `done` if a page failed to open.

```java
package mymod.compat;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import spawnparty.api.SetupMenuHost;
import spawnparty.api.SpawnLocateRequest;
import spawnparty.api.SpawnPartyAPI;
import spawnparty.api.SpawnSetupExtension;
import spawnparty.api.SpawnSetupSession;

public final class ExampleSetup implements SpawnSetupExtension {

	private static final Identifier ID = Identifier.fromNamespaceAndPath("mymod", "spawn_setup");
	private static final Identifier EXTRA = Identifier.fromNamespaceAndPath("mymod", "mode");

	public static void register() {
		SpawnPartyAPI.registerSetupExtension(new ExampleSetup());
	}

	@Override
	public Identifier id() {
		return ID;
	}

	@Override
	public boolean wantsSelection(ServerPlayer player) {
		return true;
	}

	@Override
	public void openSelection(ServerPlayer player, SpawnSetupSession session, Runnable done) {
		String picked = session.get(EXTRA).orElse("");
		SetupMenuHost.open(player, Component.literal("Example Setup"), page -> {
			page.setSlot(11, named(Items.COMPASS, "Plains" + ("plains".equals(picked) ? " (selected)" : "")), () -> {
				session.put(EXTRA, "plains");
				openSelection(player, session, done);
			});
			page.setSlot(15, named(Items.CLOCK, "Night" + ("night".equals(picked) ? " (selected)" : "")), () -> {
				session.put(EXTRA, "night");
				openSelection(player, session, done);
			});
		});
	}

	@Override
	public Component forwardBlocked(ServerPlayer player, SpawnSetupSession session) {
		if (session.get(EXTRA).isEmpty()) {
			return Component.literal("Pick a mode first.");
		}
		return null;
	}

	@Override
	public ItemStack iconForLockedExtra(spawnparty.party.Party party, String key, String value) {
		if (!EXTRA.toString().equals(key)) return null;
		return named(Items.COMPASS, "Mode: " + value);
	}

	@Override
	public void contributeLocate(SpawnSetupSession session, SpawnLocateRequest request) {
		request.setPreferVillage(false);
		request.setPrepareChunkRadius(2);
		// request.setBiome(Identifier.parse("minecraft:plains"));
		// request.setSampleY(63);
	}

	@Override
	public void onPartyLocked(ServerPlayer player, spawnparty.party.Party party, SpawnSetupSession session) {
		// Frozen extras: SpawnPartyAPI.getPartyExtras(party.id)
	}

	private static ItemStack named(net.minecraft.world.item.Item item, String name) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
		return stack;
	}
}
```

Read locked extras later with `SpawnPartyAPI.getPartyExtras(partyId)` or `extrasForMember(playerId)`. The party menu lists them after the first spawn; clicks do not edit them. `iconForLockedExtra` can replace the default paper icon. Hold/reopen: while `SpawnPartyAPI.isHolding(uuid)`, setup pages stay up; do not `closeContainer` before `SetupMenuHost.open` (the new menu replaces the current chest).

`contributeLocate` merge: first biome wins; any `preferVillage=false` wins; max prepare radius; standing predicates AND.


## License

[CC0 1.0 Universal](LICENSE) — public domain dedication.
