package io.lolyay.mc.whitelistPlugin.util;

import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

@UtilityClass
public class Msg {

    public static final Component PREFIX =
            Component.text("[WL] ", NamedTextColor.AQUA);

    public static void send(CommandSender to, Component body) {
        to.sendMessage(PREFIX.append(body));
    }

    public static void info(CommandSender to, String text) {
        send(to, Component.text(text, NamedTextColor.GRAY));
    }

    public static void ok(CommandSender to, String text) {
        send(to, Component.text(text, NamedTextColor.GREEN));
    }

    public static void err(CommandSender to, String text) {
        send(to, Component.text(text, NamedTextColor.RED));
    }

    public static void accent(CommandSender to, String text) {
        send(to, Component.text(text, NamedTextColor.YELLOW));
    }
}
