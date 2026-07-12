package io.lolyay.mc.whitelistPlugin.util;

import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

@UtilityClass
public class ThreadUtil {

    public static void kick(Plugin plugin, Player player, Component reason) {
        if (Bukkit.isOwnedByCurrentRegion(player)) {
            player.kick(reason);
        } else {
            player.getScheduler().run(plugin, task -> player.kick(reason), null);
        }
    }
}
