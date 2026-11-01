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

There are 180 enabled catalog tasks across Hunter, Angler, Wizard, Gathering, Crafting, and Challenges: 30 per guild through tier 5, including 45 timed tasks. Timers count online play, pause offline, and stop when the objectives are complete. All 12 tier-5 finales are one-time per player. New vanilla villages prioritize one path-connected Task Board pavilion, with duplicates prevented. Porter, Exploration and additional integrations remain planned. Existing villages are not retrofitted; modified village pools and every possible terrain slope are outside the placement checks.

## Configuration and commands

On first launch, editable definitions are generated in `config/taskbound/data/`. Every default task includes a plain-text `title` and `description` beside its objectives; the original `display_text` fallback remains supported for 1.0 templates and saved tasks. Use the [content example](#adding-and-editing-content) below to add tasks, givers and rewards. World datapacks override config definitions; accepted tasks keep their saved objectives and payouts. Keep `.initialized` to preserve deliberate deletions. Version 1.0.0 establishes the compatibility baseline; earlier development formats are unsupported. Keep released IDs and saved-data formats stable.

Defaults in the server-owned `taskbound-server.toml`:

| Setting | Default |
| --- | --- |
| `tasks.maximum_active_tasks` | `3`, including completed tasks awaiting claim |
| `tasks.offer_refresh_ticks` | `6000` game ticks / five minutes |
| `tasks.repeat_cooldown_ticks` | `24000` game ticks / twenty minutes after claiming |
| `rewards.use_numismatics_rewards` | `true` |
| `rewards.numismatics_spurs_per_emerald` | `1` |
| `rewards.use_custom_currency_rewards` | `false` |
| `rewards.custom_currency_item` | `minecraft:emerald` |

`/taskbound` is restricted to operator permission level 2 and contains only debugging/administration commands. Players accept, claim and abandon tasks at a Task Board; the journal shows their tasks and reputation. Use `/reload` after editing definitions and check the server log for validation errors; invalid reloads retain the previous valid catalog. List density is saved locally in `config/taskbound-client.toml`; gameplay state stays on the server.

## Compatibility

Create: Numismatics is optional. When installed and enabled, new emerald payouts become spurs at **1 emerald = 1 spur** by default. Enable `rewards.use_custom_currency_rewards` and set `rewards.custom_currency_item`, for example to `minecraft:diamond`, to use one custom item per emerald instead. A valid custom item takes priority over Numismatics; invalid, missing or air items fall back to automatic currency. Other rewards, fixed level gifts and generated/accepted task snapshots retain their items and quantities. The Rewards heading follows the current configuration; individual payment icons and sorting recognize the saved currency.

Optional mods are neither required nor bundled. Ars Nouveau and Starcatcher adapters exist, but their deferred task pools are not enabled by default. Currency payout and client checks have run with real Create 6.0.10 and Numismatics 1.1.0; arbitrary modpacks and the full gameplay test suite with Create are not established by those checks.

## Default configs

The [default_configs](default_configs/README.md) folder contains a clean reference copy of all default tasks, givers, categories, rewards and level gifts, plus the client/server TOML defaults, in the layout used under the Minecraft game directory. Follow its restore instructions to replace an edited configuration safely. Resetting these files does not reset player reputation or accepted tasks.

## Adding and editing content

Back up `config/taskbound` before editing. Definitions live in `config/taskbound/data/<namespace>/`; shipped examples are under [taskbound_defaults](src/main/resources/taskbound_defaults/data/taskbound). A world datapack can provide the same `data/<namespace>/` paths and override config definitions by ID. Keep the filename/path and JSON `id` aligned, update references together, and run `/reload` after saving a complete change.

- Tasks: edit `task_templates/` for literal `title`/`description`, objective parameters, amounts and difficulty. Keep their descriptions consistent with their objectives. Optional `time_limit_ticks` is a positive integer, at 1200 ticks per minute; omit it for untimed tasks. Keep `display_text` for the 1.0 fallback.
- Givers: `task_givers/` lists category IDs and reward pool IDs. Each `task_categories/` entry lists its templates. To remove a task, remove its category reference and template file; to hide a giver, set `enabled` to `false`. Removing a giver file safely omits its associated definitions while retaining accepted tasks and banked reputation. Check overriding datapacks too.
- Rewards: `rewards/` entries sharing `pool_id` form a pool. Selection uses difficulty times the giver's `reward_multiplier` as its budget. `cost` spends that budget; `payload.count` is the actual item quantity. `level_rewards/` supplies fixed intermediate-level gifts separately.

Accepted tasks retain their saved text, requirements and payouts. Editing defaults does not rewrite them. Keep `.initialized` to preserve deliberate deletions. New JARs also preserve existing config definitions; deliberately merge new defaults or use a backed-up, fresh definition folder to adopt an updated catalog. Removing only `.initialized` does not replace older category/giver lists.

<details>
<summary>Complete Village Watch example: five zombies, two emeralds, optional level gifts</summary>

Create the four required files below, then reload. The optional fifth file adds gifts at levels 2 and 3. These exact examples are parsed and generated by the native test suite. A custom currency setting can convert the example's emerald payment.

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
