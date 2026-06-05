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

public record SetConfiguredModuleItemAtMessage(int windowId, int index, Identifier value) implements AbstractMessage {
    public static final CustomPacketPayload.Type<SetConfiguredModuleItemAtMessage> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(API.MOD_ID, "set_module_item"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetConfiguredModuleItemAtMessage> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, SetConfiguredModuleItemAtMessage::windowId,
        ByteBufCodecs.VAR_INT, SetConfiguredModuleItemAtMessage::index,
        Identifier.STREAM_CODEC, SetConfiguredModuleItemAtMessage::value,
        SetConfiguredModuleItemAtMessage::new);

    // --------------------------------------------------------------------- //

    @Override
    public void handleMessage(final IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player &&
            player.containerMenu != null &&
            player.containerMenu.containerId == windowId &&
            player.containerMenu instanceof AbstractModuleContainerMenu) {
            ((AbstractModuleContainerMenu) player.containerMenu).setItemAt(index, value);
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
