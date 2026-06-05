package li.cil.scannable.common;

import li.cil.scannable.api.API;
import li.cil.scannable.client.ClientConfig;
import li.cil.scannable.client.scanning.ScanResultProviders;
import li.cil.scannable.common.config.CommonConfig;
import li.cil.scannable.common.container.Containers;
import li.cil.scannable.common.item.Items;
import li.cil.scannable.common.item.ModDataComponents;
import li.cil.scannable.common.network.Network;
import li.cil.scannable.common.scanning.ProviderCacheManager;
import li.cil.scannable.common.tags.ItemTags;
import li.cil.scannable.util.ConfigManager;
import li.cil.scannable.util.RegistryUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

public final class CommonSetup {
    public static void initialize() {
        ConfigManager.add(CommonConfig::new);
        ConfigManager.add(ClientConfig::new);
        ConfigManager.initialize();

        RegistryUtils.begin(API.MOD_ID);

        ItemTags.initialize();
        ModDataComponents.initialize();
        Items.initialize();
        Containers.initialize();
        Network.initialize();

        if (FMLEnvironment.dist == Dist.CLIENT) {
            ScanResultProviders.initialize();
            ProviderCacheManager.initialize();
        }

        RegistryUtils.finish();

        ModCreativeTabs.initialize();
    }
}
