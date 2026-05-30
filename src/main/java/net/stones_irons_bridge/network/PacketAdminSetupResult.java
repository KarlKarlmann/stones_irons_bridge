package net.stones_irons_bridge.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.stones_irons_bridge.StonesIronsBridge;
import net.stones_irons_bridge.config.BridgeServerConfig;

import java.util.function.Supplier;

public class PacketAdminSetupResult {
    public final boolean useReagents;

    public PacketAdminSetupResult(boolean useReagents) {
        this.useReagents = useReagents;
    }

    public PacketAdminSetupResult(FriendlyByteBuf buf) {
        this.useReagents = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(useReagents);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            
            // Sicherheits-Check: Hat dieser Spieler überhaupt Rechte dazu?
            if (sender != null && sender.hasPermissions(2)) {
                BridgeServerConfig.useReagents = this.useReagents;
                BridgeServerConfig.promptAnswered = true;
                BridgeServerConfig.save();
                
                // Wir informieren SOFORT alle eingeloggten Spieler über die neue Regel.
                // DA DER KONSTRUKTOR NUN 3 WERTE ERWARTET (inkl. isAdmin), gehen wir alle Spieler durch,
                // damit nicht aus Versehen jeder Spieler sich selbst auf dem Client als Admin ansieht.
                for (ServerPlayer player : sender.getServer().getPlayerList().getPlayers()) {
                    boolean isPlayerAdmin = player.hasPermissions(2);
                    StonesIronsBridge.PACKET_HANDLER.send(PacketDistributor.PLAYER.with(() -> player), 
                        new PacketSyncServerSettings(BridgeServerConfig.useReagents, true, isPlayerAdmin));
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}