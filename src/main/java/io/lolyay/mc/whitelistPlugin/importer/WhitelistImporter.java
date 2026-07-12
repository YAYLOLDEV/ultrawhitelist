package io.lolyay.mc.whitelistPlugin.importer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.lolyay.mc.whitelistPlugin.util.Ids;
import lombok.experimental.UtilityClass;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@UtilityClass
public class WhitelistImporter {

    public static final class Result {
        public final List<UUID> uuids = new ArrayList<>();
        public final List<String> names = new ArrayList<>();
        public final Map<UUID, String> profileNames = new HashMap<>();

        public int total() {
            return uuids.size() + names.size();
        }
    }

    public static File resolve(File serverRoot, String fileName) {
        File direct = new File(fileName);
        if (direct.isFile()) {
            return direct;
        }
        File inRoot = new File(serverRoot, fileName);
        return inRoot.isFile() ? inRoot : null;
    }

    public static Result parse(File file) throws IOException {
        Result result = new Result();
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            JsonArray array = asArray(root);
            if (array == null) {
                throw new IOException("Unsupported JSON shape - expected an array of entries.");
            }
            for (JsonElement element : array) {
                if (element.isJsonPrimitive()) {
                    addName(result, element.getAsString());
                } else if (element.isJsonObject()) {
                    readObject(result, element.getAsJsonObject());
                }
            }
        }
        return result;
    }

    private static JsonArray asArray(JsonElement root) {
        if (root.isJsonArray()) {
            return root.getAsJsonArray();
        }
        if (root.isJsonObject()) {
            JsonObject obj = root.getAsJsonObject();
            for (String key : new String[]{"players", "list", "whitelist", "entries"}) {
                if (obj.has(key) && obj.get(key).isJsonArray()) {
                    return obj.getAsJsonArray(key);
                }
            }
        }
        return null;
    }

    private static void readObject(Result result, JsonObject obj) {
        String uuidStr = firstString(obj, "uuid", "uniqueId", "id");
        UUID uuid = Ids.parse(uuidStr);
        String name = firstString(obj, "name", "player", "username");
        if (uuid != null) {
            result.uuids.add(uuid);
            if (name != null && !name.isBlank()) {
                result.profileNames.put(uuid, name.trim());
            }
            return;
        }
        addName(result, name);
    }

    private static void addName(Result result, String name) {
        if (name != null && !name.isBlank()) {
            result.names.add(name.trim());
        }
    }

    private static String firstString(JsonObject obj, String... keys) {
        for (String key : keys) {
            if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
                return obj.get(key).getAsString();
            }
        }
        return null;
    }
}
