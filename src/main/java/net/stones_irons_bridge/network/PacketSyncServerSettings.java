package net.stones_irons_bridge.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.stones_irons_bridge.client.ClientServerSettingsCache;

import java.util.function.Supplier;

public class PacketSyncServerSettings {
    public final boolean useReagents;
    public final boolean promptAnswered;
    public final boolean isAdmin; // NEU: Admin Status hinzugefügt

    public PacketSyncServerSettings(boolean useReagents, boolean promptAnswered, boolean isAdmin) {
        this.useReagents = useReagents;
        this.promptAnswered = promptAnswered;
        this.isAdmin = isAdmin;
    }

    public PacketSyncServerSettings(FriendlyByteBuf buf) {
        this.useReagents = buf.readBoolean();
        this.promptAnswered = buf.readBoolean();
        this.isAdmin = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(useReagents);
        buf.writeBoolean(promptAnswered);
        buf.writeBoolean(isAdmin);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientServerSettingsCache.useReagents = this.useReagents;
            ClientServerSettingsCache.promptAnswered = this.promptAnswered;
            ClientServerSettingsCache.isAdmin = this.isAdmin; // Füllt den lokalen Cache aus dem Paket
        });
        ctx.get().setPacketHandled(true);
    }
}