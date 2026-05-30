package net.stones_irons_bridge.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraftforge.fml.loading.FMLPaths;
import net.stones_irons_bridge.StonesIronsBridge;

import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;

public class BridgeServerConfig {
    // SERVER-SEITIGE REGELN
    public static boolean useReagents = true;
    public static boolean promptAnswered = false;

    private static final Path CONFIG_DIR = FMLPaths.CONFIGDIR.get().resolve("stones_irons_bridge");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("server_settings.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void load() {
        try {
            if (!Files.exists(CONFIG_DIR)) Files.createDirectories(CONFIG_DIR);
            if (!Files.exists(CONFIG_FILE)) {
                save();
                return;
            }
            try (FileReader reader = new FileReader(CONFIG_FILE.toFile())) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                if (json != null) {
                    if (json.has("use_reagents")) useReagents = json.get("use_reagents").getAsBoolean();
                    if (json.has("prompt_answered")) promptAnswered = json.get("prompt_answered").getAsBoolean();
                }
            }
        } catch (Exception e) {
            StonesIronsBridge.LOGGER.error("Fehler beim Laden der server_settings.json!", e);
        }
    }

    public static void save() {
        try {
            if (!Files.exists(CONFIG_DIR)) Files.createDirectories(CONFIG_DIR);
            JsonObject json = new JsonObject();
            json.addProperty("use_reagents", useReagents);
            json.addProperty("prompt_answered", promptAnswered);
            try (FileWriter writer = new FileWriter(CONFIG_FILE.toFile())) {
                GSON.toJson(json, writer);
            }
        } catch (Exception e) {
            StonesIronsBridge.LOGGER.error("Fehler beim Speichern der server_settings.json!", e);
        }
    }
}