package li.cil.scannable.common.network;

import dev.architectury.networking.NetworkManager;
import li.cil.scannable.common.network.message.AbstractMessage;
import li.cil.scannable.common.network.message.RemoveConfiguredModuleItemAtMessage;
import li.cil.scannable.common.network.message.SetConfiguredModuleItemAtMessage;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public final class Network {
    public static void initialize() {
        NetworkManager.registerReceiver(NetworkManager.Side.C2S,
            SetConfiguredModuleItemAtMessage.TYPE, SetConfiguredModuleItemAtMessage.STREAM_CODEC,
            (message, context) -> context.queue(() -> message.handleMessage(context)));
        NetworkManager.registerReceiver(NetworkManager.Side.C2S,
            RemoveConfiguredModuleItemAtMessage.TYPE, RemoveConfiguredModuleItemAtMessage.STREAM_CODEC,
            (message, context) -> context.queue(() -> message.handleMessage(context)));
    }

    @OnlyIn(Dist.CLIENT)
    public static void sendToServer(final AbstractMessage message) {
        NetworkManager.sendToServer(message);
    }

    private Network() {
    }
}
