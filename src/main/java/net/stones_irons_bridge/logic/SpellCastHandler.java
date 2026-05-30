package net.stones_irons_bridge.logic;

import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.stones.StonesMod;
import net.stones_irons_bridge.StonesIronsBridge;
import net.stones_irons_bridge.config.SpellConfigManager;
import net.stones_irons_bridge.config.SpellConfigManager.Reagent;
import net.stones_irons_bridge.config.SpellConfigManager.SpellConfig;
import net.stones_irons_bridge.config.BridgeServerConfig;
import net.stones.enchantment.behavior.TriggerType;

import java.util.List;

public class SpellCastHandler {

    private static final TriggerType ON_SPELL_PRE_CAST = TriggerType.register("ON_SPELL_PRE_CAST");
    private static final TriggerType ON_SPELL_CAST = TriggerType.register("ON_SPELL_CAST");
    private static final ThreadLocal<Boolean> IS_MILESTONE_TRIGGERING = ThreadLocal.withInitial(() -> false);
	
    // ==========================================
    // CHAOSMAGIE-POOL (SORCERER)
    // ==========================================
    private static final String[] WILD_MAGIC_POOL = {
        "irons_spellbooks:magic_missile",
        "irons_spellbooks:blood_needles",
        "irons_spellbooks:poison_splash",
        "irons_spellbooks:heal",
        "irons_spellbooks:invisibility",
        "irons_spellbooks:electrocute",
        "irons_spellbooks:wisp",
        "irons_spellbooks:blight"
    };

    // ==========================================
    // LOGIK: SERVER-SEITIGES CASTEN
    // ==========================================
    public static void executeSpellOnServer(ServerPlayer player, String spellId) {
        io.redspace.ironsspellbooks.api.magic.SpellSelectionManager manager = new io.redspace.ironsspellbooks.api.magic.SpellSelectionManager(player);
        for (var spellSlot : manager.getAllSpells()) {
            if (spellSlot != null && spellSlot.spellData != null && spellSlot.spellData.getSpell() != null) {
                if (spellSlot.spellData.getSpell().getSpellId().equals(spellId)) {
                    AbstractSpell spell = spellSlot.spellData.getSpell();
                    int level = spellSlot.spellData.getLevel();
                    spell.attemptInitiateCast(ItemStack.EMPTY, level, player.serverLevel(), player, CastSource.SPELLBOOK, true, spellSlot.slot);
                    return; 
                }
            }
        }
    }

    // ==========================================
    // LOGIK: WISSENS-ABFRAGE (NEU)
    // ==========================================
    public static double getRequiredKnowledge(int circle) {
        if (circle <= 1) return 0.0;
        // Die progressive Meister-Kurve: Zirkel 8 = 100.0
        return 2.04 * Math.pow(circle - 1, 2);
    }

    public static double getPlayerKnowledge(Player player, String schoolId) {
        if (player == null) return 0.0;
        String cleanSchool = schoolId.replace("irons_spellbooks:", "");
        
        // Dynamisch das richtige Attribut aus der neuen Registry holen
        var attrRef = net.stones_irons_bridge.init.StonesIronsAttributes.KNOWLEDGE_ATTRIBUTES.get(cleanSchool);
        
        if (attrRef != null && attrRef.isPresent()) {
            var inst = player.getAttribute(attrRef.get());
            if (inst != null) {
                return inst.getValue();
            }
        }
        return 0.0;
    }

    public static double calculateBaseSuccess(double requiredKnowledge, double currentKnowledge) {
        // Wahre Meisterschaft: Anforderung erreicht = 100%
        if (currentKnowledge >= requiredKnowledge) return 100.0;
        
        // Direktes Punkte-Defizit berechnen
        double deficit = requiredKnowledge - currentKnowledge;
        return 100.0 - (deficit * 2.5); // 1 fehlender Punkt = -2.5% Erfolgschance
    }

    public static double calculateSuccessChance(double requiredKnowledge, double currentKnowledge, double luck) {
        if (currentKnowledge >= requiredKnowledge) return 100.0;
        
        double baseSuccess = calculateBaseSuccess(requiredKnowledge, currentKnowledge);
        
        // Der Sorcerer-Cap: Wahre Meisterschaft lässt sich ab 85% nicht durch Glück erzwingen
        if (baseSuccess >= 85.0) return Math.max(0.0, Math.min(100.0, baseSuccess));
        
        // Glück aufaddieren, aber strikt bei 85% kappen (1 Luck = +10%)
        double luckBonus = luck * 10.0; 
        return Math.max(0.0, Math.min(85.0, baseSuccess + luckBonus));
    }

    // ==========================================
    // LOGIK: REAGENZIEN PRÜFEN / KONSUMIEREN
    // ==========================================
    public static boolean hasReagents(Player player, List<Reagent> requirements) {
        for (Reagent req : requirements) {
            if (!hasReagent(player, req)) return false;
        }
        return true;
    }

    public static boolean hasReagent(Player player, Reagent req) {
        if (player == null) return false;
        Inventory inv = player.getInventory();
        ResourceLocation itemLoc = new ResourceLocation(req.item);
        int countFound = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(itemLoc)) {
                countFound += stack.getCount();
            }
        }
        return countFound >= req.count;
    }

    public static void consumeReagents(Player player, List<Reagent> requirements) {
        Inventory inv = player.getInventory();
        for (Reagent req : requirements) {
            ResourceLocation itemLoc = new ResourceLocation(req.item);
            int remainingToConsume = req.count;
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(itemLoc)) {
                    int toTake = Math.min(stack.getCount(), remainingToConsume);
                    stack.shrink(toTake);
                    remainingToConsume -= toTake;
                    if (remainingToConsume <= 0) break;
                }
            }
        }
    }

    // ==========================================
    // EVENTS: FIZZLE & WILD MAGIC
    // ==========================================
    @SubscribeEvent
    public void onSpellPreCast(SpellPreCastEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
       
        if (event.getCastSource() == CastSource.SCROLL) return;

        SpellConfig config = SpellConfigManager.SPELL_CONFIGS.get(event.getSpellId());
        if (config == null) return;

        AbstractSpell spell = SpellRegistry.getSpell(event.getSpellId());
        if (spell == null) return;

        if (!player.level().isClientSide() && player instanceof ServerPlayer serverPlayer) {
            if (!IS_MILESTONE_TRIGGERING.get()) {
                IS_MILESTONE_TRIGGERING.set(true);
                try {
                    // Übermittelt den Trigger als hocheffizientes TriggerType-Objekt!
                    net.stones.event.MilestoneEventHandler.executeMilestones(serverPlayer, ON_SPELL_PRE_CAST, event);
                } finally {
                    IS_MILESTONE_TRIGGERING.set(false);
                }
            }
        }    
		
        // --- BUFF CHECK (Meilensteine) ---
        boolean hasOverdrive = player.hasEffect(StonesIronsBridge.OVERDRIVE.get());
        boolean hasClearcast = player.hasEffect(StonesIronsBridge.CLEARCAST.get());

        // 1. Harte Blockade für Reagenzien (Wird übersprungen, wenn Server-Config "useReagents" auf false ist)
        if (BridgeServerConfig.useReagents && !hasOverdrive && !hasClearcast && config.reagents != null && !hasReagents(player, config.reagents)) {
            event.setCanceled(true);
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.displayClientMessage(Component.translatable("message.stones_irons_bridge.missing_reagents").withStyle(ChatFormatting.RED), false);
                serverPlayer.playNotifySound(StonesIronsBridge.SPELL_FIZZLE.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            }
            return;
        }

        // 2. Wissen und Erfolgschance berechnen
        int spellLevel = event.getSpellLevel();
        int effectiveCircle = config.circle + (spellLevel - 1);
        String schoolId = spell.getSchoolType().getId().toString();
        
        double requiredKnowledge = getRequiredKnowledge(effectiveCircle);
        double currentKnowledge = getPlayerKnowledge(player, schoolId);
        double luck = player.getAttributeValue(Attributes.LUCK);
        
        double baseSuccess = calculateBaseSuccess(requiredKnowledge, currentKnowledge);
        double successChance = calculateSuccessChance(requiredKnowledge, currentKnowledge, luck);

        // 3. FIZZLE WÜRFELN
        if (currentKnowledge < requiredKnowledge && !hasOverdrive) {
            double roll = player.getRandom().nextFloat() * 100.0;
            
            // Wenn der Wurf größer-gleich der Erfolgschance ist -> Fehlschlag!
            if (roll >= successChance) {
                event.setCanceled(true);
                if (player instanceof ServerPlayer serverPlayer) {
                    // Bei Fail die Reagenzien abziehen (Nur wenn aktiviert)
                    if (BridgeServerConfig.useReagents && !hasClearcast && config.reagents != null) {
                        consumeReagents(player, config.reagents);
                    }
                    serverPlayer.playNotifySound(StonesIronsBridge.SPELL_FIZZLE.get(), SoundSource.PLAYERS, 1.0F, 1.0F);

                    // Miscast-Check: Wenn das Basis-Wissen extrem niedrig ist (< 50% Basis-Erfolg)
                    if (baseSuccess < 50.0) {
                        // Der Spieler würfelt noch einmal für die Miscast-Strafe
                        if (luck > 0 && player.getRandom().nextFloat() < 0.5f) {
                            serverPlayer.displayClientMessage(Component.literal("§dWild Magic Surge! Die Energie entlädt sich unkontrolliert!"), true);
                            triggerWildMagic(serverPlayer);
                        } else {
                            serverPlayer.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 1));
                            serverPlayer.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 1));
                            serverPlayer.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
                            serverPlayer.displayClientMessage(Component.literal("§cKatastrophaler arkaner Rückschlag!"), true);
                        }
                    } else {
                        // Normaler Fehlschlag (Keine Lebensgefahr, nur Slow)
                        serverPlayer.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
                        serverPlayer.displayClientMessage(Component.literal("§eDer Zauber ist fehlgeschlagen..."), true);
                    }
                }
            }
        }
    }

    private void triggerWildMagic(ServerPlayer player) {
        String randomSpellId = WILD_MAGIC_POOL[player.getRandom().nextInt(WILD_MAGIC_POOL.length)];
        AbstractSpell wildSpell = SpellRegistry.getSpell(randomSpellId);
        if (wildSpell != null && !wildSpell.getSpellId().equals("irons_spellbooks:none")) {
            wildSpell.attemptInitiateCast(ItemStack.EMPTY, 1, player.serverLevel(), player, CastSource.SCROLL, false, "wild_magic");
        }
    }

    @SubscribeEvent
    public void onSpellOnCast(SpellOnCastEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        
        if (event.getCastSource() == CastSource.SCROLL || event.getCastSource().toString().equals("wild_magic")) return;

        AbstractSpell spell = SpellRegistry.getSpell(event.getSpellId());
        SpellConfig config = SpellConfigManager.SPELL_CONFIGS.get(event.getSpellId());
   
        // --- BUFF CHECK ---
        boolean hasOverdrive = player.hasEffect(StonesIronsBridge.OVERDRIVE.get());
        boolean hasClearcast = player.hasEffect(StonesIronsBridge.CLEARCAST.get());
        boolean hasQuickcast = player.hasEffect(StonesIronsBridge.QUICKCAST.get());

        // --- 1. ARKANER OVERDRIVE KONSUMIEREN ---
        if (hasOverdrive) {
            event.setManaCost(0); // Mana-Kosten auf 0 setzen
            player.removeEffect(StonesIronsBridge.OVERDRIVE.get());
            // Reagenzien werden hier NICHT abgezogen, Zauber war komplett gratis!
        } else {
            // --- 2. CLEARCAST (Keine Reagenzien) ---
            if (BridgeServerConfig.useReagents && !hasClearcast && config != null && config.reagents != null) {
                if (!player.level().isClientSide()) {
                    if (hasReagents(player, config.reagents)) {
                        consumeReagents(player, config.reagents);
                    }
                }
            }
        }

        // --- 3. QUICKCAST (Cooldown Reset) ---
        if (hasQuickcast && !player.level().isClientSide()) {
            if (player instanceof ServerPlayer serverPlayer) {
                // Wir nutzen einen Server-Work-Tick, um den Cooldown direkt NACH dem Cast wieder zu löschen.
                // Iron's Spells setzt den Cooldown teilweise erst sehr spät in der Aufrufkette.
                StonesMod.queueServerWork(1, () -> {
                    MagicData magicData = MagicData.getPlayerMagicData(serverPlayer);
                    if (magicData != null && magicData.getPlayerCooldowns() != null) {
                        magicData.getPlayerCooldowns().removeCooldown(spell.getSpellId());
                    }
                });
            }
        }
	
		if (!player.level().isClientSide() && player instanceof ServerPlayer serverPlayer) {
			if (!IS_MILESTONE_TRIGGERING.get()) {
				IS_MILESTONE_TRIGGERING.set(true);
				try {
					// Übermittelt den Trigger als hocheffizientes TriggerType-Objekt!
					net.stones.event.MilestoneEventHandler.executeMilestones(serverPlayer, ON_SPELL_CAST, event);
				} finally {
					IS_MILESTONE_TRIGGERING.set(false);
				}
			}
		}
    }
}