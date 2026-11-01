# Default Taskbound configuration

These are the unmodified defaults for this source revision of Taskbound 1.0.0. Paths below `config/` mirror the layout under the Minecraft instance or dedicated-server directory:

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

There are 259 JSON definitions, including 180 tasks and 45 timed tasks. Currency defaults to spurs when Numismatics is installed and enabled, otherwise emeralds. Custom item currency is disabled by default.

## Restore a clean configuration

1. Stop Minecraft or the server. Back up your existing `config/taskbound/` folder and Taskbound TOML files outside `config/taskbound/`.
2. Move the entire old `config/taskbound/` folder out of the config directory, then copy this reference's `config/taskbound/` into its place. Copying over the old folder alone leaves extra custom definitions behind.
3. If you also want to reset settings, replace `config/taskbound-server.toml` with the included server file. If your world uses `serverconfig/taskbound-server.toml`, use the server reference at that location too after backing it up. Replace `config/taskbound-client.toml` on each client only if you want to reset local display preferences. A dedicated server does not need the client file.
4. Start the game/server and inspect the log for definition errors. World datapacks can override these definitions; disable or update those overrides if you want only the shipped defaults.

The reference deliberately omits `.initialized`; the first start creates it after checking the seeded files. Keep that generated marker afterward so deliberate deletions remain deleted. Do not copy a modified live configuration back into this reference.

This resets definition files and optional settings, not saved player reputation, claimed level gifts, completed-task history, or accepted-task snapshots. Already generated tasks retain their saved requirements and rewards. Current offers may also retain old snapshots until refreshed.

## Maintaining the reference

The authoritative JSON seeds remain in [taskbound_defaults](../src/main/resources/taskbound_defaults/data/taskbound). This reference mirrors every entry in their index; tests compare its exact bytes and paths against a freshly seeded configuration. The native test suite also parses both TOML files and compares every setting with the server/client configuration specifications. Update the reference alongside intentional default changes.
