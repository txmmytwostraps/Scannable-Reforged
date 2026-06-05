package li.cil.scannable.common.neoforge;

import li.cil.scannable.api.API;
import li.cil.scannable.common.CommonSetup;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(API.MOD_ID)
public final class CommonSetupNeoForge {
    public CommonSetupNeoForge(final IEventBus modEventBus) {
        ModEventBus.INSTANCE = modEventBus;
        CommonSetup.initialize();
    }
}
