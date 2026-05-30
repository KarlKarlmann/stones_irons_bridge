package net.stones_irons_bridge.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.PacketDistributor;
import net.stones_irons_bridge.StonesIronsBridge;
import net.stones_irons_bridge.network.PacketSyncSpellConfigs;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpellConfigManager {

    public static final Map<String, SpellConfig> SPELL_CONFIGS = new HashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final File CONFIG_DIR = new File(FMLPaths.CONFIGDIR.get().toFile(), "stones_irons_bridge");
    private static final File CONFIG_FILE = new File(CONFIG_DIR, "reagent_list.json");

    public static class SpellConfig {
        public int circle;
        public List<Reagent> reagents;
        public SpellConfig(int circle, List<Reagent> reagents) {
            this.circle = circle;
            this.reagents = reagents != null ? reagents : new ArrayList<>();
        }
    }

    public static class Reagent {
        public String item;
        public int count;
        public Reagent(String item, int count) {
            this.item = item != null ? item : "minecraft:air";
            this.count = count;
        }
    }

    /**
     * Wird beim Serverstart aufgerufen.
     * Reihenfolge: 1. Hardcoded Liste -> 2. Config Override -> 3. Lücken mit Dummys füllen -> 4. Speichern
     */
    public static void loadAndGenerateConfigs() {
        if (!CONFIG_DIR.exists()) CONFIG_DIR.mkdirs();
        SPELL_CONFIGS.clear();
        boolean needsSaving = false;

        // 1. HARDCODED LISTE AUS DEN MOD-DATEN LADEN (\data\irons_spellbooks\spell_reagents.json)
        try (InputStream is = SpellConfigManager.class.getResourceAsStream("/data/stones_irons_bridge/spell_reagents.json")) {
            if (is != null) {
                try (InputStreamReader reader = new InputStreamReader(is)) {
                    Type type = new TypeToken<Map<String, SpellConfig>>(){}.getType();
                    Map<String, SpellConfig> dataMap = GSON.fromJson(reader, type);
                    if (dataMap != null) {
                        for (Map.Entry<String, SpellConfig> entry : dataMap.entrySet()) {
                            if (entry.getValue().reagents == null) entry.getValue().reagents = new ArrayList<>();
                            SPELL_CONFIGS.put(entry.getKey(), entry.getValue());
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Ignorieren, falls die Datei nicht existiert
        }

        // 2. CONFIG LADEN UND ÜBERSCHREIBEN (config/stones_irons_bridge/reagent_list.json)
        Map<String, SpellConfig> customConfigs = loadFromFile(CONFIG_FILE);
        if (customConfigs != null && !customConfigs.isEmpty()) {
            SPELL_CONFIGS.putAll(customConfigs);
        } else if (customConfigs == null) {
            customConfigs = new HashMap<>(); 
        }

        // 3. ALLE SPELLS ABGLEICHEN UND LÜCKEN FÜLLEN 
        try {
            // DIE GOLDENE LÖSUNG: Wir fragen einfach die offizielle Forge-Registry von ISS ab!
            // Das liefert zu 100% alle Zauber (auch aus Addons), die jemals ins Spiel geladen wurden.
            for (AbstractSpell spell : SpellRegistry.REGISTRY.get().getValues()) {
                if (spell == null || spell.getSpellId() == null || spell.getSpellId().equals("irons_spellbooks:none")) continue;
                
                String spellId = spell.getSpellId();
                
                // Ist der Spell noch nicht in unserer kombinierten Liste?
                if (!SPELL_CONFIGS.containsKey(spellId)) {
                    SpellConfig fallbackConfig = generateFallback(spell);
                    
                    // Zur Laufzeit-Liste hinzufügen
                    SPELL_CONFIGS.put(spellId, fallbackConfig);
                    
                    // Zur Config-Liste hinzufügen, damit der Admin es später anpassen kann
                    customConfigs.put(spellId, fallbackConfig);
                    needsSaving = true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 4. CONFIG SPEICHERN (Nur wenn neue, unbekannte Spells ergänzt wurden)
        if (needsSaving) {
            saveToFile(CONFIG_FILE, customConfigs);
        }
    }

    /**
     * JIT-Sicherheitsnetz. Wenn zur Laufzeit ein völlig unbekannter Zauber auftaucht.
     */
    public static SpellConfig getSpellConfig(String spellId) {
        if (spellId == null || spellId.isEmpty()) return null;
        SpellConfig config = SPELL_CONFIGS.get(spellId);
        
        if (config == null) {
            AbstractSpell spell = SpellRegistry.getSpell(spellId);
            if (spell != null && !spell.getSpellId().equals("irons_spellbooks:none")) {
                config = generateFallback(spell);
                SPELL_CONFIGS.put(spellId, config); 
                
                Map<String, SpellConfig> customConfigs = loadFromFile(CONFIG_FILE);
                customConfigs.put(spellId, config);
                saveToFile(CONFIG_FILE, customConfigs);
                
                syncToAllPlayers();
            }
        }
        return config;
    }

	private static void syncToAllPlayers() {
		if (StonesIronsBridge.PACKET_HANDLER != null && 
			net.minecraftforge.fml.util.thread.EffectiveSide.get().isServer()) {
			StonesIronsBridge.PACKET_HANDLER.send(
				PacketDistributor.ALL.noArg(),
				new PacketSyncSpellConfigs(SPELL_CONFIGS)
			);
		}
	}

    private static SpellConfig generateFallback(AbstractSpell spell) {
        int baseCircle = 1;
        if (spell.getDefaultConfig() != null && spell.getDefaultConfig().minRarity != null) {
            baseCircle = switch (spell.getDefaultConfig().minRarity) {
                case COMMON -> 1;
                case UNCOMMON -> 3;
                case RARE -> 5;
                case EPIC -> 7;
                case LEGENDARY -> 9;
                default -> 1;
            };
        }

        String reagentItem = "minecraft:nether_wart";
        if (spell.getSchoolType() != null && spell.getSchoolType().getId() != null) {
            reagentItem = switch (spell.getSchoolType().getId().toString().replace("irons_spellbooks:", "")) {
                case "fire" -> "minecraft:gunpowder";
                case "lightning" -> "minecraft:oxeye_daisy";
                case "ice" -> "minecraft:string";
                case "blood" -> "minecraft:beetroot";
                case "nature" -> "minecraft:sweet_berries";
                case "holy" -> "minecraft:allium";
                case "evocation" -> "minecraft:nether_wart";
                case "ender", "void" -> "minecraft:ender_pearl";
                default -> "minecraft:nether_wart";
            };
        }

        List<Reagent> reagents = new ArrayList<>();
        reagents.add(new Reagent(reagentItem, 1));
        return new SpellConfig(baseCircle, reagents);
    }

    private static Map<String, SpellConfig> loadFromFile(File file) {
        if (!file.exists()) return new HashMap<>();
        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<Map<String, SpellConfig>>(){}.getType();
            Map<String, SpellConfig> map = GSON.fromJson(reader, type);
            if (map != null) {
                for (SpellConfig config : map.values()) {
                    if (config.reagents == null) config.reagents = new ArrayList<>();
                }
                return map;
            }
        } catch (Exception e) {}
        return new HashMap<>();
    }

    private static void saveToFile(File file, Map<String, SpellConfig> data) {
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(data, writer);
        } catch (Exception e) {}
    }

    public static void setConfigsFromServer(Map<String, SpellConfig> serverConfigs) {
        if (serverConfigs != null) {
            SPELL_CONFIGS.clear();
            SPELL_CONFIGS.putAll(serverConfigs);
        }
    }
}