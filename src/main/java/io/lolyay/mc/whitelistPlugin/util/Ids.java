package io.lolyay.mc.whitelistPlugin.util;

import lombok.experimental.UtilityClass;

import java.util.UUID;
import java.util.regex.Pattern;

@UtilityClass
public class Ids {

    private static final Pattern DASHED = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern UNDASHED = Pattern.compile("[0-9a-fA-F]{32}");

    public static UUID parse(String s) {
        if (s == null) {
            return null;
        }
        try {
            if (DASHED.matcher(s).matches()) {
                return UUID.fromString(s);
            }
            if (UNDASHED.matcher(s).matches()) {
                String dashed = s.replaceFirst(
                        "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})",
                        "$1-$2-$3-$4-$5");
                return UUID.fromString(dashed);
            }
        } catch (IllegalArgumentException ignored) {
        }
        return null;
    }
}
