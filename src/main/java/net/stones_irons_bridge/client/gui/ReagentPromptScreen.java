package net.stones_irons_bridge.client.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.stones_irons_bridge.StonesIronsBridge;
import net.stones_irons_bridge.network.PacketAdminSetupResult;

import java.util.List;

public class ReagentPromptScreen extends Screen {

    private static final ResourceLocation WIZARD_PORTRAIT = new ResourceLocation(StonesIronsBridge.MODID, "textures/gui/server_wizard.png");

    public ReagentPromptScreen() {
        super(Component.translatable("gui.stones_irons_bridge.reagent.title"));
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;
        int bottomY = this.height / 2 + 55;

        // Button JA
        this.addRenderableWidget(Button.builder(Component.translatable("gui.stones_irons_bridge.reagent.btn.yes").withStyle(ChatFormatting.GREEN), btn -> {
            StonesIronsBridge.PACKET_HANDLER.sendToServer(new PacketAdminSetupResult(true));
            this.minecraft.setScreen(null); 
        }).bounds(centerX - 135, bottomY, 130, 20).build());

        // Button NEIN
        this.addRenderableWidget(Button.builder(Component.translatable("gui.stones_irons_bridge.reagent.btn.no").withStyle(ChatFormatting.RED), btn -> {
            StonesIronsBridge.PACKET_HANDLER.sendToServer(new PacketAdminSetupResult(false));
            this.minecraft.setScreen(null); 
        }).bounds(centerX + 5, bottomY, 130, 20).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        int centerX = this.width / 2;
        int textTop = this.height / 2 - 80;
        int textCenterX = centerX + 20; // Text leicht nach rechts rücken für das Portrait

        guiGraphics.drawCenteredString(this.font, Component.translatable("gui.stones_irons_bridge.reagent.header").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), textCenterX, textTop, 0xFFFFFF);
        
        // Frage aufteilen, damit sie gut in den neuen Platz passt
        Component question = Component.translatable("gui.stones_irons_bridge.reagent.question");
        List<FormattedCharSequence> qLines = this.font.split(question, 240);
        int lineY = textTop + 20;
        for (FormattedCharSequence line : qLines) {
            guiGraphics.drawString(this.font, line, textCenterX - (this.font.width(line) / 2), lineY, 0xFFFFFF, false);
            lineY += this.font.lineHeight + 2;
        }

        Component infoText = Component.translatable("gui.stones_irons_bridge.reagent.info").withStyle(ChatFormatting.GRAY);

        List<FormattedCharSequence> lines = this.font.split(infoText, 240);
        lineY += 5;
        for (FormattedCharSequence line : lines) {
            guiGraphics.drawString(this.font, line, textCenterX - (this.font.width(line) / 2), lineY, 0xFFFFFF, false);
            lineY += this.font.lineHeight + 2;
        }

        int portraitSize = 72;
        // Portrait auf der linken Seite rendern
        guiGraphics.blit(WIZARD_PORTRAIT, centerX - 160, textTop + 10, 0, 0, portraitSize, portraitSize, portraitSize, portraitSize);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
}