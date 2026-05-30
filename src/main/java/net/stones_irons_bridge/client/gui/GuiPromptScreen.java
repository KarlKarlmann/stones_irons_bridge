package net.stones_irons_bridge.client.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.stones_irons_bridge.StonesIronsBridge;
import net.stones_irons_bridge.config.BridgeSettings;

public class GuiPromptScreen extends Screen {

    private static final ResourceLocation WIZARD_PORTRAIT = new ResourceLocation(StonesIronsBridge.MODID, "textures/gui/server_wizard.png");

    public GuiPromptScreen() {
        super(Component.translatable("gui.stones_irons_bridge.prompt.title"));
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int btnY = this.height / 2 + 30;

        // Button 1: Stones GUI
        this.addRenderableWidget(Button.builder(Component.translatable("gui.stones_irons_bridge.prompt.btn.stones").withStyle(ChatFormatting.AQUA), btn -> {
            BridgeSettings.useStonesGui = true;
            BridgeSettings.guiPromptAnswered = true;
            BridgeSettings.save();
            unbindIronsKeys(); // Tasten automatisch säubern
            this.minecraft.setScreen(null); // Schließt das Fenster
        }).bounds(centerX - 135, btnY, 130, 20).build());

        // Button 2: Iron's GUI
        this.addRenderableWidget(Button.builder(Component.translatable("gui.stones_irons_bridge.prompt.btn.irons").withStyle(ChatFormatting.GOLD), btn -> {
            BridgeSettings.useStonesGui = false;
            BridgeSettings.guiPromptAnswered = true;
            BridgeSettings.save();
            this.minecraft.setScreen(null); // Schließt das Fenster
        }).bounds(centerX + 5, btnY, 130, 20).build());
    }

    private void unbindIronsKeys() {
        if (this.minecraft == null || this.minecraft.options == null) return;
        boolean optionsChanged = false;
        for (net.minecraft.client.KeyMapping key : this.minecraft.options.keyMappings) {
            if (key.getCategory().contains("irons_spellbooks") && key.isDefault()) {
                key.setKey(com.mojang.blaze3d.platform.InputConstants.UNKNOWN);
                optionsChanged = true;
            }
        }
        if (optionsChanged) {
            this.minecraft.options.save();
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        int centerX = this.width / 2;
        int textTop = this.height / 2 - 60;
        int textCenterX = centerX + 20; // Text leicht nach rechts rücken für das Portrait

        guiGraphics.drawCenteredString(this.font, Component.translatable("gui.stones_irons_bridge.prompt.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), textCenterX, textTop, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font, Component.translatable("gui.stones_irons_bridge.prompt.question"), textCenterX, textTop + 25, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font, Component.translatable("gui.stones_irons_bridge.prompt.info").withStyle(ChatFormatting.GRAY), textCenterX, textTop + 40, 0xFFFFFF);

        int portraitSize = 72;
        // Portrait auf der linken Seite rendern
        guiGraphics.blit(WIZARD_PORTRAIT, centerX - 160, textTop - 5, 0, 0, portraitSize, portraitSize, portraitSize, portraitSize);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
}