package io.lolyay.mc.whitelistPlugin.core;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public final class WhitelistList {

    private final String name;
    private final Set<UUID> uuids = ConcurrentHashMap.newKeySet();
    private final Map<String, String> names = new ConcurrentHashMap<>();

    public boolean addUuid(UUID id) {
        return uuids.add(id);
    }

    public boolean removeUuid(UUID id) {
        return uuids.remove(id);
    }

    public boolean addName(String display) {
        return names.put(display.toLowerCase(Locale.ROOT), display) == null;
    }

    public boolean removeName(String display) {
        return names.remove(display.toLowerCase(Locale.ROOT)) != null;
    }

    public boolean contains(UUID id, String playerName) {
        if (id != null && uuids.contains(id)) {
            return true;
        }
        return playerName != null && names.containsKey(playerName.toLowerCase(Locale.ROOT));
    }

    public int size() {
        return uuids.size() + names.size();
    }

    public boolean isEmpty() {
        return uuids.isEmpty() && names.isEmpty();
    }
}
