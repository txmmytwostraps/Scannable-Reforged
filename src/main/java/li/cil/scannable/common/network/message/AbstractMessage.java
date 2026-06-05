package li.cil.scannable.common.network.message;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public interface AbstractMessage extends CustomPacketPayload {
    void handleMessage(final IPayloadContext context);
}
