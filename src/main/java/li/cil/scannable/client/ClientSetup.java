package li.cil.scannable.client;

import li.cil.scannable.client.shader.Shaders;

public class ClientSetup {
    public static void initialize() {
        // Screen factories are registered via RegisterMenuScreensEvent (see ClientSetupNeoForge).
        Shaders.initialize();
    }
}
