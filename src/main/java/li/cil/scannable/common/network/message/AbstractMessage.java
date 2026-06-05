package li.cil.scannable.common.network.message;

import dev.architectury.networking.NetworkManager;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public interface AbstractMessage extends CustomPacketPayload {
    void handleMessage(final NetworkManager.PacketContext context);
}
