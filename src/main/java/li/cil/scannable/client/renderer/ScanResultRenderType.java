package li.cil.scannable.client.renderer;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import li.cil.scannable.api.API;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

/**
 * Render type for the scan-result boxes: filled, translucent, two-sided (no cull) and drawn
 * through walls (depth test always passes, no depth write) so highlighted ores show behind terrain.
 * Built on the vanilla debug-filled-box snippet (POSITION_COLOR / QUADS / translucent blend).
 */
public final class ScanResultRenderType {
    public static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
        .withLocation(Identifier.fromNamespaceAndPath(API.MOD_ID, "pipeline/scan_result"))
        .withCull(false)
        .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
        .build();

    public static final RenderType TYPE = RenderType.create(API.MOD_ID + ":scan_result", RenderSetup.builder(PIPELINE).createRenderSetup());

    private ScanResultRenderType() {
    }
}
