# Spawn Party

Multiplayer, but make it a party. Shared spawn origins, chest menus with vanilla item icons, light social tools. Server-side — clients do not need the mod installed.


## Features

- **Party origins** — Create or join a party; your personal spawn origin becomes that party's pad (stand-in for world spawn). Beds and death work like vanilla; no bed means party origin, including End return.
- **Spread-out spawns** — Configurable minimum distance between different parties' origins (default 3000).
- **Biome / village targeting** — Prefer villages in a biome, fall back to the closest village to that biome, or free-roam safe spots.
- **First-join menu** — New players are held in spectator at world spawn with a chest GUI until they create or join a party.
- **Open / closed / password parties** — Leaders manage access from the menu.
- **Party chat** — Toggle Party ↔ Global with `/pchat` (action bar confirms the channel).
- **Chat & tab prefixes** — Optional `[TAG]` or `[Name]` on global chat and the tab list.
- **OP admin GUI** — Paginated list/edit/delete/relocate for moderation (`/spawnparty admin`).

All settings live in `config/spawnparty.properties`. Party data is in `config/spawnparty-parties.json`.


## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 26.2+.
2. Drop the jar in the server (or singleplayer) `mods/` folder with Fabric API.
3. Clients do **not** need the mod.


## Commands

| Command | Who | Effect |
|---------|-----|--------|
| `/spawnparty` or `/sp` | Players | Open party menu |
| `/spawnparty admin` | OP (level 2+) | Admin party browser |
| `/pchat` or `/spawnparty chat` | Players | Toggle Party / Global chat |


## Configuration

Config file: `config/spawnparty.properties` (created on first launch).

### Party Spawn

| Key | Default | Effect |
|-----|---------|--------|
| `party_spawn_distance` | `3000` | Minimum blocks between different parties' spawn origins |
| `spawn_biome` | *(empty)* | Optional biome id (e.g. `minecraft:plains`). Empty = any biome |
| `prefer_village` | `true` | Prefer villages per matrix below |

**Spawn matrix**

| `spawn_biome` | `prefer_village` | Behavior |
|---------------|------------------|----------|
| set | true | Village **in** that biome; else **closest village** to that biome |
| set | false | Safe surface in that biome |
| empty | true | Closest village to world spawn |
| empty | false | Safe spot searching outward from world spawn |

### Chat & Tab

| Key | Default | Effect |
|-----|---------|--------|
| `party_chat_enabled` | `true` | Allow Party/Global channel mode |
| `global_chat_prefix` | `true` | Prefix party tag or name on global chat |
| `chat_prefix_mode` | `tag` | `tag` or `name` |
| `tab_list_prefix` | `true` | Same prefix on the tab list |

### Holding

| Key | Default | Effect |
|-----|---------|--------|
| `holding_yaw_toward_sunrise` | `true` | Spectator look toward +X at world spawn |

Compatible with common Fabric chat mods in spirit: keep surfaces small. If another mod also prefixes names, disable one side to avoid doubles.


## License

[CC0 1.0 Universal](LICENSE) — public domain dedication.
