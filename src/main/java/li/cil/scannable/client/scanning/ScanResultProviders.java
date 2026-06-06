package li.cil.scannable.client.scanning;

import li.cil.scannable.api.API;
import li.cil.scannable.api.scanning.ScanResultProvider;
import li.cil.scannable.util.RegistryUtils;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ScanResultProviders {
    private static final DeferredRegister<ScanResultProvider> DEFERRED_REGISTER = RegistryUtils.get(ScanResultProvider.REGISTRY);

    // --------------------------------------------------------------------- //

    public static final DeferredHolder<ScanResultProvider, ScanResultProviderBlock> BLOCKS = DEFERRED_REGISTER.register(
        API.SCAN_RESULT_PROVIDER_BLOCKS.getPath(), ScanResultProviderBlock::new);
    public static final DeferredHolder<ScanResultProvider, ScanResultProviderEntity> ENTITIES = DEFERRED_REGISTER.register(
        API.SCAN_RESULT_PROVIDER_ENTITIES.getPath(), ScanResultProviderEntity::new);

    // --------------------------------------------------------------------- //

    public static void initialize() {
        DEFERRED_REGISTER.makeRegistry(builder -> {
        });
    }
}
