package li.cil.scannable.common.neoforge.capabilities;

import li.cil.scannable.api.API;
import li.cil.scannable.common.item.Items;
import li.cil.scannable.common.item.ScannerModuleItem;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.ItemCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

@EventBusSubscriber(modid = API.MOD_ID)
public final class Capabilities {
    public static final class ScannerModule {
        public static final ItemCapability<li.cil.scannable.api.scanning.ScannerModule, Void> ITEM = ItemCapability.createVoid(Identifier.fromNamespaceAndPath(API.MOD_ID, "scanner_module"), li.cil.scannable.api.scanning.ScannerModule.class);
    }

    // --------------------------------------------------------------------- //

    @SubscribeEvent
    public static void initialize(final RegisterCapabilitiesEvent event) {
        // TODO(26.1 capability rework): re-register the scanner ENERGY and ITEM-HANDLER
        // capabilities. MC/NeoForge 26.1 replaced the holders this mod used:
        //   Capabilities.EnergyStorage (IEnergyStorage) -> Capabilities.Energy (EnergyHandler, ItemAccess)
        //   Capabilities.ItemHandler   (IItemHandler)   -> Capabilities.Item   (ResourceHandler<ItemResource>, ItemAccess)
        // The deprecated EnergyStorage/InvWrapper bridges only implement the OLD interfaces,
        // so the scanner energy store + module-inventory exposure need a proper rewrite to the
        // new resource/handler API (paired with the Phase 3 rendering work). Until then only the
        // mod's own ScannerModule capability is registered.
        event.registerItem(ScannerModule.ITEM, (stack, context) -> ((ScannerModuleItem) stack.getItem()).getModule(),
            Items.RANGE_MODULE.get(),
            Items.ENTITY_MODULE.get(),
            Items.FRIENDLY_ENTITY_MODULE.get(),
            Items.HOSTILE_ENTITY_MODULE.get(),
            Items.BLOCK_MODULE.get(),
            Items.COMMON_ORES_MODULE.get(),
            Items.RARE_ORES_MODULE.get(),
            Items.FLUID_MODULE.get(),
            Items.CHEST_MODULE.get(),
            Items.SPAWNER_MODULE.get());
    }
}
