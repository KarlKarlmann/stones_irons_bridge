package net.stones_irons_bridge.events;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.BasicItemListing;
import net.minecraftforge.event.village.WandererTradesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.stones_irons_bridge.StonesIronsBridge;

@Mod.EventBusSubscriber(modid = StonesIronsBridge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class WanderingTraderEvents {

    @SubscribeEvent
    public static void onWandererTrades(WandererTradesEvent event) {
        
        // ==========================================
        // SPIELER VERKAUFT ROHSTOFFE -> ERHÄLT SMARAGDE
        // Parameter: (Was der Trader verlangt, Was der Trader gibt, Max. Nutzungen, XP für den Trader, Preis-Multiplikator)
        // ==========================================
        
        event.getGenericTrades().add(new BasicItemListing(
                new ItemStack(Items.DIRT, 64), 
                new ItemStack(Items.EMERALD, 1), 
                16, 2, 0.05f));
                
        event.getGenericTrades().add(new BasicItemListing(
                new ItemStack(Items.COBBLESTONE, 64), 
                new ItemStack(Items.EMERALD, 1), 
                16, 2, 0.05f));
                
        event.getGenericTrades().add(new BasicItemListing(
                new ItemStack(Items.DEEPSLATE, 32), 
                new ItemStack(Items.EMERALD, 1), 
                16, 2, 0.05f));
                
        event.getGenericTrades().add(new BasicItemListing(
                new ItemStack(Items.SAND, 32), 
                new ItemStack(Items.EMERALD, 1), 
                16, 2, 0.05f));

        // ==========================================
        // SPIELER KAUFT REAGENZIEN -> BEZAHLT SMARAGDE
        // Parameter: (Kosten in Smaragden, Was der Trader gibt, Max. Nutzungen, XP für den Trader)
        // ==========================================
        
        // 5 Emeralds -> 1 Enderpearl
        event.getGenericTrades().add(new BasicItemListing(
                5, new ItemStack(Items.ENDER_PEARL, 1), 8, 2));
                
        // 4 Emeralds -> 1 Oxeye Daisy (Margerite)
        event.getGenericTrades().add(new BasicItemListing(
                4, new ItemStack(Items.OXEYE_DAISY, 1), 8, 2));
                
        // 3 Emeralds -> 1 Gunpowder (Schwarzpulver)
        event.getGenericTrades().add(new BasicItemListing(
                3, new ItemStack(Items.GUNPOWDER, 1), 8, 2));
                
        // 1 Emerald -> 1 Allium (Sternlauch)
        event.getGenericTrades().add(new BasicItemListing(
                1, new ItemStack(Items.ALLIUM, 1), 8, 2));
    }
}