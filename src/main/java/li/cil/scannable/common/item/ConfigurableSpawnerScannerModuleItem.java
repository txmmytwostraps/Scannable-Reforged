package li.cil.scannable.common.item;

import li.cil.scannable.common.config.Strings;
import li.cil.scannable.common.container.EntityModuleContainerMenu;
import li.cil.scannable.common.scanning.ConfigurableSpawnerScannerModule;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;

import java.util.List;
import java.util.function.Consumer;

/**
 * The spawner module is configured exactly like the living-entity module (it stores the same
 * {@code ENTITY_TYPES} list, so it reuses that module's menu/screen), but the configured mobs filter
 * which <em>spawners</em> are highlighted. Configure it three ways: the spawn-egg screen (right-click
 * in the air), right-clicking a spawner block (adds the mob it spawns), or right-clicking a mob.
 */
public final class ConfigurableSpawnerScannerModuleItem extends ScannerModuleItem {
    public ConfigurableSpawnerScannerModuleItem(final Properties properties) {
        super(ConfigurableSpawnerScannerModule.INSTANCE, properties);
    }

    // --------------------------------------------------------------------- //
    // Item

    @Override
    public void appendHoverText(final ItemStack stack, final Item.TooltipContext context, final TooltipDisplay tooltipDisplay, final Consumer<Component> tooltip, final TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltipDisplay, tooltip, flag);

        final List<EntityType<?>> entities = ConfigurableEntityScannerModuleItem.getEntityTypes(stack);
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

        if (!level.isClientSide() && player instanceof final ServerPlayer serverPlayer) {
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
    public InteractionResult useOn(final UseOnContext context) {
        final Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        final Level level = context.getLevel();
        if (level.getBlockEntity(context.getClickedPos()) instanceof final SpawnerBlockEntity spawner) {
            // Add the spawner's mob; an empty spawner just consumes the click (nothing to add).
            final Entity display = spawner.getSpawner().getOrCreateDisplayEntity(level, context.getClickedPos());
            if (display != null) {
                addType(player, context.getHand(), display.getType());
            }
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult interactLivingEntity(final ItemStack stack, final Player player, final LivingEntity target, final InteractionHand hand) {
        addType(player, hand, target.getType());
        // Always succeed to prevent opening the item UI.
        return InteractionResult.SUCCESS;
    }

    // --------------------------------------------------------------------- //

    private static void addType(final Player player, final InteractionHand hand, final EntityType<?> type) {
        // NOT the captured stack — that's a copy in creative mode.
        final ItemStack held = player.getItemInHand(hand);
        if (ConfigurableEntityScannerModuleItem.addEntityType(held, type)) {
            player.swing(hand);
            player.getInventory().setChanged();
        } else if (!player.level().isClientSide() && !ConfigurableEntityScannerModuleItem.isLocked(held)) {
            player.sendOverlayMessage(Strings.MESSAGE_NO_FREE_SLOTS);
        }
    }
}
