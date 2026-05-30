package net.stones_irons_bridge.network;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.stones_irons_bridge.config.SpellConfigManager;
import net.stones_irons_bridge.config.SpellConfigManager.Reagent;
import net.stones_irons_bridge.config.SpellConfigManager.SpellConfig;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class PacketSyncSpellConfigs {
    private final Map<String, SpellConfig> configs;

    public PacketSyncSpellConfigs(Map<String, SpellConfig> configs) {
        this.configs = configs;
    }

    // Encoder (Schreibt Daten in den Puffer)
    public PacketSyncSpellConfigs(FriendlyByteBuf buf) {
        this.configs = new HashMap<>();
        int size = buf.readInt();
        for (int i = 0; i < size; i++) {
            String spellId = buf.readUtf();
            int circle = buf.readInt();
            int reagentCount = buf.readInt();
            
            List<Reagent> reagents = new ArrayList<>();
            for (int j = 0; j < reagentCount; j++) {
                reagents.add(new Reagent(buf.readUtf(), buf.readInt()));
            }
            this.configs.put(spellId, new SpellConfig(circle, reagents));
        }
    }

    // Decoder (Liest Daten aus dem Puffer)
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(configs.size());
        for (Map.Entry<String, SpellConfig> entry : configs.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeInt(entry.getValue().circle);
            buf.writeInt(entry.getValue().reagents.size());
            for (Reagent r : entry.getValue().reagents) {
                buf.writeUtf(r.item);
                buf.writeInt(r.count);
            }
        }
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {

            SpellConfigManager.SPELL_CONFIGS.clear();

            try (InputStream is = SpellConfigManager.class.getResourceAsStream("/data/stones_irons_bridge/spell_reagents.json")) {
                if (is != null) {
                    try (InputStreamReader reader = new InputStreamReader(is)) {
                        Type type = new TypeToken<Map<String, SpellConfig>>(){}.getType();
                        Map<String, SpellConfig> dataMap = new Gson().fromJson(reader, type);
                        if (dataMap != null) {
                            for (Map.Entry<String, SpellConfig> entry : dataMap.entrySet()) {
                                if (entry.getValue().reagents == null) {
                                    entry.getValue().reagents = new ArrayList<>();
                                }
                                SpellConfigManager.SPELL_CONFIGS.put(entry.getKey(), entry.getValue());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            // 3. Server-Daten darüber mergen (Server hat Vorrang und überschreibt!)
            if (this.configs != null) {
                SpellConfigManager.SPELL_CONFIGS.putAll(this.configs);
            }
        });
        
        // WICHTIG: Sagt Minecraft, dass das Paket erfolgreich verarbeitet wurde.
        context.setPacketHandled(true); 
        return true;
    }
}