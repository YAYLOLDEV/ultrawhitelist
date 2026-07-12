package io.lolyay.mc.whitelistPlugin.storage;

import io.lolyay.mc.whitelistPlugin.core.WhitelistList;

import java.util.Map;
import java.util.UUID;

public interface Storage {

    void init() throws Exception;

    Map<String, WhitelistList> loadLists() throws Exception;

    Map<String, String> loadSettings() throws Exception;

    void createList(String name);

    void deleteList(String name);

    void addUuid(String list, UUID id);

    void removeUuid(String list, UUID id);

    void addName(String list, String display);

    void removeName(String list, String display);

    void setSetting(String key, String value);

    void cacheName(UUID id, String name);

    Map<UUID, String> loadNameCache() throws Exception;

    void close();
}
