package net.stones_irons_bridge.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.stones.network.PacketPerformAction;
import net.stones_irons_bridge.logic.SpellCastHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PacketPerformAction.class, remap = false)
public class PacketPerformActionMixin {

    /**
     * DIE WEICHE (PHASE 3)
     * Wir fangen das Server-Paket ab, bevor die Stones Mod ihre Runen-Logik startet.
     */
    @Inject(method = "validateAndExecute", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onValidateAndExecute(ServerPlayer player, String runeId, int slot, CallbackInfo ci) {
        if (runeId != null && runeId.startsWith("irons_spellbooks:")) {
            // 1. Wir brechen die Stones-Mod ab! Sie soll nicht nach einer Rune suchen.
            ci.cancel();
            
            // 2. Wir delegieren den Cast an das Backend von Iron's Spells
            SpellCastHandler.executeSpellOnServer(player, runeId);
        }
    }
}