package li.cil.scannable.client.renderer;

import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Optional;
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

    // Animated per-box shimmer (additive, through walls): the result-box fill, drawn with the
    // scan_result shader (scrolling scanlines + pulse + edge glow). World geometry, so it reuses the
    // vanilla core/position_tex_color vertex shader (ProjMat * ModelViewMat + UV/colour passthrough);
    // the fragment reads GameTime from the auto-bound Globals UBO, so it renders through the normal
    // buffered path (no manual pass needed).
    public static final RenderPipeline SHIMMER_PIPELINE = RenderPipeline.builder()
        .withLocation(Identifier.fromNamespaceAndPath(API.MOD_ID, "pipeline/scan_shimmer"))
        .withVertexShader("core/position_tex_color")
        .withFragmentShader(Identifier.fromNamespaceAndPath(API.MOD_ID, "core/scan_result"))
        .withUniform("Projection", UniformType.UNIFORM_BUFFER)
        .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
        .withUniform("Globals", UniformType.UNIFORM_BUFFER)
        .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
        .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
        .withCull(false)
        .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
        .build();

    public static final RenderType SHIMMER_TYPE = RenderType.create(API.MOD_ID + ":scan_shimmer", RenderSetup.builder(SHIMMER_PIPELINE).createRenderSetup());

    // HUD scan-progress ring: a textured, two-sided, TRIANGLES pie (the clock-wipe fan). Derived
    // from the GUI textured snippet but with cull off (the fan winding is mixed) and TRIANGLES mode
    // (each wedge is its own triangle — no degenerate quads).
    public static final RenderPipeline SCAN_PROGRESS_PIPELINE = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
        .withLocation(Identifier.fromNamespaceAndPath(API.MOD_ID, "pipeline/scan_progress"))
        .withCull(false)
        .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.TRIANGLES)
        .build();

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
        // No depth-stencil state: this fullscreen pass reads depth via the DepthSampler, not as a
        // depth attachment, so the pipeline must not request one (avoids the GlCommandEncoder warning).
        .withDepthStencilState(Optional.empty())
        .withCull(false)
        .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
        .build();

    private ScanResultRenderType() {
    }
}
