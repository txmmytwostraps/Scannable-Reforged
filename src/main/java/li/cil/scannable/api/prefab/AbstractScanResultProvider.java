package li.cil.scannable.api.prefab;

import com.mojang.blaze3d.vertex.PoseStack;
import li.cil.scannable.api.scanning.ScanResultProvider;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Collection;

/**
 * Helper base class for scan result providers, providing some common
 * functionality for drawing result information.
 */
@OnlyIn(Dist.CLIENT)
public abstract class AbstractScanResultProvider implements ScanResultProvider {
    protected Player player;
    protected Vec3 center;
    protected int radius;

    // --------------------------------------------------------------------- //
    // ScanResultProvider

    @Override
    public void initialize(final Player player, final Collection<ItemStack> modules, final Vec3 center, final float radius, final int scanTicks) {
        this.player = player;
        this.center = center;
        this.radius = (int) radius;
    }

    @Override
    public void reset() {
        player = null;
        center = null;
        radius = 0;
    }

    // --------------------------------------------------------------------- //

    /**
     * Renders an icon with a label that is only shown when looked at. This is
     * what's used to render the entity labels for example.
     * <p>
     * TODO(Phase 3b): rebuild on the 1.21.6 render path. The old implementation used
     * RenderType.create / RenderStateShard / GameRenderer position shaders and Font#drawInBatch's
     * batch buffer — all removed or reworked in 1.21.5/1.21.6. Stubbed to a no-op for the
     * launchable build (scan-result icons/labels do not draw yet).
     */
    protected static void renderIconLabel(final MultiBufferSource bufferSource, final PoseStack poseStack, final float yaw, final float pitch, final Vec3 lookVec, final Vec3 viewerEyes, final float displayDistance, final Vec3 resultPos, final Identifier icon, @Nullable final Component label) {
    }
}
