package li.cil.scannable.common.item;

import li.cil.scannable.common.scanning.*;
import li.cil.scannable.util.RegistryUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class Items {
    private static final DeferredRegister<Item> ITEMS = RegistryUtils.get(Registries.ITEM);

    // --------------------------------------------------------------------- //

    public static final DeferredHolder<Item, ? extends Item> SCANNER = ITEMS.register("scanner", ScannerItem::new);

    public static final DeferredHolder<Item, ? extends Item> BLANK_MODULE = ITEMS.register("blank_module", ModItem::new);
    public static final DeferredHolder<Item, ? extends Item> RANGE_MODULE = ITEMS.register("range_module", () -> new ScannerModuleItem(RangeScannerModule.INSTANCE));
    public static final DeferredHolder<Item, ? extends Item> ENTITY_MODULE = ITEMS.register("entity_module", ConfigurableEntityScannerModuleItem::new);
    public static final DeferredHolder<Item, ? extends Item> FRIENDLY_ENTITY_MODULE = ITEMS.register("friendly_entity_module", () -> new ScannerModuleItem(FriendlyEntityScannerModule.INSTANCE));
    public static final DeferredHolder<Item, ? extends Item> HOSTILE_ENTITY_MODULE = ITEMS.register("hostile_entity_module", () -> new ScannerModuleItem(HostileEntityScannerModule.INSTANCE));
    public static final DeferredHolder<Item, ? extends Item> BLOCK_MODULE = ITEMS.register("block_module", ConfigurableBlockScannerModuleItem::new);
    public static final DeferredHolder<Item, ? extends Item> COMMON_ORES_MODULE = ITEMS.register("common_ores_module", () -> new ScannerModuleItem(CommonOresBlockScannerModule.INSTANCE));
    public static final DeferredHolder<Item, ? extends Item> RARE_ORES_MODULE = ITEMS.register("rare_ores_module", () -> new ScannerModuleItem(RareOresBlockScannerModule.INSTANCE));
    public static final DeferredHolder<Item, ? extends Item> FLUID_MODULE = ITEMS.register("fluid_module", () -> new ScannerModuleItem(FluidBlockScannerModule.INSTANCE));
    public static final DeferredHolder<Item, ? extends Item> CHEST_MODULE = ITEMS.register("chest_module", () -> new ScannerModuleItem(ChestScannerModule.INSTANCE));
    public static final DeferredHolder<Item, ? extends Item> SPAWNER_MODULE = ITEMS.register("spawner_module", () -> new ScannerModuleItem(SpawnerBlockScannerModule.INSTANCE));

    // --------------------------------------------------------------------- //

    public static void initialize() {
    }
}
