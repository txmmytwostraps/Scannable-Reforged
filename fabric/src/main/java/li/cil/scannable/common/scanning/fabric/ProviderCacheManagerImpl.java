package li.cil.scannable.common.scanning.fabric;

import fuzs.forgeconfigapiport.fabric.api.forge.v4.ForgeModConfigEvents;
import li.cil.scannable.api.API;
import li.cil.scannable.common.scanning.ProviderCacheManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

public final class ProviderCacheManagerImpl {
    public static void initialize() {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
            return;
        }

        ForgeModConfigEvents.loading(API.MOD_ID).register((cfg) -> clearCaches());
        ForgeModConfigEvents.reloading(API.MOD_ID).register((cfg) -> clearCaches());
    }

    private static void clearCaches() {
        ProviderCacheManager.clearCache();
    }
}
