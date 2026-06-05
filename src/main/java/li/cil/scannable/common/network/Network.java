package li.cil.scannable.common.network;

import li.cil.scannable.api.API;
import li.cil.scannable.common.neoforge.ModEventBus;
import li.cil.scannable.common.network.message.AbstractMessage;
import li.cil.scannable.common.network.message.RemoveConfiguredModuleItemAtMessage;
import li.cil.scannable.common.network.message.SetConfiguredModuleItemAtMessage;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class Network {
    public static void initialize() {
        ModEventBus.INSTANCE.addListener(Network::onRegisterPayloadHandlers);
    }

    private static void onRegisterPayloadHandlers(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(API.MOD_ID);
        registrar.playToServer(
            SetConfiguredModuleItemAtMessage.TYPE, SetConfiguredModuleItemAtMessage.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> payload.handleMessage(context)));
        registrar.playToServer(
            RemoveConfiguredModuleItemAtMessage.TYPE, RemoveConfiguredModuleItemAtMessage.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> payload.handleMessage(context)));
    }

    @OnlyIn(Dist.CLIENT)
    public static void sendToServer(final AbstractMessage message) {
        PacketDistributor.sendToServer(message);
    }

    private Network() {
    }
}
