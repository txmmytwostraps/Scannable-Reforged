package li.cil.scannable.common.container;

import li.cil.scannable.util.RegistryUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class Containers {
    public static final DeferredRegister<MenuType<?>> CONTAINERS = RegistryUtils.get(Registries.MENU);

    // --------------------------------------------------------------------- //

    public static final DeferredHolder<MenuType<?>, MenuType<ScannerContainerMenu>> SCANNER_CONTAINER = CONTAINERS.register("scanner", () -> IMenuTypeExtension.create(ScannerContainerMenu::create));
    public static final DeferredHolder<MenuType<?>, MenuType<BlockModuleContainerMenu>> BLOCK_MODULE_CONTAINER = CONTAINERS.register("block_module", () -> IMenuTypeExtension.create(BlockModuleContainerMenu::create));
    public static final DeferredHolder<MenuType<?>, MenuType<EntityModuleContainerMenu>> ENTITY_MODULE_CONTAINER = CONTAINERS.register("entity_module", () -> IMenuTypeExtension.create(EntityModuleContainerMenu::create));
    public static final DeferredHolder<MenuType<?>, MenuType<SpawnerModuleContainerMenu>> SPAWNER_MODULE_CONTAINER = CONTAINERS.register("spawner_module", () -> IMenuTypeExtension.create(SpawnerModuleContainerMenu::create));

    // --------------------------------------------------------------------- //

    public static void initialize() {
    }
}
