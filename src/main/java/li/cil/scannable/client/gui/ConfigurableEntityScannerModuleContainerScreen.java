package li.cil.scannable.client.gui;

import li.cil.scannable.common.config.Strings;
import li.cil.scannable.common.container.EntityModuleContainerMenu;
import li.cil.scannable.common.item.ConfigurableEntityScannerModuleItem;
import li.cil.scannable.common.network.Network;
import li.cil.scannable.common.network.message.SetConfiguredModuleItemAtMessage;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

import java.util.List;

public class ConfigurableEntityScannerModuleContainerScreen extends AbstractConfigurableScannerModuleContainerScreen<EntityModuleContainerMenu, EntityType<?>> {
    public ConfigurableEntityScannerModuleContainerScreen(final EntityModuleContainerMenu container, final Inventory inventory, final Component title) {
        super(container, inventory, title, Strings.GUI_ENTITIES_LIST_CAPTION);
    }

    // --------------------------------------------------------------------- //

    @Override
    protected List<EntityType<?>> getConfiguredItems(final ItemStack stack) {
        return ConfigurableEntityScannerModuleItem.getEntityTypes(stack);
    }

    @Override
    protected Component getItemName(final EntityType<?> entityType) {
        return entityType.getDescription();
    }

    @Override
    protected void renderConfiguredItem(final GuiGraphicsExtractor graphics, final EntityType<?> entityType, final int x, final int y) {
        // TODO(Phase 3): render a live entity preview. GUI entity rendering was reworked in 1.21.6
        // (graphics.pose() is now a 2D Matrix3x2fStack; entity rendering goes through a submit /
        // render-state path). Stubbed to a blank slot for the launchable build; configured entities
        // are still listed via the hover tooltip.
    }

    @Override
    protected void configureItemAt(final ItemStack stack, final int slot, final ItemStack value) {
        if (value.getItem() instanceof SpawnEggItem) {
            final EntityType<?> entityType = ((SpawnEggItem) value.getItem()).getType(value);
            BuiltInRegistries.ENTITY_TYPE.getResourceKey(entityType).ifPresent(entityTypeResourceKey ->
                    Network.sendToServer(new SetConfiguredModuleItemAtMessage(menu.containerId, slot, entityTypeResourceKey.identifier())));
        }
    }
}
