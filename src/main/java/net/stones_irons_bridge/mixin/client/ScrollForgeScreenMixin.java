package net.stones_irons_bridge.mixin.client;

import io.redspace.ironsspellbooks.gui.scroll_forge.ScrollForgeScreen;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.stones_irons_bridge.client.util.ScreenTextHelper;
import net.stones_irons_bridge.logic.SpellCastHandler;
import net.stones_irons_bridge.config.SpellConfigManager;
import net.stones_irons_bridge.config.SpellConfigManager.SpellConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScrollForgeScreen.class)
public abstract class ScrollForgeScreenMixin {

    // Wir brauchen hier ein Hilfs-Feld, um uns den Spell zu merken, den wir rendern wollen
    private AbstractSpell currentHoveredOrSelectedSpell = SpellRegistry.none();

    /**
     * 1. HIER DUNKELN WIR AB:
     * Wir fangen die Abfrage "canBeCraftedBy" ab, während Iron's Spells die Liste zeichnet.
     */
    @Redirect(
        method = "renderSpellList", 
        at = @At(value = "INVOKE", target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;canBeCraftedBy(Lnet/minecraft/world/entity/player/Player;)Z"), 
        remap = false
    )
    private boolean redirectCanBeCrafted(AbstractSpell spell, Player player) {
        if (!spell.canBeCraftedBy(player)) {
            return false;
        }

        // Neues Wissens-System statt harter Zirkel
        String schoolId = spell.getSchoolType().getId().toString();
        double playerKnowledge = SpellCastHandler.getPlayerKnowledge(player, schoolId);

        SpellConfig config = SpellConfigManager.SPELL_CONFIGS.get(spell.getSpellId());
        double requiredKnowledge = SpellCastHandler.getRequiredKnowledge(config != null ? config.circle : 1);

        // Erlaubt das Craften, sobald das Wissen ausreicht
        return playerKnowledge >= requiredKnowledge;
    }

    /**
     * Da wir nicht mehr auf das private getSelectedSpell zugreifen können,
     * fangen wir einfach den Moment ab, in dem Iron's Spells einen Zauber in der Liste generiert
     * und "merken" ihn uns, wenn er aktiv (selected) ist.
     */
    @Inject(method = "setSelectedSpell", at = @At("HEAD"), remap = false)
    private void onSetSelectedSpell(AbstractSpell spell, CallbackInfo ci) {
        this.currentHoveredOrSelectedSpell = spell;
    }

    /**
     * Wir setzen den Spell auch zurück, wenn die UI geschlossen oder resetted wird.
     */
    @Inject(method = "resetList", at = @At("HEAD"), remap = false)
    private void onResetList(CallbackInfo ci) {
        this.currentHoveredOrSelectedSpell = SpellRegistry.none();
    }

}