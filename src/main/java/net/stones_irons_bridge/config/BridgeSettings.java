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

public class BridgeSettings {

    // NUR NOCH CLIENT-EINSTELLUNGEN
    public static boolean guiPromptAnswered = false;
    public static boolean useStonesGui = true;    // Steuert, welche GUI genutzt wird

    private static final Path CONFIG_DIR = FMLPaths.CONFIGDIR.get().resolve("stones_irons_bridge");
    // Umbenannt, damit alte fehlerhafte Configs ignoriert werden
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("client_settings.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static {
        load();
    }

    public static void load() {
        try {
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }
            if (!Files.exists(CONFIG_FILE)) {
                save(); // Erstellt die Standard-Datei
                return;
            }
            try (FileReader reader = new FileReader(CONFIG_FILE.toFile())) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                if (json != null) {
                    if (json.has("gui_prompt_answered")) guiPromptAnswered = json.get("gui_prompt_answered").getAsBoolean();
                    if (json.has("use_stones_gui")) useStonesGui = json.get("use_stones_gui").getAsBoolean();
                }
            }
        } catch (Exception e) {
            StonesIronsBridge.LOGGER.error("Fehler beim Laden der client_settings.json!", e);
        }
    }

    public static void save() {
        try {
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }
            JsonObject json = new JsonObject();
            json.addProperty("gui_prompt_answered", guiPromptAnswered);
            json.addProperty("use_stones_gui", useStonesGui);

            try (FileWriter writer = new FileWriter(CONFIG_FILE.toFile())) {
                GSON.toJson(json, writer);
            }
        } catch (Exception e) {
            StonesIronsBridge.LOGGER.error("Fehler beim Speichern der client_settings.json!", e);
        }
    }
}