package net.stones_irons_bridge.client.util;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;
import net.stones_irons_bridge.config.SpellConfigManager;
import net.stones_irons_bridge.config.SpellConfigManager.SpellConfig;
import net.stones_irons_bridge.config.SpellConfigManager.Reagent;

public class ScreenTextHelper {

    public static void drawSpellRequirements(GuiGraphics guiGraphics, Font font, AbstractSpell spell, int spellLevel, int startX, int startY) {
        int currentY = startY;

        if (spell != null && !spell.getSpellId().equals("irons_spellbooks:none")) {
            SpellConfig config = SpellConfigManager.SPELL_CONFIGS.get(spell.getSpellId());

            if (config != null) {
                // 1. Zirkel berechnen
                int effectiveCircle = config.circle + (spellLevel - 1);
                
                // Magieschule abrufen (nutzt automatisch eure integrierten Translate-Strings)
                MutableComponent schoolName = spell.getSchoolType().getDisplayName().copy();
                
                // Vanilla Iron's Spells String für das Level nutzen (z.B. "Level 1")
                Component levelText = Component.translatable("ui.irons_spellbooks.level", effectiveCircle).withStyle(ChatFormatting.AQUA);
                
                // Rendert dynamisch z.B.: "School of Blood Level 3" oder "School of Ender Level 1"
                guiGraphics.drawString(font, schoolName.append(" ").append(levelText), startX, currentY, 0xFFFFFF, false);
                currentY += 12;

                // 2. Reagenzien anzeigen
                if (config.reagents != null && !config.reagents.isEmpty()) {
                    for (Reagent r : config.reagents) {
                        Item reqItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation(r.item));
                        MutableComponent itemName = reqItem != null ? reqItem.getDescription().copy() : Component.literal(r.item);
                        
                        guiGraphics.drawString(font, Component.translatable("ui.stones_irons_bridge.reagent_format", r.count, itemName).withStyle(ChatFormatting.RED), startX, currentY, 0xFFFFFF, false);
                        currentY += 12;
                    }
                }
            }
        }
    }
}