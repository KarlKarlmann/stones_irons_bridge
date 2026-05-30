package net.stones_irons_bridge.init;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.stones_irons_bridge.StonesIronsBridge;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * DYNAMISCHE RESSOURCEN-REGISTRIERUNG:
 * Um absolute Synchronität zwischen Client und Server zu gewährleisten, scannen wir beim
 * Spielstart das Verzeichnis "/data/stones_irons_bridge/schools" innerhalb aller geladenen Mod-JARs.
 * * Jede dort gefundene JSON-Datei (z.B. blood.json) registriert automatisch das entsprechende Attribut.
 * Da Client und Server dieselben Mods installiert haben müssen, sind die Registries garantiert zu 100% synchron.
 */
public class StonesIronsAttributes {
    
    // Das DeferredRegister für unsere Magie-Attribute
    public static final DeferredRegister<Attribute> REGISTRY = DeferredRegister.create(ForgeRegistries.ATTRIBUTES, StonesIronsBridge.MODID);

    // Eine Map, um später im Code extrem performant via String (z.B. "blood") an das Attribut zu kommen
    public static final Map<String, RegistryObject<Attribute>> KNOWLEDGE_ATTRIBUTES = new HashMap<>();

    private static final String INTERNAL_DATA_PATH = "data/stones_irons_bridge/schools";

    static {
        // Scanne alle geladenen Mods nach Schul-Definitionen
        List<String> schools = scanSchoolsFromResources();
        
        // Sicherheitsnetz-Fallback: Falls aus irgendeinem Grund gar keine Ressourcen geladen werden konnten
        if (schools.isEmpty()) {
            StonesIronsBridge.LOGGER.warn("Keine Magieschulen in den Mod-Ressourcen gefunden! Verwende standardmäßige Fallbacks.");
            schools.add("blood");
            schools.add("ender");
            schools.add("evocation");
            schools.add("fire");
            schools.add("holy");
            schools.add("ice");
            schools.add("lightning");
            schools.add("nature");
        }

        // Registriere jedes gefundene Attribut dynamisch
        for (String school : schools) {
            registerKnowledge(school);
        }
    }

    private static void registerKnowledge(String school) {
        String attributeName = "knowledge_" + school;
        
        // Erstellt das RangedAttribute. setSyncable(true) sorgt für den automatischen Client-Sync der Werte im Spiel!
        RegistryObject<Attribute> attr = REGISTRY.register(attributeName,
            () -> new RangedAttribute("attribute.name." + StonesIronsBridge.MODID + "." + attributeName, 0.0, 0.0, 1024.0).setSyncable(true)
        );
        
        KNOWLEDGE_ATTRIBUTES.put(school, attr);
    }

    /**
     * Durchsucht alle aktiven Mod-JARs nach Dateien im Pfad "data/stones_irons_bridge/schools/".
     * Der Name der JSON-Datei bestimmt den Namen der Schule.
     */
    private static List<String> scanSchoolsFromResources() {
        List<String> schools = new ArrayList<>();

        for (IModInfo mod : ModList.get().getMods()) {
            String modId = mod.getModId();
            try {
                Path path = ModList.get().getModFileById(modId).getFile().findResource(INTERNAL_DATA_PATH);
                if (Files.exists(path)) {
                    try (Stream<Path> walk = Files.walk(path, 1)) {
                        walk.filter(p -> p.toString().endsWith(".json"))
                            .forEach(file -> {
                                try {
                                    // Der Dateiname ohne Endung wird als Bezeichner der Schule genutzt (z.B. blood.json -> "blood")
                                    String filename = file.getFileName().toString().replace(".json", "").toLowerCase().trim();
                                    if (!schools.contains(filename)) {
                                        schools.add(filename);
                                        StonesIronsBridge.LOGGER.info("Erkannte Magieschule aus Ressourcen von Mod '{}': '{}'", modId, filename);
                                    }
                                } catch (Exception e) {
                                    StonesIronsBridge.LOGGER.error("Fehler beim Verarbeiten der Schul-Datei: " + file, e);
                                }
                            });
                    }
                }
            } catch (Exception e) {
                // Mod besitzt diesen Pfad nicht, wir können ihn stillschweigend überspringen
            }
        }
        return schools;
    }
}