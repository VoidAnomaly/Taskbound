# Taskbound

By **voidanomaly**.

Taskbound adds guild tasks, reputation, and rewards for ordinary Minecraft gameplay. Browse offers at a Task Board, accept a task, perform its objectives, and claim the saved reward at a nearby board. The Task Journal keeps your active tasks and guild reputation available while travelling.

## Requirements and play

- Minecraft **1.21.1**, NeoForge **21.1.242 or newer**, and Java **21**. Install Taskbound on both the server and connecting clients.
- Players receive one journal on their first login; replacements use a shapeless recipe with **one book, one feather, and one compass**.
- The board's **Tasks**, **Reputation**, and **Active** tabs show offers, progression, and accepted tasks. Sneak-use changes its default guild. In the journal, click a task to read its details on the right, or a guild to read its ranks, unlocks and level gifts on the left. Use Active Tasks to return to the task list. The journal is read-only; claim rewards at a board.
- For hand-in tasks, finish the other objectives and select **Hand in** at the board. Place your chosen stacks into the requested slots, then **Confirm**. Cancelling or closing returns the selection.
- Offers rotate every five minutes of server game time. Accepted tasks retain their progress and payouts. There are three active-task slots by default.
- Each intermediate reputation level adds an available offer and a separately claimable gift. Rank tiers unlock at levels 1, 5, 10, 15, and 20.

There are **210 selected tasks across seven guilds**, 30 per guild through tier 5, including **14 one-time finales**. The normal catalog has **47 timed tasks**; installing Starcatcher replaces the five timed vanilla fishing tasks with untimed Starcatcher tasks, giving **42 timed tasks**. Farmer's Delight replaces 15 Farmer tasks without changing these totals. Timers count online play, pause offline, and stop when objectives are complete.

New vanilla villages prioritize one path-connected Task Board pavilion, with duplicates prevented. Standalone pavilions also generate on dry Nether cave floors and the End's outer islands. Nether pavilions use polished basalt, Nether brick, blackstone and a soul lantern; End pavilions use purpur pillars, End stone, End stone brick and an end rod. Random placement spacing is **23 chunks in the Nether** and **85 in the End**, compared with vanilla villages at 34 chunks. Suitable terrain and biomes determine actual distances. Only new chunks receive pavilions; existing terrain is not retrofitted.

**Farmer**, shown with an iron hoe, replaces Gathering's mining focus with crops, food and ranching while retaining its existing guild ID and reputation. **Porter** adds assigned deliveries, remote collections, receipts, ordered circuits, fragile cargo and portal routes. Nearby boards become destinations automatically as the server loads them; you do not need to open them first. Routes use their stated horizontal distance limits: starter legs are 256–768 blocks. The search uses saved board locations and does not generate unexplored chunks. Cross-dimension offers also require a previous ordinary-portal visit to the destination dimension. Complete a Porter route by interacting with its assigned final board; that board pays automatically.

Accepting a Porter task gives you a temporary **Delivery Compass** with a wood-and-brass face pointing to the next assigned board. It updates after each stop and disappears when the task completes, fails or is abandoned. It spins while you are outside its target dimension. Keep or recover the original guide; lost guides are not automatically replaced. With Xaero's Minimap installed, the guide also supplies a temporary destination waypoint/beacon; World Map alone uses the compass fallback. The compass is always provided, and only Taskbound's own waypoint and guide are removed.

Parcels look like taped cardboard packages with varied warm colors. Their postage stamps show where that parcel must be delivered: **green for the Overworld, red for the Nether, purple for the End**. The tooltip shows the delivery board's **X and Z coordinates**, without height. Cardboard colors stay consistent for each parcel and do not change its requirements or rewards.

## Configuration and commands

On first launch, editable definitions are generated in `config/taskbound/data/`. Every default task includes a plain-text `title` and `description` beside its objectives; the original `display_text` fallback remains supported for 1.0 templates and saved tasks. Use the [content example](#adding-and-editing-content) below to add tasks, givers and rewards. World datapacks override config definitions; accepted tasks keep their saved objectives and payouts. Keep `.initialized` to preserve deliberate deletions. Version 1.0.0 establishes the compatibility baseline; earlier development formats are unsupported. Keep released IDs and saved-data formats stable.

Defaults in the server-owned `taskbound-server.toml`:

| Setting | Default |
| --- | --- |
| `tasks.maximum_active_tasks` | `3`, including completed tasks awaiting claim |
| `tasks.offer_refresh_ticks` | `6000` game ticks / five minutes |
| `tasks.repeat_cooldown_ticks` | `24000` game ticks / twenty minutes after claiming |
| `tasks.porter_allow_teleportation` | `false`; Walking Post still requires travel on foot |
| `rewards.use_numismatics_rewards` | `true` |
| `rewards.numismatics_spurs_per_emerald` | `1` |
| `rewards.use_custom_currency_rewards` | `false` |
| `rewards.custom_currency_item` | `minecraft:emerald` |

`/taskbound` is restricted to operator permission level 2 and contains only debugging/administration commands. Players accept, claim and abandon tasks at a Task Board; the journal shows their tasks and reputation. Use `/reload` after editing definitions and check the server log for validation errors; invalid reloads retain the previous valid catalog. List density is saved locally in `config/taskbound-client.toml`; gameplay state stays on the server. Set `porter_xaero_waypoints = false` under `[ui]` in that client file to disable automatic Xaero destination markers; it defaults to `true` and does not disable the compass.

## Compatibility

Since 1.1.0, `kill_entity` and `tracked_kill` objectives can require an exact `required_damage_type` or a `required_damage_type_tag`, such as `minecraft:is_explosion`. Both fields together require both matches. These are damage registry IDs/tags, not weapon item IDs; tag values omit the leading `#`. Existing tasks without these fields retain their requirements.

Modded kills can use the damage source's player or the retained owner of its projectile/TNT. Weapon requirements use the weapon reported by the source, including custom projectiles and beams, without substituting the player's currently held item for missing information. Ownerless damage cannot receive personal task credit. Some Orbital Railgun: Renewed and Project: Explosive damage paths omit attribution, so full compatibility with those mods is not established. See the [damage requirements wiki](https://github.com/VoidAnomaly/Taskbound/wiki/Objective-Reference#damage-requirements-110) for examples and limits.

Create: Numismatics is optional. When installed and enabled, new emerald payouts become spurs at **1 emerald = 1 spur** by default. Enable `rewards.use_custom_currency_rewards` and set `rewards.custom_currency_item`, for example to `minecraft:diamond`, to use one custom item per emerald instead. A valid custom item takes priority over Numismatics; invalid, missing or air items fall back to automatic currency. Other rewards, fixed level gifts and generated/accepted task snapshots retain their items and quantities. The Rewards heading follows the current configuration; individual payment icons and sorting recognize the saved currency.

Starcatcher replaces the entire vanilla Angler offer list with 30 tasks built around its fishing minigame, species, habitats, rarity, weight and perfect catches. All Starcatcher tasks are untimed. Farmer's Delight replaces 15 of Farmer's 30 tasks with crops, cutting-board work and freshly prepared food. The other 15 Farmer tasks remain available. Both integrations are optional; Farmer and Porter also have full vanilla-compatible catalogs. Existing accepted tasks retain their original requirements.

Optional mods are not bundled. The gameplay integrations target Starcatcher 3.3.10 and Farmer's Delight 1.3.4 for NeoForge 1.21.1. The optional waypoint adapter targets Xaero's Minimap 26.6.0, with World Map 1.47.0 when both are present; World Map alone does not supply waypoint beacons. Porter detects standard player teleport APIs, including direct dimension transfers; a mod that bypasses those APIs may need an additional adapter. Waystones compatibility is not established.

## Default configs

The [default_configs](default_configs/README.md) folder contains a clean reference copy of all default tasks, givers, categories, rewards and level gifts, plus the client/server TOML defaults, in the layout used under the Minecraft game directory. Follow its restore instructions to replace an edited configuration safely. Resetting these files does not reset player reputation or accepted tasks.

## Adding and editing content

Back up `config/taskbound` before editing. Definitions live in `config/taskbound/data/<namespace>/`; shipped examples are under [taskbound_defaults](src/main/resources/taskbound_defaults/data/taskbound). A world datapack can provide the same `data/<namespace>/` paths and override config definitions by ID. Keep the filename/path and JSON `id` aligned, update references together, and run `/reload` after saving a complete change.

- Tasks: edit `task_templates/` for literal `title`/`description`, objective parameters, amounts and difficulty. Keep their descriptions consistent with their objectives. Optional `time_limit_ticks` is a positive integer, at 1200 ticks per minute; omit it for untimed tasks. Keep `display_text` for the 1.0 fallback.
- Givers: `task_givers/` lists category IDs and reward pool IDs. Each `task_categories/` entry lists its templates. To remove a task, remove its category reference and template file; to hide a giver, set `enabled` to `false`. Removing a giver file safely omits its associated definitions while retaining accepted tasks and banked reputation. Check overriding datapacks too.
- Rewards: `rewards/` entries sharing `pool_id` form a pool. Selection normally uses difficulty times the giver's `reward_multiplier` as its budget. New Farmer, Starcatcher and Porter templates use variant `conditions.currency_reward` for an explicit currency payout instead; Porter can add `currency_per_1024_blocks` for generated route distance. `level_rewards/` supplies fixed intermediate-level gifts separately.

Version 1.1.0 applies its default-content update once. It adds the new files and replaces only files that are byte-for-byte original released defaults; replaced originals are backed up under `config/taskbound/.updates/1.1/`. Edited files and deliberate deletions are preserved. Keep both `.initialized` and `.defaults-1.1` afterward. Customized giver/category lists need a manual merge with `default_configs/` to expose the new catalogs. Accepted tasks, reputation and claimed rewards remain saved. The stock Farmer icon is updated to an iron hoe with its original backed up under `.updates/farmer-icon/`; customized giver files keep their chosen icon.

<details>
<summary>Complete Village Watch example: five zombies, two emeralds, optional level gifts</summary>

Create the four required files below, then reload. The optional fifth file adds gifts at levels 2 and 3. A custom currency setting can convert the example's emerald payment.

`config/taskbound/data/mytasks/task_givers/watch.json`:

```json
{
  "id": "mytasks:watch",
  "display_name": "Village Watch",
  "reputation": {
    "thresholds": {
      "1": 0,
      "2": 10,
      "3": 25,
      "4": 45,
      "5": 70,
      "6": 100,
      "7": 135,
      "8": 175,
      "9": 220,
      "10": 270,
      "11": 325,
      "12": 385,
      "13": 450,
      "14": 520,
      "15": 595,
      "16": 675,
      "17": 760,
      "18": 850,
      "19": 945,
      "20": 1045
    }
  },
  "rank_names": {
    "1": "Recruit",
    "5": "Patroller",
    "10": "Guardian",
    "15": "Captain",
    "20": "Commander"
  },
  "category_ids": ["mytasks:watch/starter"],
  "reward_pool_ids": ["mytasks:watch"],
  "reward_multiplier": 1.0,
  "enabled": true,
  "icon_item": "minecraft:shield"
}
```

`config/taskbound/data/mytasks/task_categories/watch/starter.json`:

```json
{
  "id": "mytasks:watch/starter",
  "giver_id": "mytasks:watch",
  "minimum_reputation_level": 1,
  "maximum_reputation_level": null,
  "base_weight": 5.0,
  "soft_boost_after": 3,
  "recency_boost_per_miss": 0.2,
  "guarantee_after": 8,
  "template_ids": ["mytasks:watch/zombie_patrol"],
  "required_capabilities": [],
  "new_unlock_weight_multiplier": 2.0,
  "new_unlock_boost_draws": 3
}
```

`config/taskbound/data/mytasks/task_templates/watch/zombie_patrol.json`:

```json
{
  "id": "mytasks:watch/zombie_patrol",
  "category_id": "mytasks:watch/starter",
  "minimum_reputation_level": 1,
  "maximum_reputation_level": null,
  "base_weight": 10.0,
  "base_difficulty": 6,
  "display_text": "Defeat five zombies to help protect the village.",
  "title": "Zombie Patrol",
  "description": "Defeat 5 zombies to help protect the village.",
  "required_capabilities": ["minecraft:kill_entity"],
  "variants": [
    {
      "id": "mytasks:watch/zombie_patrol/standard",
      "weight": 1.0,
      "objectives": [
        {
          "type": "kill_entity",
          "parameters": {
            "required_count": 5,
            "entity": "minecraft:zombie"
          },
          "difficulty_components": [],
          "required_capabilities": ["minecraft:kill_entity"]
        }
      ],
      "conditions": {
        "source_catalog_id": "mytasks:watch/zombie_patrol"
      },
      "difficulty_components": [],
      "required_capabilities": []
    }
  ]
}
```

`config/taskbound/data/mytasks/rewards/watch/emeralds.json`:

```json
{
  "id": "mytasks:watch/emeralds",
  "type": "ITEM",
  "pool_id": "mytasks:watch",
  "weight": 1.0,
  "cost": 6,
  "minimum_difficulty": 0,
  "maximum_difficulty": null,
  "minimum_reputation_level": null,
  "maximum_count": 1,
  "giver_restrictions": ["mytasks:watch"],
  "rarity_group": "common",
  "tangible": true,
  "payload": {
    "item": "minecraft:emerald",
    "count": 2
  }
}
```

`config/taskbound/data/mytasks/level_rewards/watch.json`:

```json
{
  "id": "mytasks:watch",
  "rewards": [
    {
      "level": 2,
      "items": [
        {"item": "minecraft:arrow", "count": 4}
      ]
    },
    {
      "level": 3,
      "items": [
        {"item": "minecraft:bread", "count": 2}
      ]
    }
  ]
}
```

</details>

## License and artwork

[CC BY-NC 4.0](LICENSE) (Creative Commons Attribution-NonCommercial 4.0 International), copyright **voidanomaly**. You may use, modify and redistribute Taskbound for noncommercial purposes. When sharing it or modified versions, credit **voidanomaly**, include the license or its [link](https://creativecommons.org/licenses/by-nc/4.0/), and indicate any changes. Commercial use, including selling or monetizing the mod, requires separate permission from voidanomaly. Task Board and Task Journal artwork is author-provided. Runtime GUI materials are committed PNGs; Minecraft item icons use the game's renderer rather than copied vanilla textures.

The supplied original pixel-art package granted the following permission for its newly authored font, symbols and build source; this does not relicense the rest of the project:

> These newly authored pixel assets, bitmap glyphs, vector shapes, and build source are provided for unrestricted use, modification, and redistribution in your mod or other projects. No attribution is required. The concept-reference images are not included in this package.
