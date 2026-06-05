package li.cil.scannable.client;

import li.cil.scannable.client.shader.Shaders;

public class ClientSetup {
    public static void initialize() {
        // Screen factories are registered per-loader (NeoForge: RegisterMenuScreensEvent),
        // because the cross-loader MenuRegistry.registerScreenFactory does not take effect
        // when called from FMLClientSetupEvent on NeoForge 1.21.1.
        Shaders.initialize();
    }
}
