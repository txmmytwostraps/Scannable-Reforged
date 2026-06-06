package li.cil.scannable.client.gui;

import li.cil.scannable.common.config.Strings;
import li.cil.scannable.common.container.EntityModuleContainerMenu;
import li.cil.scannable.common.item.ConfigurableEntityScannerModuleItem;
import li.cil.scannable.common.network.Network;
import li.cil.scannable.common.network.message.SetConfiguredModuleItemAtMessage;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ConfigurableEntityScannerModuleContainerScreen extends AbstractConfigurableScannerModuleContainerScreen<EntityModuleContainerMenu, EntityType<?>> {
    // Per-screen cache of sample entities for the slot previews (created with the current level so a
    // world switch never leaves a stale one around; living entities only — others render as blank).
    private final Map<EntityType<?>, Optional<LivingEntity>> renderEntities = new HashMap<>();

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
        final LivingEntity entity = getRenderEntity(entityType);
        if (entity == null) {
            return;
        }

        // The scale is a direct pixel multiplier, so normalise by the entity's largest dimension to
        // fit any mob into the 16x16 slot; render at a gentle fixed 3/4 angle.
        final EntityDimensions dimensions = entityType.getDimensions();
        final int scale = Math.max(1, (int) (13.0f / Math.max(dimensions.width(), dimensions.height())));
        InventoryScreen.renderEntityInInventoryFollowsAngle(graphics, x, y, x + 16, y + 16, scale, 0.0625f, 0.6f, 0.25f, entity);
    }

    @Override
    protected void configureItemAt(final ItemStack stack, final int slot, final ItemStack value) {
        if (value.getItem() instanceof SpawnEggItem) {
            final EntityType<?> entityType = ((SpawnEggItem) value.getItem()).getType(value);
            BuiltInRegistries.ENTITY_TYPE.getResourceKey(entityType).ifPresent(entityTypeResourceKey ->
                    Network.sendToServer(new SetConfiguredModuleItemAtMessage(menu.containerId, slot, entityTypeResourceKey.identifier())));
        }
    }

    private LivingEntity getRenderEntity(final EntityType<?> entityType) {
        return renderEntities.computeIfAbsent(entityType, type -> {
            final Entity entity = type.create(menu.getPlayer().level(), EntitySpawnReason.LOAD);
            return entity instanceof LivingEntity living ? Optional.of(living) : Optional.empty();
        }).orElse(null);
    }
}
