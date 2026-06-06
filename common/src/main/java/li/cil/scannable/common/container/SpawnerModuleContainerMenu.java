package li.cil.scannable.common.container;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;

/**
 * The spawner module's config menu. It edits the same {@code ENTITY_TYPES} list as the entity module
 * (so it inherits all editing behaviour) under its own menu type, which lets its screen use a
 * "Spawners" caption.
 */
public final class SpawnerModuleContainerMenu extends EntityModuleContainerMenu {
    public static SpawnerModuleContainerMenu create(final int windowId, final Inventory inventory, final FriendlyByteBuf buffer) {
        final InteractionHand hand = buffer.readEnum(InteractionHand.class);
        return new SpawnerModuleContainerMenu(windowId, inventory, hand);
    }

    public SpawnerModuleContainerMenu(final int windowId, final Inventory inventory, final InteractionHand hand) {
        super(Containers.SPAWNER_MODULE_CONTAINER.get(), windowId, inventory, hand);
    }
}
