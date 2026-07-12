# UltraWhitelist

UltraWhitelist is a Paper/Folia whitelist plugin with multiple named lists, SQLite persistence, player-name resolution, JSON imports, and region-safe enforcement.

## Requirements

- Java 25
- Paper/Folia API 26.1.2 or newer
- Maven 3.9+

## Build and install

```text
mvn clean package
```

Copy the shaded JAR from `target/` into the server's `plugins/` directory and restart the server. Runtime data is stored under `plugins/UltraWhitelist/`.

## Configuration

`config.yml`:

```yaml
playerDbUrl: "https://playerdb.co/api/player/minecraft/{player}"
```

`{player}` is replaced with the URL-encoded player name or internal identity used for lookup. The URL must use HTTP or HTTPS and must contain the placeholder. `/wl reload` reloads both this configuration and whitelist data.

## Commands

| Command | Purpose |
|---|---|
| `/wl on` | Enable whitelist enforcement. |
| `/wl off` | Disable whitelist enforcement. |
| `/wl info` | Show current status. |
| `/wl list` | Show every whitelist. |
| `/wl listplayers <list> [page]` | Show player names in a list. |
| `/wl find <player>` | Show every list containing a player. |
| `/wl create <name>` | Create a list. |
| `/wl delete <list>` | Stage a list deletion for confirmation. |
| `/wl add <list\|all> <player>` | Add a player and report exactly which lists changed. |
| `/wl remove <list\|all> <player>` | Remove a player and report exactly which lists changed. |
| `/wl use <list>` | Stage an active-list change for confirmation. |
| `/wl enforce [list]` | Kick online players not allowed by a list. |
| `/wl merge <a> <b> <new>` | Merge two lists into a new list. |
| `/wl addfromlist <source> <destination>` | Copy new entries between lists. |
| `/wl import <file.json> <list>` | Import a whitelist-style JSON file. |
| `/wl reload` | Reload configuration and persisted data. |
| `/wl confirm` | Run the pending confirmed action. |
| `/wl help` | Show command help. |

The command aliases are `/ultrawhitelist` and `/uwl`. Mutating commands return an error when nothing changes. Player UUIDs remain internal and command output always uses player names; uncached names are fetched through the configured PlayerDB endpoint.

## Permissions

| Permission | Purpose | Default |
|---|---|---|
| `ultrawhitelist.admin` | Use whitelist-management commands. | Operators |
| `ultrawhitelist.bypass` | Avoid `/wl enforce` kicks. | Operators |

## Storage and imports

Whitelist state and the player-name cache are stored in `whitelist.db`. Imports accept vanilla `whitelist.json`, arrays of names, and arrays wrapped by `players`, `list`, `whitelist`, or `entries`.

## Metrics

UltraWhitelist uses bStats plugin ID `32575`. The `whitelist_lists` chart reports the current number of lists. Server owners can control metrics through the standard bStats configuration.

For Java integration and Maven coordinates, see [API.md](API.md).
