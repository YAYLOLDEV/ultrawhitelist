package io.lolyay.mc.whitelistPlugin.core;

import io.lolyay.mc.whitelistPlugin.storage.Storage;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public final class WhitelistService {

    private final Map<String, WhitelistList> lists = new ConcurrentHashMap<>();
    private final Storage storage;

    private final Map<UUID, String> uuidToName = new ConcurrentHashMap<>();
    private final Map<String, UUID> nameToUuid = new ConcurrentHashMap<>();

    @Getter
    private volatile boolean enabled;
    @Getter
    @Accessors(fluent = true)
    private volatile String activeListName;

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    public void load() throws Exception {
        lists.clear();
        lists.putAll(storage.loadLists());
        uuidToName.clear();
        nameToUuid.clear();
        storage.loadNameCache().forEach(this::putNameCache);
        Map<String, String> settings = storage.loadSettings();
        this.enabled = Boolean.parseBoolean(settings.getOrDefault("enabled", "false"));
        String active = settings.get("active_list");
        this.activeListName = (active == null || active.isEmpty() || getList(active) == null)
                ? null : getList(active).name();
    }

    private void putNameCache(UUID id, String name) {
        String previous = uuidToName.put(id, name);
        if (previous != null && !previous.equalsIgnoreCase(name)) {
            nameToUuid.remove(previous.toLowerCase(Locale.ROOT), id);
        }
        nameToUuid.put(name.toLowerCase(Locale.ROOT), id);
    }

    public void cacheName(UUID id, String name) {
        if (id == null || name == null || name.isBlank()) {
            return;
        }
        putNameCache(id, name);
        storage.cacheName(id, name);
    }

    public String nameFor(UUID id) {
        return id == null ? null : uuidToName.get(id);
    }

    public UUID uuidForName(String name) {
        return name == null ? null : nameToUuid.get(name.toLowerCase(Locale.ROOT));
    }

    public WhitelistList getList(String name) {
        return name == null ? null : lists.get(key(name));
    }

    public Collection<WhitelistList> lists() {
        return lists.values();
    }

    public WhitelistList activeList() {
        return getList(activeListName);
    }

    public boolean isWhitelisted(UUID id, String name) {
        if (!enabled) {
            return true;
        }
        WhitelistList active = activeList();
        return active == null || active.contains(id, name);
    }

    public boolean setEnabled(boolean value) {
        if (enabled == value) {
            return false;
        }
        this.enabled = value;
        storage.setSetting("enabled", String.valueOf(value));
        return true;
    }

    public boolean setActiveList(String name) {
        WhitelistList list = getList(name);
        String next = list == null ? null : list.name();
        if (Objects.equals(activeListName, next)) {
            return false;
        }
        this.activeListName = next;
        storage.setSetting("active_list", activeListName == null ? "" : activeListName);
        return true;
    }

    public boolean createList(String name) {
        if (getList(name) != null) {
            return false;
        }
        lists.put(key(name), new WhitelistList(name));
        storage.createList(name);
        return true;
    }

    public boolean deleteList(String name) {
        WhitelistList list = getList(name);
        if (list == null) {
            return false;
        }
        lists.remove(key(name));
        storage.deleteList(list.name());
        if (list.name().equalsIgnoreCase(activeListName)) {
            setActiveList(null);
        }
        return true;
    }

    public boolean addUuid(String list, UUID id) {
        WhitelistList l = getList(list);
        if (l == null) {
            return false;
        }
        boolean changed = l.addUuid(id);
        if (changed) {
            storage.addUuid(l.name(), id);
        }
        return changed;
    }

    public boolean addName(String list, String display) {
        WhitelistList l = getList(list);
        if (l == null) {
            return false;
        }
        boolean changed = l.addName(display);
        if (changed) {
            storage.addName(l.name(), display);
        }
        return changed;
    }

    public boolean removeUuid(String list, UUID id) {
        WhitelistList l = getList(list);
        if (l == null) {
            return false;
        }
        boolean changed = l.removeUuid(id);
        if (changed) {
            storage.removeUuid(l.name(), id);
        }
        return changed;
    }

    public boolean removeName(String list, String display) {
        WhitelistList l = getList(list);
        if (l == null) {
            return false;
        }
        boolean changed = l.removeName(display);
        if (changed) {
            storage.removeName(l.name(), display);
        }
        return changed;
    }

    private int copyInto(WhitelistList src, WhitelistList dest) {
        int changed = 0;
        for (UUID id : src.uuids()) {
            if (dest.addUuid(id)) {
                storage.addUuid(dest.name(), id);
                changed++;
            }
        }
        for (String display : src.names().values()) {
            if (dest.addName(display)) {
                storage.addName(dest.name(), display);
                changed++;
            }
        }
        return changed;
    }

    public MergeResult merge(String a, String b, String newName) {
        WhitelistList la = getList(a);
        WhitelistList lb = getList(b);
        if (la == null) {
            return MergeResult.MISSING_A;
        }
        if (lb == null) {
            return MergeResult.MISSING_B;
        }
        if (getList(newName) != null) {
            return MergeResult.TARGET_EXISTS;
        }
        createList(newName);
        WhitelistList dest = getList(newName);
        copyInto(la, dest);
        copyInto(lb, dest);
        return MergeResult.OK;
    }

    public int addFromList(String src, String dest) {
        WhitelistList s = getList(src);
        WhitelistList d = getList(dest);
        if (s == null || d == null) {
            return -1;
        }
        return copyInto(s, d);
    }

    public List<String> addUuidToAll(UUID id) {
        List<String> changed = new ArrayList<>();
        for (WhitelistList l : lists.values()) {
            if (l.addUuid(id)) {
                storage.addUuid(l.name(), id);
                changed.add(l.name());
            }
        }
        changed.sort(String.CASE_INSENSITIVE_ORDER);
        return changed;
    }

    public List<String> addNameToAll(String display) {
        List<String> changed = new ArrayList<>();
        for (WhitelistList l : lists.values()) {
            if (l.addName(display)) {
                storage.addName(l.name(), display);
                changed.add(l.name());
            }
        }
        changed.sort(String.CASE_INSENSITIVE_ORDER);
        return changed;
    }

    public List<String> removeUuidFromAll(UUID id) {
        List<String> changed = new ArrayList<>();
        for (WhitelistList l : lists.values()) {
            if (l.removeUuid(id)) {
                storage.removeUuid(l.name(), id);
                changed.add(l.name());
            }
        }
        changed.sort(String.CASE_INSENSITIVE_ORDER);
        return changed;
    }

    public List<String> removeNameFromAll(String display) {
        List<String> changed = new ArrayList<>();
        for (WhitelistList l : lists.values()) {
            if (l.removeName(display)) {
                storage.removeName(l.name(), display);
                changed.add(l.name());
            }
        }
        changed.sort(String.CASE_INSENSITIVE_ORDER);
        return changed;
    }

    public List<String> findLists(UUID id, String name) {
        return lists.values().stream()
                .filter(list -> list.contains(id, name))
                .map(WhitelistList::name)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    public enum MergeResult {
        OK, MISSING_A, MISSING_B, TARGET_EXISTS
    }
}
