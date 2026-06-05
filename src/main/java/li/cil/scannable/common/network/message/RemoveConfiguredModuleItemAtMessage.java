package li.cil.scannable.common.network.message;

import li.cil.scannable.api.API;
import li.cil.scannable.common.container.AbstractModuleContainerMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RemoveConfiguredModuleItemAtMessage(int windowId, int index) implements AbstractMessage {
    public static final CustomPacketPayload.Type<RemoveConfiguredModuleItemAtMessage> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(API.MOD_ID, "remove_module_item"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RemoveConfiguredModuleItemAtMessage> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, RemoveConfiguredModuleItemAtMessage::windowId,
        ByteBufCodecs.VAR_INT, RemoveConfiguredModuleItemAtMessage::index,
        RemoveConfiguredModuleItemAtMessage::new);

    // --------------------------------------------------------------------- //

    @Override
    public void handleMessage(final IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player &&
            player.containerMenu != null &&
            player.containerMenu.containerId == windowId &&
            player.containerMenu instanceof AbstractModuleContainerMenu) {
            ((AbstractModuleContainerMenu) player.containerMenu).removeItemAt(index);
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
