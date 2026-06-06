package li.cil.scannable.common.scanning;

import li.cil.scannable.api.API;
import li.cil.scannable.common.scanning.filter.IgnoredBlocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;

@EventBusSubscriber(modid = API.MOD_ID)
public final class ProviderCacheManager {
    public static void initialize() {
    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void onModConfigEvent(final ModConfigEvent configEvent) {
        clearCache();
    }

    public static void clearCache() {
        // Reset on any config change, so we also rebuild the filter when resource reload
        // kicks in which can result in ids changing and thus our cache being invalid.
        CommonOresBlockScannerModule.clearCache();
        FluidBlockScannerModule.clearCache();
        RareOresBlockScannerModule.clearCache();
        ChestScannerModule.clearCache();
        SpawnerBlockScannerModule.clearCache();
        IgnoredBlocks.clearCache();
    }
}
