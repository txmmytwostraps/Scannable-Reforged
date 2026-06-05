package li.cil.scannable.common.scanning.neoforge;

import li.cil.scannable.api.API;
import li.cil.scannable.common.scanning.ProviderCacheManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = API.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ProviderCacheManagerImpl {
    public static void initialize() {
    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void onModConfigEvent(final ModConfigEvent configEvent) {
        ProviderCacheManager.clearCache();
    }
}
