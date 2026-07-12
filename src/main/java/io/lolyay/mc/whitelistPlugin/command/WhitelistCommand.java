package io.lolyay.mc.whitelistPlugin.command;

import io.lolyay.mc.whitelistPlugin.WhitelistPlugin;
import io.lolyay.mc.whitelistPlugin.core.WhitelistList;
import io.lolyay.mc.whitelistPlugin.core.WhitelistService;
import io.lolyay.mc.whitelistPlugin.importer.WhitelistImporter;
import io.lolyay.mc.whitelistPlugin.util.Ids;
import io.lolyay.mc.whitelistPlugin.util.Msg;
import io.lolyay.mc.whitelistPlugin.util.PlayerDbClient;
import io.lolyay.mc.whitelistPlugin.util.ThreadUtil;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public final class WhitelistCommand implements TabExecutor {

    private static final String PERM = "ultrawhitelist.admin";
    private static final String BYPASS = "ultrawhitelist.bypass";
    private static final int PAGE_SIZE = 10;

    private static final List<String> SUBCOMMANDS = List.of(
            "on", "off", "info", "list", "listplayers", "find", "create", "delete",
            "add", "remove", "use", "enforce", "merge", "addfromlist",
            "import", "confirm", "reload", "help");

    private final WhitelistPlugin plugin;
    private final WhitelistService service;
    private final ConfirmationManager confirm;

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String @NonNull [] args) {
        if (!sender.hasPermission(PERM)) {
            Msg.err(sender, "You don't have permission to manage whitelists.");
            return true;
        }
        if (args.length == 0) {
            help(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String[] a = Arrays.copyOfRange(args, 1, args.length);
        switch (sub) {
            case "on" -> setEnabled(sender, true);
            case "off" -> setEnabled(sender, false);
            case "info" -> info(sender);
            case "list" -> listLists(sender);
            case "listplayers" -> listPlayers(sender, a);
            case "find" -> find(sender, a);
            case "create" -> create(sender, a);
            case "delete" -> delete(sender, a);
            case "add" -> add(sender, a);
            case "remove" -> remove(sender, a);
            case "use" -> use(sender, a);
            case "enforce" -> enforce(sender, a);
            case "merge" -> merge(sender, a);
            case "addfromlist" -> addFromList(sender, a);
            case "import" -> importFile(sender, a);
            case "confirm" -> confirm(sender);
            case "reload" -> reload(sender);
            case "help" -> help(sender);
            default -> {
                Msg.err(sender, "Unknown subcommand '" + sub + "'. Try /wl help");
            }
        }
        return true;
    }

    private void setEnabled(CommandSender sender, boolean value) {
        if (!service.setEnabled(value)) {
            Msg.err(sender, "Whitelisting is already " + (value ? "ON." : "OFF."));
            return;
        }
        if (value) {
            String active = service.activeListName();
            Msg.ok(sender, "Whitelisting is now ON.");
            if (active == null) {
                Msg.accent(sender, "No active list is set - nobody is being filtered yet. Use /wl use <list>.");
            } else {
                Msg.info(sender, "Active list: " + active);
            }
        } else {
            Msg.ok(sender, "Whitelisting is now OFF. All players may join.");
        }
    }

    private void info(CommandSender sender) {
        int totalEntries = service.lists().stream().mapToInt(WhitelistList::size).sum();
        Msg.send(sender, Component.text("UltraWhitelist status", NamedTextColor.AQUA));
        Msg.info(sender, "  Enabled: " + (service.isEnabled() ? "yes" : "no"));
        Msg.info(sender, "  Active list: " + (service.activeListName() == null ? "(none)" : service.activeListName()));
        Msg.info(sender, "  Lists: " + service.lists().size() + "   Total entries: " + totalEntries);
    }

    private void listLists(CommandSender sender) {
        if (service.lists().isEmpty()) {
            Msg.info(sender, "No lists exist yet. Create one with /wl create <name>.");
            return;
        }
        Msg.send(sender, Component.text("Whitelists (" + service.lists().size() + "):", NamedTextColor.AQUA));
        String active = service.activeListName();
        service.lists().stream()
                .sorted((x, y) -> x.name().compareToIgnoreCase(y.name()))
                .forEach(l -> {
                    boolean isActive = l.name().equalsIgnoreCase(active);
                    Component line = Component.text("  " + l.name(), isActive ? NamedTextColor.GREEN : NamedTextColor.WHITE)
                            .append(Component.text(" (" + l.size() + ")", NamedTextColor.GRAY))
                            .append(isActive ? Component.text("  [active]", NamedTextColor.GREEN) : Component.empty());
                    sender.sendMessage(Msg.PREFIX.append(line));
                });
    }

    private void listPlayers(CommandSender sender, String[] a) {
        if (a.length < 1) {
            Msg.err(sender, "Usage: /wl listplayers <list> [page]");
            return;
        }
        WhitelistList list = service.getList(a[0]);
        if (list == null) {
            Msg.err(sender, "No such list: " + a[0]);
            return;
        }
        if (list.isEmpty()) {
            Msg.info(sender, "List '" + list.name() + "' is empty.");
            return;
        }
        List<UUID> unresolved = list.uuids().stream()
                .filter(id -> service.nameFor(id) == null)
                .toList();
        if (unresolved.isEmpty()) {
            showPlayers(sender, list, a);
            return;
        }
        Msg.info(sender, "Resolving player names via playerdb.co ...");
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            for (UUID id : unresolved) {
                PlayerDbClient.lookup(id.toString()).ifPresent(profile ->
                        service.cacheName(profile.uuid(), profile.name()));
            }
            showPlayers(sender, list, a);
        });
    }

    private void showPlayers(CommandSender sender, WhitelistList list, String[] a) {
        List<String> rows = new ArrayList<>(list.names().values());
        list.uuids().stream()
                .map(service::nameFor)
                .filter(name -> name != null && !name.isBlank())
                .forEach(rows::add);
        rows.sort(String.CASE_INSENSITIVE_ORDER);
        if (rows.isEmpty()) {
            Msg.err(sender, "No player names could be resolved for list '" + list.name() + "'.");
            return;
        }
        int pages = (rows.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        int page = 1;
        if (a.length >= 2) {
            try {
                page = Math.clamp(Integer.parseInt(a[1]), 1, pages);
            } catch (NumberFormatException e) {
                Msg.err(sender, "Page must be a number.");
                return;
            }
        }
        int from = (page - 1) * PAGE_SIZE;
        int to = Math.min(rows.size(), from + PAGE_SIZE);
        Msg.send(sender, Component.text(
                list.name() + " - page " + page + "/" + pages + " (" + rows.size() + " entries)",
                NamedTextColor.AQUA));
        for (int i = from; i < to; i++) {
            Msg.info(sender, "  " + rows.get(i));
        }
        if (page < pages) {
            Msg.info(sender, "  ... /wl listplayers " + list.name() + " " + (page + 1));
        }
        long unresolved = list.uuids().stream().filter(id -> service.nameFor(id) == null).count();
        if (unresolved > 0) {
            Msg.accent(sender, unresolved + " player name(s) could not be resolved.");
        }
    }

    private void find(CommandSender sender, String[] a) {
        if (a.length < 1) {
            Msg.err(sender, "Usage: /wl find <player>");
            return;
        }
        String lookup = a[0];
        UUID id = Ids.parse(lookup);
        if (id != null) {
            String name = service.nameFor(id);
            if (name != null) {
                showFind(sender, id, name);
                return;
            }
        } else {
            UUID cached = service.uuidForName(lookup);
            if (cached != null) {
                showFind(sender, cached, service.nameFor(cached));
                return;
            }
        }
        Msg.info(sender, "Resolving player via playerdb.co ...");
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            Optional<PlayerDbClient.Profile> profile = PlayerDbClient.lookup(lookup);
            if (profile.isPresent()) {
                service.cacheName(profile.get().uuid(), profile.get().name());
                showFind(sender, profile.get().uuid(), profile.get().name());
            } else if (id == null) {
                showFind(sender, null, lookup);
            } else {
                Msg.err(sender, "Couldn't resolve that player's name.");
            }
        });
    }

    private void showFind(CommandSender sender, UUID id, String name) {
        List<String> lists = service.findLists(id, name);
        if (lists.isEmpty()) {
            Msg.info(sender, "'" + name + "' is not in any lists.");
            return;
        }
        Msg.info(sender, "'" + name + "' is in lists: " + formatLists(lists) + ".");
    }

    private void create(CommandSender sender, String[] a) {
        if (a.length < 1) {
            Msg.err(sender, "Usage: /wl create <name>");
            return;
        }
        if (service.createList(a[0])) {
            Msg.ok(sender, "Created list '" + a[0] + "'.");
        } else {
            Msg.err(sender, "A list named '" + a[0] + "' already exists.");
        }
    }

    private void delete(CommandSender sender, String[] a) {
        if (a.length < 1) {
            Msg.err(sender, "Usage: /wl delete <list>");
            return;
        }
        WhitelistList list = service.getList(a[0]);
        if (list == null) {
            Msg.err(sender, "No such list: " + a[0]);
            return;
        }
        String name = list.name();
        confirm.request(sender, "delete list '" + name + "'", () -> {
            if (service.deleteList(name)) {
                Msg.ok(sender, "Deleted list '" + name + "'.");
            } else {
                Msg.err(sender, "List '" + name + "' no longer exists; nothing changed.");
            }
        });
        Msg.accent(sender, "Delete list '" + name + "' (" + list.size()
                + " entries)? This cannot be undone. Run /wl confirm within 30s.");
    }

    private void add(CommandSender sender, String[] a) {
        if (a.length < 2) {
            Msg.err(sender, "Usage: /wl add <list|all> <player>");
            return;
        }
        String target = a[0];
        String who = a[1];
        boolean all = target.equalsIgnoreCase("all");

        String listName;
        if (all) {
            if (service.lists().isEmpty()) {
                Msg.err(sender, "There are no lists to add to.");
                return;
            }
            listName = null;
        } else {
            WhitelistList list = service.getList(target);
            if (list == null) {
                Msg.err(sender, "No such list: " + target + " (create it with /wl create " + target + ")");
                return;
            }
            listName = list.name();
        }

        UUID uuid = Ids.parse(who);
        if (uuid != null) {
            String name = service.nameFor(uuid);
            if (name != null) {
                applyAddUuid(sender, all, listName, uuid, name);
            } else {
                resolveAndAdd(sender, all, listName, who, true);
            }
            return;
        }
        UUID cached = service.uuidForName(who);
        if (cached != null) {
            applyAddUuid(sender, all, listName, cached, service.nameFor(cached));
            return;
        }

        resolveAndAdd(sender, all, listName, who, false);
    }

    private void resolveAndAdd(CommandSender sender, boolean all, String listName, String lookup,
                               boolean requireProfile) {
        Msg.info(sender, "Resolving player name via playerdb.co ...");
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            Optional<PlayerDbClient.Profile> profile = PlayerDbClient.lookup(lookup);
            if (profile.isPresent()) {
                service.cacheName(profile.get().uuid(), profile.get().name());
                applyAddUuid(sender, all, listName, profile.get().uuid(), profile.get().name());
            } else if (requireProfile) {
                Msg.err(sender, "Couldn't resolve that player's name.");
            } else {
                List<String> changed = applyAddName(sender, all, listName, lookup);
                if (!changed.isEmpty()) {
                    Msg.accent(sender, "Player profile was unavailable; stored by name instead.");
                }
            }
        });
    }

    private void applyAddUuid(CommandSender sender, boolean all, String listName, UUID uuid, String name) {
        List<String> changed = all
                ? service.addUuidToAll(uuid)
                : service.addUuid(listName, uuid) ? List.of(listName) : List.of();
        reportAdded(sender, name, changed);
    }

    private List<String> applyAddName(CommandSender sender, boolean all, String listName, String name) {
        List<String> changed = all
                ? service.addNameToAll(name)
                : service.addName(listName, name) ? List.of(listName) : List.of();
        reportAdded(sender, name, changed);
        return changed;
    }

    private void reportAdded(CommandSender sender, String name, List<String> changed) {
        if (changed.isEmpty()) {
            Msg.err(sender, "'" + name + "' is already in the requested list(s); nothing changed.");
            return;
        }
        Msg.ok(sender, "Added '" + name + "' to lists: " + formatLists(changed) + ".");
    }

    private void remove(CommandSender sender, String[] a) {
        if (a.length < 2) {
            Msg.err(sender, "Usage: /wl remove <list|all> <player>");
            return;
        }
        String target = a[0];
        String who = a[1];
        boolean all = target.equalsIgnoreCase("all");

        String listName;
        if (all) {
            listName = null;
        } else {
            WhitelistList list = service.getList(target);
            if (list == null) {
                Msg.err(sender, "No such list: " + target);
                return;
            }
            listName = list.name();
        }

        UUID uuid = Ids.parse(who);
        if (uuid != null) {
            String name = service.nameFor(uuid);
            if (name != null) {
                removeResolvedPlayer(sender, all, listName, uuid, name);
            } else {
                resolveAndRemove(sender, all, listName, who);
            }
            return;
        }

        List<String> nameChanges = applyRemoveName(all, listName, who);

        UUID cached = service.uuidForName(who);
        if (cached != null) {
            reportRemoved(sender, who, mergeChanged(nameChanges, applyRemoveUuid(all, listName, cached)));
            return;
        }

        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            Optional<PlayerDbClient.Profile> profile = PlayerDbClient.lookup(who);
            if (profile.isPresent()) {
                service.cacheName(profile.get().uuid(), profile.get().name());
                List<String> changed = mergeChanged(nameChanges,
                        applyRemoveName(all, listName, profile.get().name()));
                changed = mergeChanged(changed,
                        applyRemoveUuid(all, listName, profile.get().uuid()));
                reportRemoved(sender, profile.get().name(), changed);
            } else {
                reportRemoved(sender, who, nameChanges);
            }
        });
    }

    private void resolveAndRemove(CommandSender sender, boolean all, String listName, String lookup) {
        Msg.info(sender, "Resolving player name via playerdb.co ...");
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            Optional<PlayerDbClient.Profile> profile = PlayerDbClient.lookup(lookup);
            if (profile.isEmpty()) {
                Msg.err(sender, "Couldn't resolve that player's name.");
                return;
            }
            service.cacheName(profile.get().uuid(), profile.get().name());
            removeResolvedPlayer(sender, all, listName, profile.get().uuid(), profile.get().name());
        });
    }

    private void removeResolvedPlayer(CommandSender sender, boolean all, String listName, UUID uuid, String name) {
        List<String> changed = mergeChanged(
                applyRemoveName(all, listName, name),
                applyRemoveUuid(all, listName, uuid));
        reportRemoved(sender, name, changed);
    }

    private List<String> applyRemoveUuid(boolean all, String listName, UUID uuid) {
        return all
                ? service.removeUuidFromAll(uuid)
                : service.removeUuid(listName, uuid) ? List.of(listName) : List.of();
    }

    private List<String> applyRemoveName(boolean all, String listName, String name) {
        return all
                ? service.removeNameFromAll(name)
                : service.removeName(listName, name) ? List.of(listName) : List.of();
    }

    private void reportRemoved(CommandSender sender, String name, List<String> changed) {
        if (changed.isEmpty()) {
            Msg.err(sender, "'" + name + "' is not in the requested list(s); nothing changed.");
            return;
        }
        Msg.ok(sender, "Removed '" + name + "' from lists: " + formatLists(changed) + ".");
    }

    private void use(CommandSender sender, String[] a) {
        if (a.length < 1) {
            Msg.err(sender, "Usage: /wl use <list>");
            return;
        }
        WhitelistList list = service.getList(a[0]);
        if (list == null) {
            Msg.err(sender, "No such list: " + a[0]);
            return;
        }
        String name = list.name();
        if (name.equalsIgnoreCase(service.activeListName())) {
            Msg.err(sender, "'" + name + "' is already the active list; nothing changed.");
            return;
        }
        confirm.request(sender, "set active list to '" + name + "'", () -> {
            if (service.setActiveList(name)) {
                Msg.ok(sender, "Active list changed to '" + name + "'.");
                if (!service.isEnabled()) {
                    Msg.accent(sender, "Whitelisting is currently OFF - run /wl on to start filtering logins.");
                }
            } else {
                Msg.err(sender, "'" + name + "' is already the active list; nothing changed.");
            }
        });
        Msg.accent(sender, "Set active whitelist to '" + name + "'? Run /wl confirm within 30s.");
    }

    private void enforce(CommandSender sender, String[] a) {
        String listName = a.length >= 1 ? a[0] : service.activeListName();
        if (listName == null) {
            Msg.err(sender, "No list given and no active list set. Usage: /wl enforce <list>");
            return;
        }
        WhitelistList list = service.getList(listName);
        if (list == null) {
            Msg.err(sender, "No such list: " + listName);
            return;
        }
        List<String> kicked = new ArrayList<>();
        Component reason = Component.text("You are not whitelisted on this server.", NamedTextColor.RED);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission(BYPASS)) {
                continue;
            }
            if (!list.contains(player.getUniqueId(), player.getName())) {
                ThreadUtil.kick(plugin, player, reason);
                kicked.add(player.getName());
            }
        }
        kicked.sort(String.CASE_INSENSITIVE_ORDER);
        if (kicked.isEmpty()) {
            Msg.err(sender, "Every online player is allowed by '" + list.name() + "'; nothing changed.");
            return;
        }
        Msg.ok(sender, "Kicked players not in '" + list.name() + "': " + formatLists(kicked) + ".");
    }

    private void merge(CommandSender sender, String[] a) {
        if (a.length < 3) {
            Msg.err(sender, "Usage: /wl merge <listA> <listB> <newList>");
            return;
        }
        WhitelistService.MergeResult result = service.merge(a[0], a[1], a[2]);
        switch (result) {
            case OK -> Msg.ok(sender, "Merged '" + a[0] + "' and '" + a[1] + "' into new list '"
                    + a[2] + "' (" + service.getList(a[2]).size() + " entries).");
            case MISSING_A -> Msg.err(sender, "No such list: " + a[0]);
            case MISSING_B -> Msg.err(sender, "No such list: " + a[1]);
            case TARGET_EXISTS -> Msg.err(sender, "Target list '" + a[2] + "' already exists - pick a new name.");
        }
    }

    private void addFromList(CommandSender sender, String[] a) {
        if (a.length < 2) {
            Msg.err(sender, "Usage: /wl addfromlist <sourceList> <destList>");
            return;
        }
        WhitelistList src = service.getList(a[0]);
        WhitelistList dest = service.getList(a[1]);
        if (src == null) {
            Msg.err(sender, "No such list: " + a[0]);
            return;
        }
        if (dest == null) {
            Msg.err(sender, "No such list: " + a[1]);
            return;
        }
        int added = service.addFromList(src.name(), dest.name());
        if (added == 0) {
            Msg.err(sender, "'" + dest.name() + "' already contains every entry from '"
                    + src.name() + "'; nothing changed.");
            return;
        }
        Msg.ok(sender, "Added " + added + " new entries from '" + src.name()
                + "' to '" + dest.name() + "'.");
    }

    private void importFile(CommandSender sender, String[] a) {
        if (a.length < 2) {
            Msg.err(sender, "Usage: /wl import <file.json> <list>");
            return;
        }
        String fileName = a[0];
        String listName = a[1];

        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            File file = WhitelistImporter.resolve(Bukkit.getWorldContainer(), fileName);
            if (file == null) {
                Msg.err(sender, "File not found in server directory: " + fileName);
                return;
            }
            try {
                WhitelistImporter.Result result = WhitelistImporter.parse(file);
                boolean created = service.createList(listName);
                WhitelistList list = service.getList(listName);
                if (list == null) {
                    Msg.err(sender, "Could not create or load list '" + listName + "'.");
                    return;
                }
                String destName = list.name();
                result.profileNames.forEach(service::cacheName);
                int added = 0;
                for (UUID id : result.uuids) {
                    if (service.addUuid(destName, id)) {
                        added++;
                    }
                }
                for (String name : result.names) {
                    if (service.addName(destName, name)) {
                        added++;
                    }
                }
                if (!created && added == 0) {
                    Msg.err(sender, "Import made no changes to '" + destName + "'.");
                } else if (created) {
                    Msg.ok(sender, "Created list '" + destName + "' and imported " + added
                            + " new entries from " + file.getName() + ".");
                } else {
                    Msg.ok(sender, "Imported " + added + " new entries from " + file.getName()
                            + " into '" + destName + "'.");
                }
            } catch (Exception e) {
                Msg.err(sender, "Import failed: " + e.getMessage());
            }
        });
    }

    private void confirm(CommandSender sender) {
        if (!confirm.confirm(sender)) {
            Msg.err(sender, "Nothing to confirm (or it expired).");
        }
    }

    private void reload(CommandSender sender) {
        try {
            plugin.reloadSettings();
            service.load();
            Msg.info(sender, "Reloaded whitelists from disk. Enabled=" + service.isEnabled()
                    + ", active=" + service.activeListName());
        } catch (Exception e) {
            Msg.err(sender, "Reload failed: " + e.getMessage());
        }
    }

    private void help(CommandSender sender) {
        Msg.send(sender, Component.text("UltraWhitelist commands", NamedTextColor.AQUA));
        String[] lines = {
                "/wl on | off - toggle whitelisting globally",
                "/wl use <list> - set the active (enforced-on-login) list",
                "/wl enforce [list] - kick online players not on the list",
                "/wl create <name> - create an empty list",
                "/wl delete <list> - delete a list",
                "/wl add <list|all> <player> - add a player",
                "/wl remove <list|all> <player> - remove a player",
                "/wl merge <a> <b> <new> - merge two lists into a new one",
                "/wl addfromlist <src> <dest> - copy src's players into dest",
                "/wl import <file.json> <list> - import from server-root JSON",
                "/wl list - show all lists",
                "/wl listplayers <list> [page] - show players in a list",
                "/wl find <player> - show every list containing a player",
                "/wl info - show status",
                "/wl reload - reload from disk",
                "/wl confirm - confirm a pending action",
        };
        for (String line : lines) {
            Msg.info(sender, "  " + line);
        }
    }

    private static List<String> mergeChanged(List<String> first, List<String> second) {
        TreeSet<String> merged = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        merged.addAll(first);
        merged.addAll(second);
        return new ArrayList<>(merged);
    }

    private static String formatLists(List<String> lists) {
        return "[" + String.join(", ", lists) + "]";
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String @NonNull [] args) {
        if (!sender.hasPermission(PERM)) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "add", "remove" -> {
                if (args.length == 2) {
                    List<String> opts = new ArrayList<>(listNames());
                    opts.add("all");
                    yield filter(opts, args[1]);
                }
                yield args.length == 3 ? filter(onlineNames(), args[2]) : List.of();
            }
            case "delete", "use", "enforce", "listplayers" ->
                    args.length == 2 ? filter(listNames(), args[1]) : List.of();
            case "find" -> args.length == 2 ? filter(onlineNames(), args[1]) : List.of();
            case "merge" -> args.length <= 4 ? filter(listNames(), args[args.length - 1]) : List.of();
            case "addfromlist" -> args.length <= 3 ? filter(listNames(), args[args.length - 1]) : List.of();
            case "import" -> {
                if (args.length == 2) {
                    yield filter(jsonFilesInRoot(), args[1]);
                }
                yield args.length == 3 ? filter(listNames(), args[2]) : List.of();
            }
            default -> List.of();
        };
    }

    private List<String> listNames() {
        return service.lists().stream().map(WhitelistList::name).collect(Collectors.toList());
    }

    private static List<String> onlineNames() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
    }

    private static List<String> jsonFilesInRoot() {
        File[] files = Bukkit.getWorldContainer().listFiles(
                (dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".json"));
        if (files == null) {
            return List.of();
        }
        return Arrays.stream(files).map(File::getName).collect(Collectors.toList());
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }
}
