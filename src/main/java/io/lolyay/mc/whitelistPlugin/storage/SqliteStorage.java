package io.lolyay.mc.whitelistPlugin.storage;

import io.lolyay.mc.whitelistPlugin.core.WhitelistList;
import lombok.RequiredArgsConstructor;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

@RequiredArgsConstructor
public final class SqliteStorage implements Storage {

    @FunctionalInterface
    private interface SqlAction<T> {
        T run() throws Exception;
    }

    private final File file;
    private final Logger log;
    private final ExecutorService db = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "UltraWhitelist-DB");
        t.setDaemon(true);
        return t;
    });

    private Connection conn;

    private <T> T call(SqlAction<T> action) throws Exception {
        try {
            return db.submit(action::run).get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception ex) {
                throw ex;
            }
            throw e;
        }
    }

    private void write(SqlAction<Void> action) {
        try {
            db.submit(() -> {
                try {
                    action.run();
                } catch (Exception e) {
                    log.log(Level.SEVERE, "UltraWhitelist: database write failed", e);
                }
                return null;
            });
        } catch (RejectedExecutionException ignored) {
        }
    }

    @Override
    public void init() throws Exception {
        call(() -> {
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
            try (Statement s = conn.createStatement()) {
                s.execute("PRAGMA foreign_keys = ON");
                s.execute("PRAGMA journal_mode = WAL");
                s.execute("CREATE TABLE IF NOT EXISTS lists (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "name TEXT NOT NULL UNIQUE COLLATE NOCASE)");
                s.execute("CREATE TABLE IF NOT EXISTS entries (" +
                        "list_id INTEGER NOT NULL, " +
                        "type TEXT NOT NULL, " +
                        "value TEXT NOT NULL, " +
                        "display TEXT, " +
                        "PRIMARY KEY(list_id, type, value), " +
                        "FOREIGN KEY(list_id) REFERENCES lists(id) ON DELETE CASCADE)");
                s.execute("CREATE TABLE IF NOT EXISTS settings (" +
                        "key TEXT PRIMARY KEY, value TEXT)");
                s.execute("CREATE TABLE IF NOT EXISTS name_cache (" +
                        "uuid TEXT PRIMARY KEY, name TEXT NOT NULL)");
            }
            return null;
        });
    }

    @Override
    public Map<String, WhitelistList> loadLists() throws Exception {
        return call(() -> {
            Map<String, WhitelistList> byLower = new HashMap<>();
            Map<Integer, String> idToLower = new HashMap<>();
            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery("SELECT id, name FROM lists")) {
                while (rs.next()) {
                    String name = rs.getString("name");
                    String lower = name.toLowerCase(Locale.ROOT);
                    idToLower.put(rs.getInt("id"), lower);
                    byLower.put(lower, new WhitelistList(name));
                }
            }
            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery("SELECT list_id, type, value, display FROM entries")) {
                while (rs.next()) {
                    String lower = idToLower.get(rs.getInt("list_id"));
                    if (lower == null) {
                        continue;
                    }
                    WhitelistList list = byLower.get(lower);
                    String type = rs.getString("type");
                    String value = rs.getString("value");
                    if ("uuid".equals(type)) {
                        try {
                            list.addUuid(UUID.fromString(value));
                        } catch (IllegalArgumentException ignored) {
                        }
                    } else {
                        String display = rs.getString("display");
                        list.addName(display != null ? display : value);
                    }
                }
            }
            return byLower;
        });
    }

    @Override
    public Map<String, String> loadSettings() throws Exception {
        return call(() -> {
            Map<String, String> map = new HashMap<>();
            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery("SELECT key, value FROM settings")) {
                while (rs.next()) {
                    map.put(rs.getString("key"), rs.getString("value"));
                }
            }
            return map;
        });
    }

    @Override
    public void cacheName(UUID id, String name) {
        write(() -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO name_cache(uuid, name) VALUES(?, ?) " +
                            "ON CONFLICT(uuid) DO UPDATE SET name = excluded.name")) {
                ps.setString(1, id.toString());
                ps.setString(2, name);
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public Map<UUID, String> loadNameCache() throws Exception {
        return call(() -> {
            Map<UUID, String> map = new HashMap<>();
            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery("SELECT uuid, name FROM name_cache")) {
                while (rs.next()) {
                    try {
                        map.put(UUID.fromString(rs.getString("uuid")), rs.getString("name"));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            return map;
        });
    }

    @Override
    public void createList(String name) {
        write(() -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR IGNORE INTO lists(name) VALUES(?)")) {
                ps.setString(1, name);
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public void deleteList(String name) {
        write(() -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM lists WHERE name = ?")) {
                ps.setString(1, name);
                ps.executeUpdate();
            }
            return null;
        });
    }

    private void addEntry(String list, String type, String value, String display) {
        write(() -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR IGNORE INTO entries(list_id, type, value, display) " +
                            "SELECT id, ?, ?, ? FROM lists WHERE name = ?")) {
                ps.setString(1, type);
                ps.setString(2, value);
                ps.setString(3, display);
                ps.setString(4, list);
                ps.executeUpdate();
            }
            return null;
        });
    }

    private void removeEntry(String list, String type, String value) {
        write(() -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM entries WHERE type = ? AND value = ? " +
                            "AND list_id = (SELECT id FROM lists WHERE name = ?)")) {
                ps.setString(1, type);
                ps.setString(2, value);
                ps.setString(3, list);
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public void addUuid(String list, UUID id) {
        addEntry(list, "uuid", id.toString(), null);
    }

    @Override
    public void removeUuid(String list, UUID id) {
        removeEntry(list, "uuid", id.toString());
    }

    @Override
    public void addName(String list, String display) {
        addEntry(list, "name", display.toLowerCase(Locale.ROOT), display);
    }

    @Override
    public void removeName(String list, String display) {
        removeEntry(list, "name", display.toLowerCase(Locale.ROOT));
    }

    @Override
    public void setSetting(String key, String value) {
        write(() -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO settings(key, value) VALUES(?, ?) " +
                            "ON CONFLICT(key) DO UPDATE SET value = excluded.value")) {
                ps.setString(1, key);
                ps.setString(2, value);
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public void close() {
        write(() -> {
            if (conn != null) {
                conn.close();
            }
            return null;
        });
        db.shutdown();
        try {
            if (!db.awaitTermination(5, TimeUnit.SECONDS)) {
                db.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
