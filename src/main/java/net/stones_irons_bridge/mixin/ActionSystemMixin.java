package net.stones_irons_bridge.mixin;

import com.mojang.datafixers.util.Either;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.player.ClientMagicData;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.stones.features.ActionSystem;
import net.stones_irons_bridge.client.ClientSetupHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;

@Mixin(value = ActionSystem.class, remap = false)
public class ActionSystemMixin {

    @Shadow(remap = false) private static List<ResourceLocation> CALCULATED_ACTIONS_CACHE;
    @Shadow(remap = false) private static Map<ResourceLocation, Integer> ACTION_LEVEL_CACHE;

    @Inject(method = "refreshCalculatedActions", 
            at = @At(value = "FIELD", target = "Lnet/stones/features/ActionSystem;CLIENT_SLOTS:[Ljava/lang/String;", ordinal = 0),
            remap = false)
    private static void onRefreshCalculatedActionsBeforeGhostFix(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            ClientSetupHandler.injectSpellsIntoCache(CALCULATED_ACTIONS_CACHE, ACTION_LEVEL_CACHE);
        }
    }

    @Inject(method = "getActionIcon", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onGetActionIcon(String id, CallbackInfoReturnable<ResourceLocation> cir) {
        if (id != null && id.startsWith("irons_spellbooks:")) {
            AbstractSpell spell = SpellRegistry.getSpell(id);
            if (spell != null && !spell.getSpellId().equals("irons_spellbooks:none")) {
                cir.setReturnValue(spell.getSpellIconResource());
            }
        }
    }

    @Inject(method = "getActionTooltip", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onGetActionTooltip(String id, CallbackInfoReturnable<List<Either<FormattedText, TooltipComponent>>> cir) {
        if (id != null && id.startsWith("irons_spellbooks:")) {
            int level = ACTION_LEVEL_CACHE.getOrDefault(new ResourceLocation(id), 1);
            List<Either<FormattedText, TooltipComponent>> tooltip = ClientSetupHandler.buildActionTooltip(id, level);
            cir.setReturnValue(tooltip);
        }
    }

    // --- NEU: DIE COOLDOWN EINSPEISUNG (PHASE 4) ---
    @Inject(method = "getActionCooldown", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onGetActionCooldown(String id, CallbackInfoReturnable<Integer> cir) {
        if (id != null && id.startsWith("irons_spellbooks:")) {
            AbstractSpell spell = SpellRegistry.getSpell(id);
            if (spell != null && !spell.getSpellId().equals("irons_spellbooks:none")) {
                // Holt sich den Cooldown-Fortschritt (0.0 bis 1.0)
                float cdPercent = ClientMagicData.getCooldownPercent(spell);
                if (cdPercent > 0) {
                    // Wandelt den Prozentsatz wieder in die echten verbleibenden Sekunden um
                    int totalTicks = spell.getSpellCooldown();
                    int remainingTicks = (int) (cdPercent * totalTicks);
                    cir.setReturnValue((remainingTicks / 20) + 1);
                } else {
                    cir.setReturnValue(0);
                }
            }
        }
    }
}