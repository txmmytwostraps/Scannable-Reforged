package li.cil.scannable.common.item;

import li.cil.scannable.api.API;
import li.cil.scannable.common.neoforge.ModEventBus;
import li.cil.scannable.common.scanning.*;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class Items {
    // 26.1 items must have their registry id set on Item.Properties; DeferredRegister.Items
    // .registerItem(name, Properties -> Item) does that for us, so the constructors take Properties.
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(API.MOD_ID);

    // --------------------------------------------------------------------- //

    public static final DeferredHolder<Item, ? extends Item> SCANNER = ITEMS.registerItem("scanner", ScannerItem::new);

    public static final DeferredHolder<Item, ? extends Item> BLANK_MODULE = ITEMS.registerItem("blank_module", ModItem::new);
    public static final DeferredHolder<Item, ? extends Item> RANGE_MODULE = ITEMS.registerItem("range_module", properties -> new ScannerModuleItem(RangeScannerModule.INSTANCE, properties));
    public static final DeferredHolder<Item, ? extends Item> ENTITY_MODULE = ITEMS.registerItem("entity_module", ConfigurableEntityScannerModuleItem::new);
    public static final DeferredHolder<Item, ? extends Item> FRIENDLY_ENTITY_MODULE = ITEMS.registerItem("friendly_entity_module", properties -> new ScannerModuleItem(FriendlyEntityScannerModule.INSTANCE, properties));
    public static final DeferredHolder<Item, ? extends Item> HOSTILE_ENTITY_MODULE = ITEMS.registerItem("hostile_entity_module", properties -> new ScannerModuleItem(HostileEntityScannerModule.INSTANCE, properties));
    public static final DeferredHolder<Item, ? extends Item> BLOCK_MODULE = ITEMS.registerItem("block_module", ConfigurableBlockScannerModuleItem::new);
    public static final DeferredHolder<Item, ? extends Item> COMMON_ORES_MODULE = ITEMS.registerItem("common_ores_module", properties -> new ScannerModuleItem(CommonOresBlockScannerModule.INSTANCE, properties));
    public static final DeferredHolder<Item, ? extends Item> RARE_ORES_MODULE = ITEMS.registerItem("rare_ores_module", properties -> new ScannerModuleItem(RareOresBlockScannerModule.INSTANCE, properties));
    public static final DeferredHolder<Item, ? extends Item> FLUID_MODULE = ITEMS.registerItem("fluid_module", properties -> new ScannerModuleItem(FluidBlockScannerModule.INSTANCE, properties));
    public static final DeferredHolder<Item, ? extends Item> CHEST_MODULE = ITEMS.registerItem("chest_module", properties -> new ScannerModuleItem(ChestScannerModule.INSTANCE, properties));
    public static final DeferredHolder<Item, ? extends Item> SPAWNER_MODULE = ITEMS.registerItem("spawner_module", properties -> new ScannerModuleItem(SpawnerBlockScannerModule.INSTANCE, properties));

    // --------------------------------------------------------------------- //

    public static void initialize() {
        ITEMS.register(ModEventBus.INSTANCE);
    }
}
