package io.lolyay.mc.whitelistPlugin.listener;

import io.lolyay.mc.whitelistPlugin.core.WhitelistService;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

@RequiredArgsConstructor
public final class LoginListener implements Listener {

    private final WhitelistService service;

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        service.cacheName(event.getUniqueId(), event.getName());

        if (service.isWhitelisted(event.getUniqueId(), event.getName())) {
            return;
        }
        event.disallow(
                AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST,
                Component.text("You are not whitelisted on this server.", NamedTextColor.RED));
    }
}
