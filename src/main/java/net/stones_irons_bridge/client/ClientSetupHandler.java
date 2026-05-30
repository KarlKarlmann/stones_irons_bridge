package net.stones_irons_bridge.client;

import com.mojang.datafixers.util.Either;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.item.Scroll;
import io.redspace.ironsspellbooks.player.ClientMagicData;
import io.redspace.ironsspellbooks.api.magic.SpellSelectionManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;
import net.stones.features.ActionSystem;
import net.stones_irons_bridge.StonesIronsBridge;
import net.stones_irons_bridge.config.SpellConfigManager;
import net.stones_irons_bridge.config.SpellConfigManager.Reagent;
import net.stones_irons_bridge.config.SpellConfigManager.SpellConfig;
import net.stones_irons_bridge.logic.SpellCastHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ClientSetupHandler {

    // ==========================================
    // DER SELF-UNREGISTERING TASK (0% Performance Impact)
    // ==========================================
    public static class LoginPromptTask {
        private boolean registered = false;

        public void register() {
            if (!registered) {
                MinecraftForge.EVENT_BUS.register(this);
                registered = true;
            }
        }

        @SubscribeEvent
        public void onTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            
            Minecraft mc = Minecraft.getInstance();
            
            // Wir warten 100% zuverlässig, bis der Spieler in der Welt steht und der Lade-Screen weg ist
            if (mc.player != null && mc.level != null && mc.screen == null) {
                
                // 1. Lokale GUI Frage
                if (!net.stones_irons_bridge.config.BridgeSettings.guiPromptAnswered) {
                    mc.setScreen(new net.stones_irons_bridge.client.gui.GuiPromptScreen());
                    return; // Warten, bis der Screen zu ist
                } 
                // 2. Server Reagenzien Frage (Cache weiß vom Paket, ob wir Admin sind!)
                else if (ClientServerSettingsCache.isAdmin && !ClientServerSettingsCache.promptAnswered) {
                    mc.setScreen(new net.stones_irons_bridge.client.gui.ReagentPromptScreen());
                    return; // Warten, bis der Screen zu ist
                }

                // WENN WIR HIER ANKOMMEN, IST ALLES BEANTWORTET!
                // Der Task löscht sich komplett aus dem Spiel und verbraucht nie wieder einen Tick.
                MinecraftForge.EVENT_BUS.unregister(this);
                registered = false;
            }
        }
    }

    // Die Instanz unseres Tasks
    public static final LoginPromptTask PROMPT_TASK = new LoginPromptTask();

    public static void injectSpellsIntoCache(List<ResourceLocation> actionCache, Map<ResourceLocation, Integer> levelCache) {
        SpellSelectionManager manager = ClientMagicData.getSpellSelectionManager();
        for (var spellSlot : manager.getAllSpells()) {
            if (spellSlot != null && spellSlot.spellData != null) {
                AbstractSpell spell = spellSlot.spellData.getSpell();
                if (spell != null && !spell.getSpellId().equals("irons_spellbooks:none")) {
                    ResourceLocation spellLoc = new ResourceLocation(spell.getSpellId());
                    if (!actionCache.contains(spellLoc)) actionCache.add(spellLoc);
                    levelCache.put(spellLoc, Math.max(levelCache.getOrDefault(spellLoc, 0), spellSlot.spellData.getLevel()));
                }
            }
        }
    }

    public static List<Either<FormattedText, TooltipComponent>> buildActionTooltip(String spellId, int level) {
        List<Either<FormattedText, TooltipComponent>> tooltip = new ArrayList<>();
        AbstractSpell spell = SpellRegistry.getSpell(spellId);
        if (spell == null || spell.getSpellId().equals("irons_spellbooks:none")) return tooltip;

        Minecraft mc = Minecraft.getInstance();
        SpellConfig config = SpellConfigManager.SPELL_CONFIGS.get(spellId);
        
        tooltip.add(Either.left((FormattedText) spell.getDisplayName(mc.player).copy().withStyle(Style.EMPTY.withUnderlined(true))));

        if (mc.player != null) {
            int spellLevel = spell.getLevelFor(level, mc.player);
            tooltip.add(Either.left((FormattedText) Component.translatable("ui.irons_spellbooks.level", level).withStyle(spell.getRarity(spellLevel).getDisplayName().getStyle())));
            tooltip.add(Either.left((FormattedText) Component.translatable("ui.irons_spellbooks.mana_cost", spell.getManaCost(spellLevel)).withStyle(ChatFormatting.AQUA)));
        }

        tooltip.add(Either.left((FormattedText) Component.literal(" ")));
        tooltip.add(Either.left((FormattedText) Component.translatable(spell.getComponentId() + ".guide").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));

        if (config != null) {
            tooltip.add(Either.left((FormattedText) Component.literal(" ")));
            tooltip.add(Either.left((FormattedText) Component.translatable("tooltip.stones_irons_bridge.magic_requirements").withStyle(ChatFormatting.GOLD)));

            String schoolId = spell.getSchoolType().getId().toString();
            int spellLevel = spell.getLevelFor(level, mc.player);
            int effectiveCircle = config.circle + (spellLevel - 1);
            
            double reqKnowledge = SpellCastHandler.getRequiredKnowledge(effectiveCircle);
            double curKnowledge = SpellCastHandler.getPlayerKnowledge(mc.player, schoolId);
            double luck = mc.player.getAttributeValue(Attributes.LUCK);

            double baseSuccess = SpellCastHandler.calculateBaseSuccess(reqKnowledge, curKnowledge);
            double successChance = SpellCastHandler.calculateSuccessChance(reqKnowledge, curKnowledge, luck);

            String reqStr = String.format(java.util.Locale.ROOT, "%.1f", reqKnowledge);
            String curStr = String.format(java.util.Locale.ROOT, "%.1f", curKnowledge);
            String chanceStr = String.format(java.util.Locale.ROOT, "%.1f%%", successChance);

            tooltip.add(Either.left((FormattedText) Component.translatable("ui.stones_irons_bridge.req_label").withStyle(ChatFormatting.GRAY)
                    .append(Component.translatable("ui.stones_irons_bridge.req_value", reqStr).withStyle(ChatFormatting.GOLD))
                    .append(Component.translatable("ui.stones_irons_bridge.req_circle", effectiveCircle).withStyle(ChatFormatting.DARK_GRAY))));

            tooltip.add(Either.left((FormattedText) Component.translatable("ui.stones_irons_bridge.cur_label").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(curStr).withStyle(curKnowledge >= reqKnowledge ? ChatFormatting.GREEN : ChatFormatting.RED))));

            tooltip.add(Either.left((FormattedText) Component.translatable("ui.stones_irons_bridge.chance_label").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(chanceStr).withStyle(successChance >= 100.0 ? ChatFormatting.GREEN : (successChance > 50.0 ? ChatFormatting.YELLOW : ChatFormatting.RED)))));

            if (Screen.hasShiftDown()) {
                if (curKnowledge < reqKnowledge) {
                    double deficit = reqKnowledge - curKnowledge;
                    String deficitStr = String.format(java.util.Locale.ROOT, "%.1f", deficit);
                    String baseSuccStr = String.format(java.util.Locale.ROOT, "%.1f", baseSuccess);
                    
                    tooltip.add(Either.left((FormattedText) Component.literal("  » ").withStyle(ChatFormatting.DARK_GRAY)
                            .append(Component.translatable("tooltip.stones_irons_bridge.mastery_deficit", baseSuccStr, deficitStr).withStyle(ChatFormatting.GRAY))));
                    
                    MutableComponent luckLine = Component.literal("  » ").withStyle(ChatFormatting.DARK_GRAY);
                    if (baseSuccess >= 85.0) {
                        luckLine.append(Component.translatable("tooltip.stones_irons_bridge.luck_zero").withStyle(ChatFormatting.GOLD))
                                .append(Component.translatable("tooltip.stones_irons_bridge.luck_limited").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
                    } else {
                        double rawLuckBonus = luck * 10.0;
                        double effectiveLuckBonus = Math.min(85.0 - baseSuccess, rawLuckBonus);
                        String effLuckStr = String.format(java.util.Locale.ROOT, "%.1f", effectiveLuckBonus);
                        luckLine.append(Component.translatable("tooltip.stones_irons_bridge.luck_bonus", effLuckStr).withStyle(ChatFormatting.GOLD));
                        
                        if (baseSuccess + rawLuckBonus > 85.0) {
                            luckLine.append(Component.translatable("tooltip.stones_irons_bridge.luck_capped").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
                        }
                    }
                    tooltip.add(Either.left((FormattedText) luckLine));
                    
                    double failRisk = 100.0 - successChance;
                    double miscastRisk = baseSuccess < 50.0 ? failRisk : 0.0;
                    
                    if (miscastRisk > 0) {
                        String miscastStr = String.format(java.util.Locale.ROOT, "%.1f", miscastRisk);
                        tooltip.add(Either.left((FormattedText) Component.literal("  » ").withStyle(ChatFormatting.DARK_RED)
                                .append(Component.translatable("tooltip.stones_irons_bridge.miscast_risk", miscastStr).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD))));
                    } else if (failRisk > 0) {
                        String failStr = String.format(java.util.Locale.ROOT, "%.1f", failRisk);
                        tooltip.add(Either.left((FormattedText) Component.literal("  » ").withStyle(ChatFormatting.RED)
                                .append(Component.translatable("tooltip.stones_irons_bridge.fail_risk", failStr).withStyle(ChatFormatting.RED))));
                    }
                } else {
                    tooltip.add(Either.left((FormattedText) Component.literal("  » ").withStyle(ChatFormatting.DARK_GRAY)
                            .append(Component.translatable("tooltip.stones_irons_bridge.mastery_perfect").withStyle(ChatFormatting.AQUA))));
                }
            } else {
                tooltip.add(Either.left((FormattedText) Component.translatable("tooltip.stones_irons_bridge.shift_info").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
            }

            if (ClientServerSettingsCache.useReagents && config.reagents != null && !config.reagents.isEmpty()) {
                List<ReagentStatus> statusList = new ArrayList<>();
                for (Reagent r : config.reagents) {
                    statusList.add(new ReagentStatus(r, SpellCastHandler.hasReagent(mc.player, r)));
                }
                tooltip.add(Either.right(new ReagentsTooltipData(statusList)));
            }
        }
        return tooltip;
    }

    @Mod.EventBusSubscriber(modid = StonesIronsBridge.MODID, value = Dist.CLIENT)
    public static class ClientForgeEvents {
        private static SpellSelectionManager lastSelectionManager = null;

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            
            // NUR noch das Action System bleibt hier im normalen Tick (für flüssiges UI)!
            SpellSelectionManager currentManager = ClientMagicData.getSpellSelectionManager();
            if (currentManager != lastSelectionManager) {
                lastSelectionManager = currentManager;
                ActionSystem.refreshCalculatedActions();
            }
        }

		@SubscribeEvent
		public static void onGatherTooltipComponents(RenderTooltipEvent.GatherComponents event) {
			Minecraft mci = Minecraft.getInstance();
			if (mci.player == null) return;
            ItemStack stack = event.getItemStack();
            if (!ISpellContainer.isSpellContainer(stack)) return;
            
            ISpellContainer spellContainer = ISpellContainer.get(stack);
            AbstractSpell spell = null;
            int spellLevel = 1;
            
            for (int i = 0; i < spellContainer.getMaxSpellCount(); i++) {
                var s = spellContainer.getSpellAtIndex(i);
                if (s != null && s.getSpell() != null && !s.getSpell().getSpellId().equals("irons_spellbooks:none")) {
                    spell = s.getSpell(); 
                    spellLevel = s.getLevel();
                    break;
                }
            }
            if (spell == null) return;

            SpellConfig config = SpellConfigManager.SPELL_CONFIGS.get(spell.getSpellId());
            if (config == null) return;

            List<Either<FormattedText, net.minecraft.world.inventory.tooltip.TooltipComponent>> elements = event.getTooltipElements();
            Minecraft mc = Minecraft.getInstance();
            boolean isScroll = stack.getItem() instanceof Scroll;

            elements.add(Either.left((FormattedText) Component.literal(" ")));
            elements.add(Either.left((FormattedText) Component.translatable(spell.getComponentId() + ".guide").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));

            if (!isScroll) {
                elements.add(Either.left((FormattedText) Component.translatable("tooltip.stones_irons_bridge.magic_requirements").withStyle(ChatFormatting.GOLD)));
            }
            
            String schoolId = spell.getSchoolType().getId().toString();
            int effectiveCircle = config.circle + (spellLevel - 1);
            
            double reqKnowledge = SpellCastHandler.getRequiredKnowledge(effectiveCircle);
            double curKnowledge = SpellCastHandler.getPlayerKnowledge(mc.player, schoolId);
            double luck = mc.player.getAttributeValue(Attributes.LUCK);

            double baseSuccess = SpellCastHandler.calculateBaseSuccess(reqKnowledge, curKnowledge);
            double successChance = SpellCastHandler.calculateSuccessChance(reqKnowledge, curKnowledge, luck);

            String reqStr = String.format(java.util.Locale.ROOT, "%.1f", reqKnowledge);
            String curStr = String.format(java.util.Locale.ROOT, "%.1f", curKnowledge);
            String chanceStr = String.format(java.util.Locale.ROOT, "%.1f%%", successChance);
            
            elements.add(Either.left((FormattedText) Component.translatable("ui.stones_irons_bridge.req_label").withStyle(ChatFormatting.GRAY)
                    .append(Component.translatable("ui.stones_irons_bridge.req_value", reqStr).withStyle(ChatFormatting.GOLD))
                    .append(Component.translatable("ui.stones_irons_bridge.req_circle", effectiveCircle).withStyle(ChatFormatting.DARK_GRAY))));

            elements.add(Either.left((FormattedText) Component.translatable("ui.stones_irons_bridge.cur_label").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(curStr).withStyle(curKnowledge >= reqKnowledge ? ChatFormatting.GREEN : ChatFormatting.RED))));

            elements.add(Either.left((FormattedText) Component.translatable("ui.stones_irons_bridge.chance_label").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(chanceStr).withStyle(successChance >= 100.0 ? ChatFormatting.GREEN : (successChance > 50.0 ? ChatFormatting.YELLOW : ChatFormatting.RED)))));

            if (Screen.hasShiftDown()) {
                if (curKnowledge < reqKnowledge) {
                    double deficit = reqKnowledge - curKnowledge;
                    String deficitStr = String.format(java.util.Locale.ROOT, "%.1f", deficit);
                    String baseSuccStr = String.format(java.util.Locale.ROOT, "%.1f", baseSuccess);
                    
                    elements.add(Either.left((FormattedText) Component.literal("  » ").withStyle(ChatFormatting.DARK_GRAY)
                            .append(Component.translatable("tooltip.stones_irons_bridge.mastery_deficit", baseSuccStr, deficitStr).withStyle(ChatFormatting.GRAY))));
                    
                    MutableComponent luckLine = Component.literal("  » ").withStyle(ChatFormatting.DARK_GRAY);
                    if (baseSuccess >= 85.0) {
                        luckLine.append(Component.translatable("tooltip.stones_irons_bridge.luck_zero").withStyle(ChatFormatting.GOLD))
                                .append(Component.translatable("tooltip.stones_irons_bridge.luck_limited").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
                    } else {
                        double rawLuckBonus = luck * 10.0;
                        double effectiveLuckBonus = Math.min(85.0 - baseSuccess, rawLuckBonus);
                        String effLuckStr = String.format(java.util.Locale.ROOT, "%.1f", effectiveLuckBonus);
                        luckLine.append(Component.translatable("tooltip.stones_irons_bridge.luck_bonus", effLuckStr).withStyle(ChatFormatting.GOLD));
                        
                        if (baseSuccess + rawLuckBonus > 85.0) {
                            luckLine.append(Component.translatable("tooltip.stones_irons_bridge.luck_capped").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
                        }
                    }
                    elements.add(Either.left((FormattedText) luckLine));
                    
                    double failRisk = 100.0 - successChance;
                    double miscastRisk = baseSuccess < 50.0 ? failRisk : 0.0;
                    
                    if (miscastRisk > 0) {
                        String miscastStr = String.format(java.util.Locale.ROOT, "%.1f", miscastRisk);
                        elements.add(Either.left((FormattedText) Component.literal("  » ").withStyle(ChatFormatting.DARK_RED)
                                .append(Component.translatable("tooltip.stones_irons_bridge.miscast_risk", miscastStr).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD))));
                    } else if (failRisk > 0) {
                        String failStr = String.format(java.util.Locale.ROOT, "%.1f", failRisk);
                        elements.add(Either.left((FormattedText) Component.literal("  » ").withStyle(ChatFormatting.RED)
                                .append(Component.translatable("tooltip.stones_irons_bridge.fail_risk", failStr).withStyle(ChatFormatting.RED))));
                    }
                } else {
                    elements.add(Either.left((FormattedText) Component.literal("  » ").withStyle(ChatFormatting.DARK_GRAY)
                            .append(Component.translatable("tooltip.stones_irons_bridge.mastery_perfect").withStyle(ChatFormatting.AQUA))));
                }
            } else {
                elements.add(Either.left((FormattedText) Component.translatable("tooltip.stones_irons_bridge.shift_info").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
            }

            if (ClientServerSettingsCache.useReagents && config.reagents != null && !config.reagents.isEmpty()) {
                List<ReagentStatus> statusList = new ArrayList<>();
                for (Reagent r : config.reagents) {
                    statusList.add(new ReagentStatus(r, SpellCastHandler.hasReagent(Minecraft.getInstance().player, r)));
                }
                elements.add(Either.right(new ReagentsTooltipData(statusList)));
            }
        }
    }

    @Mod.EventBusSubscriber(modid = StonesIronsBridge.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        
        // HIER WIRD DER TASK REGISTRIERT
        @SubscribeEvent
        public static void onClientSetup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
            PROMPT_TASK.register();
        }

        @SubscribeEvent
        public static void registerTooltipComponents(RegisterClientTooltipComponentFactoriesEvent event) {
            event.register(ReagentsTooltipData.class, ReagentsTooltipRenderer::new);
        }
    }

    public record ReagentStatus(Reagent reagent, boolean hasEnough) {}
    public record ReagentsTooltipData(List<ReagentStatus> statuses) implements net.minecraft.world.inventory.tooltip.TooltipComponent {}

    public static class ReagentsTooltipRenderer implements net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent {
        private final List<ReagentStatus> statuses;
        private final List<ItemStack> stacks;

        public ReagentsTooltipRenderer(ReagentsTooltipData data) {
            this.statuses = data.statuses();
            this.stacks = this.statuses.stream()
                .map(rs -> new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation(rs.reagent().item))))
                .toList();
        }

        @Override public int getHeight() { return 18; }
        @Override public int getWidth(net.minecraft.client.gui.Font font) {
            int w = 0;
            for (ReagentStatus rs : statuses) {
                w += (rs.reagent().count <= 3 ? rs.reagent().count * 18 : font.width(rs.reagent().count + "x ") + 18);
                w += font.width(" \u2714") + 8;
            }
            return w;
        }

        @Override public void renderImage(net.minecraft.client.gui.Font font, int x, int y, net.minecraft.client.gui.GuiGraphics gui) {
            int cx = x;
            for (int i = 0; i < statuses.size(); i++) {
                ReagentStatus rs = statuses.get(i);
                if (rs.reagent().count <= 3) {
                    for (int j = 0; j < rs.reagent().count; j++) { gui.renderItem(stacks.get(i), cx, y); cx += 18; }
                } else {
                    String t = rs.reagent().count + "x ";
                    gui.drawString(font, t, cx, y + 4, 0xFFFFFF, false); cx += font.width(t);
                    gui.renderItem(stacks.get(i), cx, y); cx += 18;
                }
                String ind = rs.hasEnough() ? "§a\u2714" : "§c\u2718";
                gui.drawString(font, ind, cx + 2, y + 4, 0xFFFFFF, false);
                cx += font.width(ind) + 10;
            }
        }
    }
}