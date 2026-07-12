# UltraWhitelist

UltraWhitelist is a Paper/Folia whitelist plugin with multiple named lists, SQLite persistence, player-name resolution, JSON imports, and region-safe enforcement.

## Features
- Multi Whitelist support: Designed for multiple Lists at the same time
- Hotswapping Lists: Lists can be swapped using a single command
- Easy Import: `/wl import whitelist.json xxx` imports your already existing Server whitelist.json
- Enforcement: Kicks all players not on the list
- Extensive list management: Lots of commands to Create/Delete/Merge/Manage lists
- Developer friendly API: Easy plugin integration

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


## Permissions

| Permission | Purpose | Default |
|---|---|---|
| `ultrawhitelist.admin` | Use whitelist-management commands. | Operators |
| `ultrawhitelist.bypass` | Avoid `/wl enforce` kicks. | Operators |


## Developer API

## 1. Add the Dependency:
  
  #### Maven
  
  ```xml
  <repositories>
      <repository>
          <id>lolyay-releases</id>
          <url>https://maven.lolyay.dev/releases</url>
      </repository>
  </repositories>
  
  <dependencies>
      <dependency>
          <groupId>io.lolyay.mc</groupId>
          <artifactId>WhitelistPlugin</artifactId>
          <version>1.0.0</version>
          <scope>provided</scope>
      </dependency>
  </dependencies>
  ```
  
  #### Gradle (Kotlin DSL)
  
  ```kotlin
  repositories {
      maven("https://maven.lolyay.dev/releases")
  }
  dependencies {
      compileOnly("io.lolyay.mc:WhitelistPlugin:1.0.0")
  }
  ```

### 2. Depend on it:

  ```yaml
  depend: [UltraWhitelist]
  ```


### 3. Get the service

Everything goes through `WhitelistService`, reachable from the plugin instance:

```java
import io.lolyay.mc.whitelistPlugin.WhitelistPlugin;
import io.lolyay.mc.whitelistPlugin.core.WhitelistService;

WhitelistPlugin wl = (WhitelistPlugin) Bukkit.getPluginManager().getPlugin("UltraWhitelist");
if (wl == null || wl.service() == null) return;   // not installed / not enabled yet
WhitelistService service = wl.service();
```



## 3. `WhitelistService` API

### Global state
| Method | Description |
|---|---|
| `boolean isEnabled()` | Is whitelisting globally on? |
| `boolean setEnabled(boolean)` | Turn whitelisting on/off; returns `false` when unchanged. |
| `String activeListName()` | Name of the active (login-enforced) list, or `null`. |
| `WhitelistList activeList()` | The active list object, or `null`. |
| `boolean setActiveList(String name)` | Set the active list (`null` clears it); `false` when unchanged. |

### Membership
| Method | Description |
|---|---|
| `boolean isWhitelisted(UUID id, String name)` | The exact check used on login. `true` if whitelisting is off, no active list, or the identity is on the active list (by UUID **or** name). |

```java
boolean allowed = service.isWhitelisted(event.getUniqueId(), event.getName());
```

### Lists
| Method | Description |
|---|---|
| `WhitelistList getList(String name)` | Case-insensitive lookup, or `null`. |
| `Collection<WhitelistList> lists()` | All lists. |
| `List<String> findLists(UUID id, String name)` | Sorted names of every matching list. |
| `boolean createList(String name)` | `false` if it already exists. |
| `boolean deleteList(String name)` | `false` if no such list. |

### Entries
| Method | Description |
|---|---|
| `boolean addUuid(String list, UUID id)` | Add an entry; `false` when unchanged or missing. |
| `boolean addName(String list, String name)` | Add a name entry; `false` when unchanged or missing. |
| `boolean removeUuid(String list, UUID id)` | Remove an entry; `false` when unchanged or missing. |
| `boolean removeName(String list, String name)` | Remove an entry; `false` when unchanged or missing. |
| `List<String> addUuidToAll(UUID)` / `addNameToAll(String)` | Add to every list and return changed list names. |
| `List<String> removeUuidFromAll(UUID)` / `removeNameFromAll(String)` | Remove from every list and return changed list names. |
| `MergeResult merge(String a, String b, String newName)` | Merge two lists into a new one. |
| `int addFromList(String src, String dest)` | Copy entries and return the number added (`-1` if missing). |

### Name cache (UUID <-> name)
Entries are stored **UUID-first**; a persistent cache maps UUIDs to last-known names
(fed by logins, imports, and playerdb.co lookups).

| Method | Description |
|---|---|
| `void cacheName(UUID id, String name)` | Record/persist a UUID -> name mapping. |
| `String nameFor(UUID id)` | Last-known name for a UUID, or `null`. |
| `UUID uuidForName(String name)` | Cached UUID for a name (case-insensitive), or `null`. |

### `WhitelistList`
| Method | Description |
|---|---|
| `String name()` | Display name. |
| `boolean contains(UUID id, String name)` | Matches by UUID or name. |
| `Set<UUID> uuids()` | Live UUID entries. |
| `Map<String,String> names()` | lower-cased name -> display name. |
| `int size()` | Total entry count. |

`MergeResult`: `{ OK, MISSING_A, MISSING_B, TARGET_EXISTS }`.

