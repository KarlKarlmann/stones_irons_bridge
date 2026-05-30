package net.stones_irons_bridge.mixin.client;

import io.redspace.ironsspellbooks.gui.inscription_table.InscriptionTableScreen;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.util.TooltipsUtils;
import io.redspace.ironsspellbooks.api.util.Utils;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.ChatFormatting;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.registries.ForgeRegistries;

import net.stones_irons_bridge.config.SpellConfigManager;
import net.stones_irons_bridge.config.SpellConfigManager.SpellConfig;
import net.stones_irons_bridge.config.SpellConfigManager.Reagent;
import net.stones_irons_bridge.logic.SpellCastHandler;
import net.stones_irons_bridge.client.ClientServerSettingsCache;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InscriptionTableScreen.class)
public abstract class InscriptionTableScreenMixin {

    // Hilfsvariable, um den internen selectedSpellIndex nachzubauen
    private int trackedSelectedIndex = -1;

    /**
     * Fängt den Klick auf einen Slot im Buch ab und merkt sich den Index.
     */
    @Inject(method = "setSelectedIndex", at = @At("HEAD"), remap = false)
    private void onSetSelectedIndex(int index, CallbackInfo ci) {
        this.trackedSelectedIndex = index;
    }

    /**
     * Setzt den Index zurück, wenn das UI es auch tut.
     */
    @Inject(method = "resetSelectedSpell", at = @At("HEAD"), remap = false)
    private void onResetSelectedSpell(CallbackInfo ci) {
        this.trackedSelectedIndex = -1;
    }

    /**
     * HIER LÖSCHEN WIR DIE VANILLA-SEITE UND BAUEN SIE KOMPLETT NEU AUF.
     */
    @Inject(method = "renderLorePage", at = @At("HEAD"), cancellable = true, remap = false)
    private void onRenderLorePage(GuiGraphics guiHelper, float partialTick, int mouseX, int mouseY, CallbackInfo ci) {
        // Vanilla-Rendering komplett abbrechen!
        ci.cancel();

        InscriptionTableScreen screen = (InscriptionTableScreen) (Object) this;
        Font font = screen.getMinecraft().font;

        // Koordinaten und Layout-Größen exakt aus Iron's Spells Vanilla-Code übernommen
        int x = screen.getGuiLeft() + 176;
        int y = screen.getGuiTop();
        int margin = 2;
        int LORE_PAGE_WIDTH = 80;
        Style textColor = Style.EMPTY.withColor(0x322c2a); // Das typische Buch-Braun

        // Status aus dem UI-Slot lesen
        ItemStack spellBookStack = screen.getMenu().getSpellBookSlot().getItem();
        boolean hasBook = spellBookStack.getItem() instanceof io.redspace.ironsspellbooks.item.SpellBook;

        ISpellContainer container = hasBook ? ISpellContainer.get(spellBookStack) : null;
        SpellData spellData = null;
        boolean spellSelected = false;

        // Haben wir einen validen Zauber angeklickt?
        if (hasBook && this.trackedSelectedIndex >= 0 && this.trackedSelectedIndex < container.getMaxSpellCount()) {
            spellData = container.getSpellAtIndex(this.trackedSelectedIndex);
            if (spellData != null && spellData.getSpell() != SpellRegistry.none()) {
                spellSelected = true;
            }
        }

        // ==========================================
        // 1. TITEL (Name des Zaubers)
        // ==========================================
        MutableComponent title = this.trackedSelectedIndex < 0 ? Component.translatable("ui.irons_spellbooks.no_selection") 
            : spellSelected ? spellData.getSpell().getDisplayName(screen.getMinecraft().player).copy() 
            : Component.translatable("ui.irons_spellbooks.empty_slot");

        var titleLines = font.split(title.withStyle(ChatFormatting.UNDERLINE).withStyle(textColor), LORE_PAGE_WIDTH);
        int titleY = y + 10;

        for (FormattedCharSequence line : titleLines) {
            int titleWidth = font.width(line);
            int titleX = x + (LORE_PAGE_WIDTH - titleWidth) / 2;
            guiHelper.drawString(font, line, titleX, titleY, 0xFFFFFF, false);

            // Hover-Tooltip für die Beschreibung des Zaubers
            if (spellSelected && mouseX >= titleX && mouseY >= titleY && mouseX < titleX + titleWidth && mouseY < titleY + font.lineHeight) {
                guiHelper.renderTooltip(font, TooltipsUtils.createSpellDescriptionTooltip(spellData.getSpell(), font), mouseX, mouseY);
            }
            titleY += font.lineHeight;
        }

        int descLine = titleY + 4;

        if (!spellSelected) {
            return; // Nichts weiter zu zeichnen, wenn der Slot leer ist
        }

        AbstractSpell spell = spellData.getSpell();
        int spellLevel = spellData.getLevel();

        // ==========================================
        // 2. SCHULE (Wie gehabt, zentriert)
        // ==========================================
        Component school = spell.getSchoolType().getDisplayName();
        guiHelper.drawString(font, school, x + (LORE_PAGE_WIDTH - font.width(school)) / 2, descLine, 0xFFFFFF, false);
        descLine += font.lineHeight; // Nur EIN einfacher Zeilenumbruch, damit der Zirkel direkt darunter rückt

        // ==========================================
        // 3. ZIRKEL / STATUS (Ersetzt das Vanilla Level)
        // ==========================================
        SpellConfig config = SpellConfigManager.SPELL_CONFIGS.get(spell.getSpellId());
        int effectiveCircle = (config != null ? config.circle : 1) + (spellLevel - 1);
        
        String schoolId = spell.getSchoolType().getId().toString();
        double reqKnowledge = SpellCastHandler.getRequiredKnowledge(effectiveCircle);
        double curKnowledge = SpellCastHandler.getPlayerKnowledge(screen.getMinecraft().player, schoolId);

        String reqStr = String.format(java.util.Locale.ROOT, "%.1f", reqKnowledge);
        String curStr = String.format(java.util.Locale.ROOT, "%.1f", curKnowledge);

        // Zeile 1: Benötigtes Wissen + Zirkel
        MutableComponent reqLine = Component.translatable("ui.stones_irons_bridge.req_knowledge_short", reqStr, effectiveCircle).withStyle(ChatFormatting.GOLD);
        var reqLines = font.split(reqLine, LORE_PAGE_WIDTH - margin * 2);
        for (FormattedCharSequence line : reqLines) {
            guiHelper.drawString(font, line, x + (LORE_PAGE_WIDTH - font.width(line)) / 2, descLine, 0xFFFFFF, false);
            descLine += font.lineHeight;
        }

        // Zeile 2: Aktuelles Wissen (Grün oder Rot)
        MutableComponent curLine = Component.translatable("ui.stones_irons_bridge.cur_knowledge_short", curStr);
        if (curKnowledge >= reqKnowledge) {
            curLine.withStyle(ChatFormatting.GREEN);
        } else {
            curLine.withStyle(ChatFormatting.RED);
        }

        var curLines = font.split(curLine, LORE_PAGE_WIDTH - margin * 2);
        for (FormattedCharSequence line : curLines) {
            guiHelper.drawString(font, line, x + (LORE_PAGE_WIDTH - font.width(line)) / 2, descLine, 0xFFFFFF, false);
            descLine += font.lineHeight;
        }
        descLine += 4; // Etwas Platz vor dem Mana-Block

        // ==========================================
        // 4. MANA, CAST TIME, COOLDOWN, UNIQUE INFO
        // ==========================================
        
        // Mana
        Component manaLabel = Component.translatable("ui.irons_spellbooks.mana_cost", Component.literal(String.valueOf(spell.getManaCost(spellLevel))).withStyle(Style.EMPTY.withColor(0x0044a9))).withStyle(textColor);
        for(var line : font.split(manaLabel, LORE_PAGE_WIDTH - margin * 2)) { guiHelper.drawString(font, line, x + margin, descLine, 0xFFFFFF, false); descLine += font.lineHeight; }

        // Cast Time
        Component castTimeLabel = TooltipsUtils.getCastTimeComponent(spell.getCastType(), Utils.timeFromTicks(spell.getEffectiveCastTime(spellLevel, null), 1)).withStyle(textColor);
        for(var line : font.split(castTimeLabel, LORE_PAGE_WIDTH - margin * 2)) { guiHelper.drawString(font, line, x + margin, descLine, 0xFFFFFF, false); descLine += font.lineHeight; }

        // Cooldown
        Component cooldownLabel = Component.translatable("ui.irons_spellbooks.cooldown", Component.literal(Utils.timeFromTicks(spell.getSpellCooldown(), 1)).withStyle(Style.EMPTY.withColor(0x115511))).withStyle(textColor);
        for(var line : font.split(cooldownLabel, LORE_PAGE_WIDTH - margin * 2)) { guiHelper.drawString(font, line, x + margin, descLine, 0xFFFFFF, false); descLine += font.lineHeight; }

        // Unique Info (z.B. Schaden, Heilung)
        for (Component component : spell.getUniqueInfo(spellLevel, null)) {
            for(var line : font.split(component.copy().withStyle(textColor), LORE_PAGE_WIDTH - margin * 2)) {
                guiHelper.drawString(font, line, x + margin, descLine, 0xFFFFFF, false);
                descLine += font.lineHeight;
            }
        }

        // ==========================================
        // 5. REAGENZIENKOSTEN
        // ==========================================
        // PRÜFUNG DES CLIENT CACHES
        if (ClientServerSettingsCache.useReagents && config != null && config.reagents != null && !config.reagents.isEmpty()) {
            descLine += 2; // Minimaler Absatz für die Reagenzien, um Platz zu sparen
            for (Reagent r : config.reagents) {
                Item reqItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation(r.item));
                MutableComponent itemName = reqItem != null ? reqItem.getDescription().copy() : Component.literal(r.item);
                
                Component reagentLine = Component.translatable("ui.stones_irons_bridge.reagent_format", r.count, itemName).withStyle(ChatFormatting.RED);
                
                for(var line : font.split(reagentLine, LORE_PAGE_WIDTH - margin * 2)) {
                    guiHelper.drawString(font, line, x + margin, descLine, 0xFFFFFF, false);
                    descLine += font.lineHeight;
                }
            }
        }
    }
}