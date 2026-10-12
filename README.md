# Spawn Party

Multiplayer, but make it a party. Shared spawn origins, chest menus with vanilla item icons, light social tools. Server-side — clients do not need the mod installed.


## Features

- **Party origins** — Create or join a party; your personal spawn origin becomes that party's pad (stand-in for world spawn). Beds and death work like vanilla; no bed means party origin, including End return.
- **Spread-out spawns** — Configurable minimum distance between different parties' origins (default 3000). Dry land or ocean/water-surface pads are valid.
- **Max party size** — Optional hard cap (`max_party_size`; `0` = unlimited). **`1` = dispersed solo spawning** (no create/join name form or chat prefixes). Other mods can still show first-join setup pages; otherwise the player is held briefly while a spaced origin is found.
- **Biome / village targeting** — Prefer villages in a biome, fall back to the closest village to that biome, or free-roam safe spots.
- **First-join menu** — When `max_party_size` is not 1, new players are held in adventure mode (flight, invulnerable) above build height looking straight down, with a chest GUI until they create or join a party. Spawn locate waits for chunks (no server freeze). Other mods can add extra setup pages via `SpawnSetupExtension`.
- **Open / closed / password parties** — Leaders manage access from the menu.
- **Party chat** — Prefix a line with a configurable shorthand (default `!p`) to speak to your party only, e.g. `!p meet at the river`.
- **Chat prompts** — Name / tag / password prompts close the chest GUI so you can type in chat (the first-join hold will not re-open the menu until you finish or cancel).
- **Color brush** — Create/Manage use a brush button; 16 chat colors plus brown appear as dyes in the chest grid.
- **Chat & tab prefixes** — Optional `[TAG]` or `[Name]` on chat and the tab list. Party-only lines are labeled `[Party]`.
- **OP admin GUI** — Paginated list/edit/delete/relocate for moderation (`/spawnparty admin` or `/sp admin`).
- **First-spawn API** — `AFTER_FIRST_ORIGIN_PLACEMENT` for starter kits; `SpawnSetupExtension` for extra first-origin pages, locate filters, and locked per-party extras. Waterworld is one integrator (`suggests` only); any mod can register.

All settings live in `config/spawnparty.properties`. Party membership and origins are stored **per world** in `saves/<world>/data/spawnparty/spawnparty-parties.json` (dedicated: world `data/spawnparty/`).


## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 26.2+.
2. Drop the jar in the server (or singleplayer) `mods/` folder with Fabric API.
3. Clients do **not** need the mod.

Artifact name includes the loader: `spawnparty-26.2-fabric-1.0.0.jar`.


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
| `prefer_village` | `true` | Prefer villages per matrix below |
| `max_party_size` | `0` | `0` = unlimited; `>=1` = hard cap. **`1` = solo dispersed mode**: no party menu, no prefixes; each new player auto-receives a spaced origin |

**Spawn matrix**

| `spawn_biome` | `prefer_village` | Behavior |
|---------------|------------------|----------|
| set | true | Village **in** that biome; else **closest village** to that biome |
| set | false | Safe surface in that biome |
| empty | true | Closest village to world spawn |
| empty | false | Safe spot searching outward from world spawn |

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

Solo (`max_party_size=1`): if any extension wants selection, the player is held and shown those pages (no party name form), then auto-placed as a 1-person party. If none do, they are held only while origin chunks generate.

Spawn Party does **not** depend on Waterworld. Waterworld optionally soft-bridges (`suggests` only).


## Setup extension API

Register from your mod init (soft-detect with `FabricLoader.isModLoaded("spawnparty")` if you do not compile against Spawn Party). Distance between origins is Spawn Party's job. Biome and sample Y come from extensions when present.

`SetupMenuHost` opens a 9×6 chest. Prefer `SetupPageFiller` (or `SpawnPartyAPI.openSetupPage`) over `Consumer<SetupPage>`. It already fills gray glass; `fillFiller()` clears slots **and** click handlers. Bottom row: `NAV_BACK` 45, `NAV_PREV` 47, `NAV_INFO` 49, `NAV_NEXT` 51, `NAV_CONFIRM` 53. Content is slots 0–44. Setup/status chests do not close into empty hold (Escape reopens the same page). Call `done` once when finished; do not call `done` if a page failed to open.

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
		SetupMenuHost.open(player, Component.literal("Example Setup"), page -> {
			page.setSlot(13, named(Items.COMPASS, "Continue"), () -> {
				session.put(EXTRA, "ok");
				done.run();
			});
		});
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

Read locked extras later with `SpawnPartyAPI.getPartyExtras(partyId)` or `extrasForMember(playerId)`. Hold/reopen: while `SpawnPartyAPI.isHolding(uuid)`, setup pages stay up; do not `closeContainer` before `SetupMenuHost.open` (the new menu replaces the current chest).

`contributeLocate` merge: first biome wins; any `preferVillage=false` wins; max prepare radius; standing predicates AND.


## License

[CC0 1.0 Universal](LICENSE) — public domain dedication.
