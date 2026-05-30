package net.stones_irons_bridge.mixin.client;

import io.redspace.ironsspellbooks.gui.overlays.SpellBarOverlay;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(SpellBarOverlay.class)
public abstract class SpellBarOverlayMixin {

    // Liste für alle gefundenen Spell-Wheel-Keybinds (Hold, Toggle, etc.)
    private static final List<KeyMapping> spellWheelKeys = new ArrayList<>();
    private static boolean keyLookupAttempted = false;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, remap = false)
    private void onRender(ForgeGui gui, GuiGraphics guiHelper, float partialTick, int screenWidth, int screenHeight, CallbackInfo ci) {
        // Einmaliges Suchen der Tastenbelegungen
        if (!keyLookupAttempted && Minecraft.getInstance().options != null) {
            for (KeyMapping keyMapping : Minecraft.getInstance().options.keyMappings) {
                // Findet "key.irons_spellbooks.spell_wheel", "key.irons_spellbooks.spell_wheel_toggle" und zukünftige
                if (keyMapping.getName().startsWith("key.irons_spellbooks.spell_wheel")) {
                    spellWheelKeys.add(keyMapping);
                }
            }
            keyLookupAttempted = true;
        }

        // Prüfen, ob ZUMINDEST EINE der Tasten gebunden ist
        boolean isAnyBound = false;
        for (KeyMapping key : spellWheelKeys) {
            if (!key.isUnbound()) {
                isAnyBound = true;
                break;
            }
        }

        // Wenn keine einzige Spell-Wheel-Taste gebunden ist, brich das Rendern ab
        if (!isAnyBound && keyLookupAttempted) {
            ci.cancel();
        }
    }
}