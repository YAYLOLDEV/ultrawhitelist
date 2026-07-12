# UltraWhitelist — Developer API

A multi-list, Geyser-friendly whitelist for **ShreddedPaper / Folia** (regionised
multithreading). This document covers depending on the artifact and using its
runtime API from another plugin.

- **Artifact:** `io.lolyay.mc:WhitelistPlugin:1.0.0`
- **Repository:** `https://maven.lolyay.dev/releases`
- **Plugin name (plugin.yml):** `UltraWhitelist`
- **Command:** `/wl` (aliases `ultrawhitelist`, `uwl`)

---

## 1. Depend on it

The published jar is **shaded** (bundles SQLite + a relocated Gson). Because the
plugin is loaded by the server at runtime, depend on it with **`provided`** scope
so you compile against its API without re-bundling it.

### Maven

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

### Gradle (Kotlin DSL)

```kotlin
repositories {
    maven("https://maven.lolyay.dev/releases")
}
dependencies {
    compileOnly("io.lolyay.mc:WhitelistPlugin:1.0.0")
}
```

Add a soft/hard dependency in your own `plugin.yml` so load order is correct:

```yaml
depend: [UltraWhitelist]
```

---

## 2. Get the service

Everything goes through `WhitelistService`, reachable from the plugin instance:

```java
import io.lolyay.mc.whitelistPlugin.WhitelistPlugin;
import io.lolyay.mc.whitelistPlugin.core.WhitelistService;

WhitelistPlugin wl = (WhitelistPlugin) Bukkit.getPluginManager().getPlugin("UltraWhitelist");
if (wl == null || wl.service() == null) return;   // not installed / not enabled yet
WhitelistService service = wl.service();
```

> **Threading:** the service is fully thread-safe (concurrent collections). You may
> call it from the async pre-login thread, region threads, or the async scheduler.
> Reads never touch the database; writes are persisted asynchronously (write-through).

---

## 3. `WhitelistService` API

### Global state
| Method | Description |
|---|---|
| `boolean isEnabled()` | Is whitelisting globally on? |
| `boolean setEnabled(boolean)` | Turn whitelisting on/off; `false` when unchanged. |
| `String activeListName()` | Name of the active (login-enforced) list, or `null`. |
| `WhitelistList activeList()` | The active list object, or `null`. |
| `boolean setActiveList(String name)` | Set the active list (`null` clears it); `false` when unchanged. |

### Membership (the hot path)
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

### Name cache (UUID ⇄ name)
Entries are stored **UUID-first**; a persistent cache maps UUIDs to last-known names
(fed by logins, imports, and playerdb.co lookups).

| Method | Description |
|---|---|
| `void cacheName(UUID id, String name)` | Record/persist a UUID → name mapping. |
| `String nameFor(UUID id)` | Last-known name for a UUID, or `null`. |
| `UUID uuidForName(String name)` | Cached UUID for a name (case-insensitive), or `null`. |

### `WhitelistList`
| Method | Description |
|---|---|
| `String name()` | Display name. |
| `boolean contains(UUID id, String name)` | Matches by UUID or name. |
| `Set<UUID> uuids()` | Live UUID entries. |
| `Map<String,String> names()` | lower-cased name → display name. |
| `int size()` | Total entry count. |

`MergeResult` ∈ `{ OK, MISSING_A, MISSING_B, TARGET_EXISTS }`.

---

## 4. Example: gate a custom feature

```java
WhitelistService service = ((WhitelistPlugin)
        Bukkit.getPluginManager().getPlugin("UltraWhitelist")).service();

WhitelistList vips = service.getList("vips");
boolean isVip = vips != null && vips.contains(player.getUniqueId(), player.getName());
```

---

## 5. Notes & limits

- **No event API yet.** If you need a `WhitelistCheckEvent` / change notifications,
  ask and it can be added.
- **Online-mode UUIDs.** playerdb.co returns Mojang (online-mode) UUIDs. On an
  offline-mode server these won't match server-generated UUIDs — name entries still
  work; ask for offline-UUID resolution if needed.
- **SNAPSHOT vs release.** `1.0.0` is an immutable release on `/releases`. Bump the
  `<version>` in `pom.xml` for each new release (Reposilite rejects re-deploying the
  same release version).
