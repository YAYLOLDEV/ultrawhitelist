package io.lolyay.mc.whitelistPlugin.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.experimental.UtilityClass;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@UtilityClass
public class PlayerDbClient {

    public static final String defaultUrl = "https://playerdb.co/api/player/minecraft/{player}";
    private static final String userAgent = "UltraWhitelist/1.0 (Minecraft whitelist plugin)";
    private static final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static volatile String playerDbUrl = defaultUrl;

    public record Profile(UUID uuid, String name) {
    }

    public static void configure(String value) {
        String configured = value == null || value.isBlank() ? defaultUrl : value.trim();
        if (!configured.contains("{player}")) {
            throw new IllegalArgumentException("playerDbUrl must contain {player}");
        }
        URI probe = URI.create(configured.replace("{player}", "player"));
        if (!"http".equalsIgnoreCase(probe.getScheme()) && !"https".equalsIgnoreCase(probe.getScheme())) {
            throw new IllegalArgumentException("playerDbUrl must use HTTP or HTTPS");
        }
        playerDbUrl = configured;
    }

    public static Optional<Profile> lookup(String nameOrId) {
        if (nameOrId == null || nameOrId.isBlank()) {
            return Optional.empty();
        }
        try {
            String encoded = URLEncoder.encode(nameOrId.trim(), StandardCharsets.UTF_8)
                    .replace("+", "%20");
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create(playerDbUrl.replace("{player}", encoded)))
                    .header("User-Agent", userAgent)
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            return parse(response.body());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static Optional<Profile> parse(String body) {
        JsonElement root = JsonParser.parseString(body);
        if (!root.isJsonObject()) {
            return Optional.empty();
        }
        JsonObject obj = root.getAsJsonObject();
        JsonObject player = null;
        if (obj.has("data") && obj.get("data").isJsonObject()) {
            JsonObject data = obj.getAsJsonObject("data");
            if (data.has("player") && data.get("player").isJsonObject()) {
                player = data.getAsJsonObject("player");
            }
        }
        if (player == null) {
            player = obj;
        }
        String id = str(player, "id", "raw_id", "uuid");
        String name = str(player, "username", "name");
        UUID uuid = Ids.parse(id);
        if (uuid == null || name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new Profile(uuid, name));
    }

    private static String str(JsonObject obj, String... keys) {
        for (String key : keys) {
            if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
                return obj.get(key).getAsString();
            }
        }
        return null;
    }
}
