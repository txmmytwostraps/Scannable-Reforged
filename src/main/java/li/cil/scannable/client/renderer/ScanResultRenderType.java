package li.cil.scannable.client.renderer;

import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import li.cil.scannable.api.API;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

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

    // Matching no-depth line pipeline/type for the box edges (a bright outline on top of the fill).
    public static final RenderPipeline LINES_PIPELINE = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
        .withLocation(Identifier.fromNamespaceAndPath(API.MOD_ID, "pipeline/scan_result_lines"))
        .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
        .build();

    public static final RenderType LINES_TYPE = RenderType.create(API.MOD_ID + ":scan_result_lines", RenderSetup.builder(LINES_PIPELINE).createRenderSetup());

    // Textured, translucent, two-sided, through-wall pipeline for the billboarded result icons.
    // Derived from the GUI textured snippet (core/position_tex_color / Sampler0 / NO_DEPTH_TEST).
    public static final RenderPipeline ICON_PIPELINE = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
        .withLocation(Identifier.fromNamespaceAndPath(API.MOD_ID, "pipeline/scan_icon"))
        .withCull(false)
        .build();

    // One render type per icon texture (the texture is bound on the RenderSetup, not the pipeline).
    private static final Map<Identifier, RenderType> ICON_TYPES = new HashMap<>();

    public static RenderType icon(final Identifier texture) {
        return ICON_TYPES.computeIfAbsent(texture, tex -> RenderType.create(
            API.MOD_ID + ":scan_icon/" + tex,
            RenderSetup.builder(ICON_PIPELINE).withTexture("Sampler0", tex).createRenderSetup()));
    }

    // Fullscreen scan-reveal effect: samples the main depth buffer and additively paints the
    // expanding spherical wave. No vertex buffer (core/screenquad generates the fullscreen triangle
    // from gl_VertexID); no depth test/write; additive blend. Drawn via a manual RenderPass in
    // ScannerRenderer so it can bind the depth texture + a per-frame uniform buffer.
    public static final RenderPipeline SCAN_EFFECT_PIPELINE = RenderPipeline.builder()
        .withLocation(Identifier.fromNamespaceAndPath(API.MOD_ID, "pipeline/scan_effect"))
        .withVertexShader("core/screenquad")
        .withFragmentShader(Identifier.fromNamespaceAndPath(API.MOD_ID, "core/scan_effect"))
        .withSampler("DepthSampler")
        .withUniform("Projection", UniformType.UNIFORM_BUFFER)
        .withUniform("ScanInfo", UniformType.UNIFORM_BUFFER)
        .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
        .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
        .withCull(false)
        .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
        .build();

    private ScanResultRenderType() {
    }
}
