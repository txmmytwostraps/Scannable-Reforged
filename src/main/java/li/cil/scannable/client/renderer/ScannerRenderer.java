package li.cil.scannable.client.renderer;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

@OnlyIn(Dist.CLIENT)
public enum ScannerRenderer {
    INSTANCE;

    // TODO(Phase 3c): rebuild the fullscreen scan-effect (depth-buffer grab + wave GLSL) on
    // RenderPipeline/GpuTexture. ShaderInstance / BufferUploader / RenderTarget / Tesselator
    // immediate mode were removed in 1.21.6. Stubbed to a no-op for the launchable build.

    public void ping(final Vec3 pos) {
    }

    public static void render(final Matrix4f viewMatrix, final Matrix4f projectionMatrix) {
    }
}
