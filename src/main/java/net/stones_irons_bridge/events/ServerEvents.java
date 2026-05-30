package net.stones_irons_bridge.events;

import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.stones_irons_bridge.StonesIronsBridge;
import net.stones_irons_bridge.config.SpellConfigManager;
import net.stones_irons_bridge.network.PacketSyncSpellConfigs;

@Mod.EventBusSubscriber(modid = StonesIronsBridge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ServerEvents {

    @SubscribeEvent
    public static void onServerStart(ServerStartedEvent event) {
        // Generiert die Fallbacks und lädt alles in den Speicher
        SpellConfigManager.loadAndGenerateConfigs();
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        // OnDatapackSyncEvent ist der absolut sicherste Ort für Sync-Pakete in Forge 1.20+.
        // Es feuert beim Login UND wenn /reload auf dem Server getippt wird. Die Kanäle sind hier garantiert offen.
        if (event.getPlayer() != null) {
            // Ein einzelner Spieler loggt sich ein -> Sende nur an ihn
            StonesIronsBridge.PACKET_HANDLER.send(
                PacketDistributor.PLAYER.with(event::getPlayer),
                new PacketSyncSpellConfigs(SpellConfigManager.SPELL_CONFIGS)
            );
        } else {
            // Der Server wurde neu geladen (z.B. durch /reload) -> Sende an ALLE Spieler
            StonesIronsBridge.PACKET_HANDLER.send(
                PacketDistributor.ALL.noArg(),
                new PacketSyncSpellConfigs(SpellConfigManager.SPELL_CONFIGS)
            );
        }
    }
}