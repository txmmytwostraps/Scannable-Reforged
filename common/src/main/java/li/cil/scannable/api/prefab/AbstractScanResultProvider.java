package li.cil.scannable.api.prefab;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import li.cil.scannable.api.scanning.ScanResultProvider;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import static li.cil.scannable.util.UnitConversion.toRadians;

/**
 * Helper base class for scan result providers, providing some common
 * functionality for drawing result information.
 */
@Environment(EnvType.CLIENT)
public abstract class AbstractScanResultProvider implements ScanResultProvider {
    protected Player player;
    protected Vec3 center;
    protected int radius;

    // Block-scan icons: show at most MAX_ICONS, and only for results within ICON_CONE_DOT of the look
    // direction (a tight cone, so they cluster around the crosshair instead of spreading across the
    // screen). Looking right at a sparse spot shows 1; a dense one shows up to MAX_ICONS. Both tunable.
    protected static final int MAX_ICONS = 4;
    protected static final float ICON_CONE_DOT = 0.999f;

    // --------------------------------------------------------------------- //
    // ScanResultProvider

    @Override
    public void initialize(final Player player, final Collection<ItemStack> modules, final Vec3 center, final float radius, final int scanTicks) {
        this.player = player;
        this.center = center;
        this.radius = (int)radius;
    }

    @Override
    public void reset() {
        player = null;
        center = null;
        radius = 0;
    }

    // --------------------------------------------------------------------- //

    /**
     * Renders icons + at most one name for a list of results, consistently across all modules. The
     * name is shown only on the single most-centered result (so a dense scan never produces a wall of
     * names); icons render for up to {@code maxIcons} results whose look-direction dot exceeds
     * {@code minIconDot}. {@code results} must be pre-sorted ascending by that dot (most-centered
     * last). Block scans pass a finite cap + a cone; entity scans pass an effectively unlimited cap so
     * every mob keeps its icon - only the name is capped.
     */
    protected static <T> void renderIconLabels(final MultiBufferSource bufferSource, final PoseStack poseStack, final float yaw, final float pitch, final Vec3 lookVec, final Vec3 viewerEyes, final boolean showDistance, final List<T> results, final Function<T, Vec3> position, final Function<T, ResourceLocation> icon, final Function<T, Component> name, final Predicate<T> visible, final int maxIcons, final float minIconDot) {
        int shown = 0;
        boolean nameShown = false;
        for (int i = results.size() - 1; i >= 0 && shown < maxIcons; i--) {
            final T result = results.get(i);
            final Vec3 resultPos = position.apply(result);
            final Vec3 toResult = resultPos.subtract(viewerEyes);
            final float lookDirDot = (float) lookVec.dot(toResult.normalize());
            if (lookDirDot <= minIconDot) {
                break; // pre-sorted: nothing earlier is more centered
            }
            if (!visible.test(result)) {
                continue;
            }

            // The first (most-centered) result gets the name; renderIconLabel still gates the text to
            // its ~0.999 cone, so at most one name ever shows. The rest are icon-only.
            Component label = null;
            if (!nameShown) {
                nameShown = true;
                final Component candidate = name.apply(result);
                if (candidate != null && !candidate.getString().isEmpty()) {
                    label = candidate;
                }
            }

            final float distance = showDistance ? (float) toResult.length() : 0f;
            renderIconLabel(bufferSource, poseStack, yaw, pitch, lookVec, viewerEyes, distance, resultPos, icon.apply(result), label);
            shown++;
        }
    }

    /**
     * Renders an icon with a label that is only shown when looked at. This is
     * what's used to render the entity labels for example.
     *
     * @param bufferSource    the buffer source to use for batch rendering.
     * @param poseStack       the pose stack for rendering.
     * @param yaw             the interpolated yaw of the viewer.
     * @param pitch           the interpolated pitch of the viewer.
     * @param lookVec         the look vector of the viewer.
     * @param viewerEyes      the eye position of the viewer.
     * @param displayDistance the distance to show in the label. Zero or negative to hide.
     * @param resultPos       the interpolated position of the result.
     * @param icon            the icon to display.
     * @param label           the label text. May be null.
     */
    protected static void renderIconLabel(final MultiBufferSource bufferSource, final PoseStack poseStack, final float yaw, final float pitch, final Vec3 lookVec, final Vec3 viewerEyes, final float displayDistance, final Vec3 resultPos, final ResourceLocation icon, @Nullable final Component label) {
        final Vec3 toResult = resultPos.subtract(viewerEyes);
        final float distance = (float) toResult.length();
        final float lookDirDot = (float) lookVec.dot(toResult.normalize());
        final float sqLookDirDot = lookDirDot * lookDirDot;
        final float sq2LookDirDot = sqLookDirDot * sqLookDirDot;
        final float focusScale = Mth.clamp(sq2LookDirDot * sq2LookDirDot + 0.005f, 0.5f, 1f);
        final float scale = distance * focusScale * 0.005f;

        poseStack.pushPose();
        poseStack.translate(resultPos.x, resultPos.y, resultPos.z);
        poseStack.mulPose(new Quaternionf().rotationY(toRadians(-yaw)));
        poseStack.mulPose(new Quaternionf().rotationX(toRadians(pitch)));
        poseStack.scale(-scale, -scale, scale);

        if (lookDirDot > 0.999f && label != null) {
            final Component text;
            if (displayDistance > 0) {
                text = withDistance(label, Mth.ceil(displayDistance));
            } else {
                text = label;
            }

            final Font font = Minecraft.getInstance().font;
            final int width = font.width(text) + 16;

            poseStack.pushPose();
            poseStack.translate(width / 2f, 0, 0);

            drawQuad(bufferSource.getBuffer(getRenderLayer()), poseStack, width, font.lineHeight + 5, 0, 0, 0, 0.6f);

            poseStack.popPose();
            font.drawInBatch(text, 12, -4, 0xFFFFFFFF, true, poseStack.last().pose(), bufferSource, Font.DisplayMode.SEE_THROUGH, 0, 0xf000f0);
            // HACK This is a workaround for text shadow rendering on top of main text in 1.20.  Can't figure out why.
            font.drawInBatch(text, 12, -4, 0xFFFFFFFF, false, poseStack.last().pose(), bufferSource, Font.DisplayMode.SEE_THROUGH, 0, 0xf000f0);
        }

        drawQuad(bufferSource.getBuffer(getRenderLayer(icon)), poseStack, 16, 16);

        poseStack.popPose();
    }

    // --------------------------------------------------------------------- //
    // Drawing simple primitives in an existing buffer.

    protected static void drawQuad(final VertexConsumer buffer, final PoseStack poseStack, final float width, final float height) {
        drawQuad(buffer, poseStack, width, height, 1, 1, 1, 1);
    }

    protected static void drawQuad(final VertexConsumer buffer, final PoseStack poseStack, final float width, final float height, final float r, final float g, final float b, final float a) {
        final var matrix = poseStack.last().pose();
        buffer.addVertex(matrix, -width * 0.5f, height * 0.5f, 0).setColor(r, g, b, a).setUv(0, 1f);
        buffer.addVertex(matrix, width * 0.5f, height * 0.5f, 0).setColor(r, g, b, a).setUv(1f, 1f);
        buffer.addVertex(matrix, width * 0.5f, -height * 0.5f, 0).setColor(r, g, b, a).setUv(1f, 0);
        buffer.addVertex(matrix, -width * 0.5f, -height * 0.5f, 0).setColor(r, g, b, a).setUv(0, 0);
    }

    // --------------------------------------------------------------------- //
    // Simple render layers for result rendering.

    protected static RenderType getRenderLayer() {
        return RenderType.create("scan_result",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 65536,
            false,
            false,
            RenderType.CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorShader))
                .setTransparencyState(RenderType.TRANSLUCENT_TRANSPARENCY)
                .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .createCompositeState(false));
    }

    protected static RenderType getRenderLayer(final ResourceLocation textureLocation) {
        return RenderType.create("scan_result",
            DefaultVertexFormat.POSITION_TEX,
            VertexFormat.Mode.QUADS, 65536,
            false,
            false,
            RenderType.CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionTexShader))
                .setTextureState(new RenderStateShard.TextureStateShard(textureLocation, false, false))
                .setTransparencyState(RenderType.TRANSLUCENT_TRANSPARENCY)
                .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .createCompositeState(false));
    }

    // --------------------------------------------------------------------- //

    private static Component withDistance(final Component caption, final float distance) {
        return Component.translatable("gui.scannable.overlay.distance", caption, Mth.ceil(distance));
    }
}
