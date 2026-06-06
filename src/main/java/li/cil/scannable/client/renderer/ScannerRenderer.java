package li.cil.scannable.client.renderer;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import li.cil.scannable.client.ScanManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryStack;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.OptionalInt;

public enum ScannerRenderer {
    INSTANCE;

    // --------------------------------------------------------------------- //

    private long currentStart;
    @Nullable private Vec3 currentCenter;

    // --------------------------------------------------------------------- //

    public void ping(final Vec3 pos) {
        currentStart = System.currentTimeMillis();
        currentCenter = pos;
    }

    /**
     * Draws the fullscreen scan-reveal wave for the active ping (if any). Called from the world
     * render hook with the camera view-rotation matrix; the wave is composited additively onto the
     * main color target by sampling its depth buffer and reconstructing world position per-pixel.
     */
    public void render(final Matrix4fc modelViewMatrix) {
        if (!shouldRender() || currentCenter == null) {
            return;
        }

        final Minecraft mc = Minecraft.getInstance();
        final RenderTarget target = mc.getMainRenderTarget();
        final GpuTextureView colorView = target.getColorTextureView();
        final GpuTextureView depthView = target.getDepthTextureView();
        if (colorView == null || depthView == null) {
            return;
        }

        final Matrix4f invViewMatrix = new Matrix4f(modelViewMatrix).invert();
        final Vec3 cameraPos = mc.gameRenderer.getMainCamera().position();
        final int adjustedDuration = ScanManager.computeScanGrowthDuration();
        final float radius = ScanManager.computeRadius(currentStart, adjustedDuration);

        final GpuDevice device = RenderSystem.getDevice();
        final CommandEncoder encoder = device.createCommandEncoder();

        // Build the per-frame std140 uniform block (invView, center, camera, radius).
        final int size = new Std140SizeCalculator().putMat4f().putVec4().putVec4().putVec4().get();
        final GpuBuffer uniformBuffer;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final ByteBuffer data = Std140Builder.onStack(stack, size)
                .putMat4f(invViewMatrix)
                .putVec4((float) currentCenter.x, (float) currentCenter.y, (float) currentCenter.z, 0.0f)
                .putVec4((float) cameraPos.x, (float) cameraPos.y, (float) cameraPos.z, 0.0f)
                .putVec4(radius, 0.0f, 0.0f, 0.0f)
                .get();
            uniformBuffer = device.createBuffer(() -> "scannable scan_effect uniforms", GpuBuffer.USAGE_UNIFORM, data);
        }

        final GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);

        try (RenderPass pass = encoder.createRenderPass(() -> "scannable scan_effect", colorView, OptionalInt.empty())) {
            pass.setPipeline(ScanResultRenderType.SCAN_EFFECT_PIPELINE);
            pass.setUniform("Projection", RenderSystem.getProjectionMatrixBuffer());
            pass.setUniform("ScanInfo", uniformBuffer);
            pass.bindTexture("DepthSampler", depthView, sampler);
            pass.draw(0, 3);
        } finally {
            uniformBuffer.close();
        }
    }

    // --------------------------------------------------------------------- //

    private boolean shouldRender() {
        final int adjustedDuration = ScanManager.computeScanGrowthDuration();
        return currentStart > 0 && adjustedDuration > (int) (System.currentTimeMillis() - currentStart);
    }
}
