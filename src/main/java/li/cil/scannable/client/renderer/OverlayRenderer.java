package li.cil.scannable.client.renderer;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import li.cil.scannable.api.API;
import li.cil.scannable.common.config.Strings;
import li.cil.scannable.common.item.ScannerItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public final class OverlayRenderer {
    private static final Identifier PROGRESS = Identifier.fromNamespaceAndPath(API.MOD_ID, "textures/gui/overlay/scanner_progress.png");
    private static final int SIZE = 64;
    // Tint applied to the (white) progress texture: light blue at ~66% alpha (matches 1.21.1).
    private static final int COLOR = 0xA8A8CCED;
    // Square-corner directions (clock angle from top, clockwise) the wipe must pass through.
    private static final float[] CORNERS = {(float) (Math.PI * 0.25), (float) (Math.PI * 0.75), (float) (Math.PI * 1.25), (float) (Math.PI * 1.75)};

    public static void render(final GuiGraphicsExtractor graphics, final float partialTick) {
        final Minecraft mc = Minecraft.getInstance();
        final Player player = mc.player;
        if (player == null) {
            return;
        }

        final ItemStack stack = player.getUseItem();
        if (stack.isEmpty() || !ScannerItem.isScanner(stack)) {
            return;
        }

        final int total = stack.getUseDuration(player);
        if (total <= 0) {
            return;
        }
        final int remaining = player.getUseItemRemainingTicks();
        final float progress = Mth.clamp(1 - (remaining - partialTick) / (float) total, 0, 1);
        if (progress <= 0) {
            return;
        }

        final int half = SIZE / 2;
        final int midX = graphics.guiWidth() / 2;
        final int midY = graphics.guiHeight() / 2;

        final AbstractTexture texture = mc.getTextureManager().getTexture(PROGRESS);
        final TextureSetup textureSetup = TextureSetup.singleTexture(texture.getTextureView(), texture.getSampler());

        final Matrix3x2f pose = new Matrix3x2f(graphics.pose());
        final ScreenRectangle bounds = new ScreenRectangle(midX - half, midY - half, SIZE, SIZE);
        graphics.submitGuiElementRenderState(new ScanProgressElement(textureSetup, pose, progress, midX, midY, half, COLOR, bounds));

        final Component label = Strings.progress(Mth.floor(progress * 100));
        graphics.text(mc.font, label, midX + half + 12, midY - mc.font.lineHeight / 2, 0xCCAACCEE, true);
    }

    // --------------------------------------------------------------------- //

    // A clock-wipe pie: a triangle fan from the centre out to the square's perimeter, sweeping
    // clockwise from the top up to the current progress angle (passing each corner so it hugs the
    // square edge). Emitted as degenerate quads for the GUI_TEXTURED (QUADS) pipeline.
    private record ScanProgressElement(TextureSetup textureSetup, Matrix3x2f pose, float progress,
                                       int midX, int midY, int half, int color,
                                       ScreenRectangle bounds) implements GuiElementRenderState {
        @Override
        public RenderPipeline pipeline() {
            return ScanResultRenderType.SCAN_PROGRESS_PIPELINE;
        }

        @Nullable
        @Override
        public ScreenRectangle scissorArea() {
            return null;
        }

        @Override
        public void buildVertices(final VertexConsumer buffer) {
            final float angle = progress * (float) (Math.PI * 2);

            final List<float[]> points = new ArrayList<>();
            points.add(perimeter(0));
            for (final float corner : CORNERS) {
                if (corner < angle) {
                    points.add(perimeter(corner));
                }
            }
            points.add(perimeter(angle));

            final float[] center = {midX, midY, 0.5f, 0.5f};
            for (int i = 0; i + 1 < points.size(); i++) {
                vertex(buffer, center);
                vertex(buffer, points.get(i));
                vertex(buffer, points.get(i + 1));
            }
        }

        private float[] perimeter(final float theta) {
            final float s = Mth.sin(theta);
            final float c = Mth.cos(theta);
            final float scale = half / Math.max(Math.abs(s), Math.abs(c));
            final float px = midX + scale * s;
            final float py = midY - scale * c; // screen up is -y
            final float u = 0.5f + (px - midX) / (2f * half);
            final float v = 0.5f + (midY - py) / (2f * half);
            return new float[]{px, py, u, v};
        }

        private void vertex(final VertexConsumer buffer, final float[] p) {
            buffer.addVertexWith2DPose(pose, p[0], p[1]).setUv(p[2], p[3]).setColor(color);
        }
    }

    private OverlayRenderer() {
    }
}
