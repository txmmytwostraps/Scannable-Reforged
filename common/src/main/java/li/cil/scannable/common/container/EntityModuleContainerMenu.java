package li.cil.scannable.common.container;

import li.cil.scannable.common.item.ConfigurableEntityScannerModuleItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

public class EntityModuleContainerMenu extends AbstractModuleContainerMenu {
    public static EntityModuleContainerMenu create(final int windowId, final Inventory inventory, final FriendlyByteBuf buffer) {
        final InteractionHand hand = buffer.readEnum(InteractionHand.class);
        return new EntityModuleContainerMenu(windowId, inventory, hand);
    }

    // --------------------------------------------------------------------- //

    public EntityModuleContainerMenu(final int windowId, final Inventory inventory, final InteractionHand hand) {
        super(Containers.ENTITY_MODULE_CONTAINER.get(), windowId, inventory, hand);
    }

    // For subclasses (e.g. the spawner module) that edit the same ENTITY_TYPES list under their own
    // menu type, so their screen can use a distinct caption.
    protected EntityModuleContainerMenu(final MenuType<?> type, final int windowId, final Inventory inventory, final InteractionHand hand) {
        super(type, windowId, inventory, hand);
    }

    @Override
    public void removeItemAt(final int index) {
        final ItemStack stack = getPlayer().getItemInHand(getHand());
        ConfigurableEntityScannerModuleItem.removeEntityTypeAt(stack, index);
    }

    @Override
    public void setItemAt(final int index, final ResourceLocation name) {
        final ItemStack stack = getPlayer().getItemInHand(getHand());
        BuiltInRegistries.ENTITY_TYPE.getOptional(name).ifPresent(type ->
            ConfigurableEntityScannerModuleItem.setEntityTypeAt(stack, index, type));
    }
}
