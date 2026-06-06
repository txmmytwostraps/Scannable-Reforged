package li.cil.scannable.client.renderer;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.client.gui.GuiGraphicsExtractor;

@OnlyIn(Dist.CLIENT)
public final class OverlayRenderer {
    // TODO(Phase 3b): rebuild the radial scan-progress overlay (immediate-mode Tesselator /
    // BufferUploader removed in 1.21.6). Stubbed to a no-op for the launchable build.
    public static void render(final GuiGraphicsExtractor graphics, final float partialTick) {
    }

    private OverlayRenderer() {
    }
}
