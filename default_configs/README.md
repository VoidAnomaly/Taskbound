# Default Taskbound configuration

These are the unmodified defaults for this source revision of Taskbound 1.1.0. Paths below `config/` mirror the layout under the Minecraft instance or dedicated-server directory:

```text
config/
  taskbound-client.toml
  taskbound-server.toml
  taskbound/
    data/
      taskbound/
        task_givers/
        task_categories/
        task_templates/
        rewards/
        level_rewards/
```

There are **210 selected tasks across seven guilds**, 30 per guild through tier 5, including **14 one-time finales**. The normal catalog has **47 timed tasks**; installing Starcatcher replaces the five timed vanilla fishing tasks with untimed Starcatcher tasks, giving **42 timed tasks**. Farmer's Delight replaces 15 Farmer tasks without changing these totals. The reference contains 285 authored task templates, including the inactive alternatives and retained 1.0 Gathering tasks. Currency defaults to spurs when Numismatics is installed and enabled, otherwise emeralds. Custom item currency is disabled by default.

## Restore a clean configuration

1. Stop Minecraft or the server. Back up your existing `config/taskbound/` folder and Taskbound TOML files outside `config/taskbound/`.
2. Move the entire old `config/taskbound/` folder out of the config directory, then copy this reference's `config/taskbound/` into its place. Copying over the old folder alone leaves extra custom definitions behind.
3. If you also want to reset settings, replace `config/taskbound-server.toml` with the included server file. If your world uses `serverconfig/taskbound-server.toml`, use the server reference at that location too after backing it up. Replace `config/taskbound-client.toml` on each client only if you want to reset local display preferences. A dedicated server does not need the client file.
4. Start the game/server and inspect the log for definition errors. World datapacks can override these definitions; disable or update those overrides if you want only the shipped defaults.

The reference deliberately omits `.initialized`; the first start creates it after checking the seeded files. Keep that generated marker and `.defaults-1.1` afterward so deliberate deletions remain deleted. Do not copy a modified live configuration back into this reference.

This resets definition files and optional settings, not saved player reputation, claimed level gifts, completed-task history, or accepted-task snapshots. Already generated tasks retain their saved requirements and rewards. Current offers may also retain old snapshots until refreshed.

## Updating an existing installation

Version 1.1.0 applies its default-content update once. It adds the new files and replaces only files that are byte-for-byte original released defaults; replaced originals are backed up under `config/taskbound/.updates/1.1/`. Edited files and deliberate deletions are preserved. Keep both `.initialized` and `.defaults-1.1` afterward. Customized giver/category lists need a manual merge with `default_configs/` to expose the new catalogs. Accepted tasks, reputation and claimed rewards remain saved. The stock Farmer icon is updated to an iron hoe with its original backed up under `.updates/farmer-icon/`; customized giver files keep their chosen icon.

The JSON files here mirror the bundled [default definitions](../src/main/resources/taskbound_defaults/data/taskbound). Use them as a reference for a manual merge or the complete clean-reset procedure above.
