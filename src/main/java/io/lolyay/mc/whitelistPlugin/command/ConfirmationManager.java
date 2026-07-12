package io.lolyay.mc.whitelistPlugin.command;

import org.bukkit.command.CommandSender;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ConfirmationManager {

    private static final long EXPIRY_MS = 30_000L;

    private record Pending(Runnable action, long stagedAt, String description) {
    }

    private final Map<String, Pending> pending = new ConcurrentHashMap<>();

    private static String key(CommandSender sender) {
        return sender.getName().toLowerCase(Locale.ROOT);
    }

    public void request(CommandSender sender, String description, Runnable action) {
        pending.put(key(sender), new Pending(action, System.currentTimeMillis(), description));
    }

    public String pendingDescription(CommandSender sender) {
        Pending p = pending.get(key(sender));
        if (p == null || isExpired(p)) {
            return null;
        }
        return p.description();
    }

    public boolean confirm(CommandSender sender) {
        Pending p = pending.remove(key(sender));
        if (p == null || isExpired(p)) {
            return false;
        }
        p.action().run();
        return true;
    }

    private static boolean isExpired(Pending p) {
        return System.currentTimeMillis() - p.stagedAt() > EXPIRY_MS;
    }
}
