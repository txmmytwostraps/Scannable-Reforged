package li.cil.scannable.common.item;

import li.cil.scannable.common.config.Constants;
import li.cil.scannable.common.config.Strings;
import li.cil.scannable.common.container.EntityModuleContainerMenu;
import li.cil.scannable.common.scanning.ConfigurableEntityScannerModule;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.Optional;

public final class ConfigurableEntityScannerModuleItem extends ScannerModuleItem {
    public static boolean isLocked(final ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.LOCKED.get(), false);
    }

    public static List<EntityType<?>> getEntityTypes(final ItemStack stack) {
        final List<Identifier> ids = stack.get(ModDataComponents.ENTITY_TYPES.get());
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }

        final List<EntityType<?>> result = new ArrayList<>();
        ids.forEach(id -> EntityType.byString(id.toString()).ifPresent(result::add));

        return result;
    }

    public static boolean addEntityType(final ItemStack stack, final EntityType<?> entityType) {
        final Optional<ResourceKey<EntityType<?>>> registryName = BuiltInRegistries.ENTITY_TYPE.getResourceKey(entityType);
        if (registryName.isEmpty()) {
            return false;
        }

        if (isLocked(stack)) {
            return false;
        }

        final Identifier id = registryName.get().identifier();

        final List<Identifier> list = new ArrayList<>(stack.getOrDefault(ModDataComponents.ENTITY_TYPES.get(), Collections.emptyList()));
        if (list.contains(id)) {
            return true;
        }
        if (list.size() >= Constants.CONFIGURABLE_MODULE_SLOTS) {
            return false;
        }

        list.add(id);
        stack.set(ModDataComponents.ENTITY_TYPES.get(), List.copyOf(list));
        return true;
    }

    public static void setEntityTypeAt(final ItemStack stack, final int index, final EntityType<?> entityType) {
        if (index < 0 || index >= Constants.CONFIGURABLE_MODULE_SLOTS) {
            return;
        }

        final Optional<ResourceKey<EntityType<?>>> registryName = BuiltInRegistries.ENTITY_TYPE.getResourceKey(entityType);
        if (registryName.isEmpty()) {
            return;
        }

        if (isLocked(stack)) {
            return;
        }

        final Identifier id = registryName.get().identifier();

        final List<Identifier> list = new ArrayList<>(stack.getOrDefault(ModDataComponents.ENTITY_TYPES.get(), Collections.emptyList()));
        final int oldIndex = list.indexOf(id);
        if (oldIndex == index) {
            return;
        }

        if (index >= list.size()) {
            list.add(id);
        } else {
            list.set(index, id);
        }

        if (oldIndex >= 0) {
            list.remove(oldIndex);
        }

        stack.set(ModDataComponents.ENTITY_TYPES.get(), List.copyOf(list));
    }

    public static void removeEntityTypeAt(final ItemStack stack, final int index) {
        if (index < 0 || index >= Constants.CONFIGURABLE_MODULE_SLOTS) {
            return;
        }

        if (isLocked(stack)) {
            return;
        }

        final List<Identifier> list = new ArrayList<>(stack.getOrDefault(ModDataComponents.ENTITY_TYPES.get(), Collections.emptyList()));
        if (index < list.size()) {
            list.remove(index);
            stack.set(ModDataComponents.ENTITY_TYPES.get(), List.copyOf(list));
        }
    }

    // --------------------------------------------------------------------- //

    public ConfigurableEntityScannerModuleItem(final Properties properties) {
        super(ConfigurableEntityScannerModule.INSTANCE, properties);
    }

    // --------------------------------------------------------------------- //
    // Item

    @Override
    public void appendHoverText(final ItemStack stack, final Item.TooltipContext context, final TooltipDisplay tooltipDisplay, final Consumer<Component> tooltip, final TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltipDisplay, tooltip, flag);

        final List<EntityType<?>> entities = getEntityTypes(stack);
        if (!entities.isEmpty()) {
            tooltip.accept(Strings.TOOLTIP_ENTITIES_LIST_CAPTION);
            entities.forEach(e -> tooltip.accept(Strings.listItem(e.getDescription())));
        }
    }

    @Override
    public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
        final ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return stack.getHoverName();
                }

                @Override
                public AbstractContainerMenu createMenu(final int id, final Inventory inventory, final Player player) {
                    return new EntityModuleContainerMenu(id, inventory, hand);
                }
            }, buffer -> buffer.writeEnum(hand));
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult interactLivingEntity(final ItemStack stack, final Player player, final LivingEntity target, final InteractionHand hand) {
        // NOT adding to `stack` parameter, because that's a copy in creative mode.
        if (addEntityType(player.getItemInHand(hand), target.getType())) {
            player.swing(hand);
            player.getInventory().setChanged();
        } else {
            if (!player.level().isClientSide() && !ConfigurableEntityScannerModuleItem.isLocked(stack)) {
                player.sendOverlayMessage(Strings.MESSAGE_NO_FREE_SLOTS);
            }
        }

        // Always succeed to prevent opening item UI.
        return InteractionResult.SUCCESS;
    }
}
