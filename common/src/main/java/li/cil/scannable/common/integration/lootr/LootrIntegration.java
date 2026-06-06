package li.cil.scannable.common.integration.lootr;

import dev.architectury.injectables.annotations.ExpectPlatform;
import dev.architectury.platform.Platform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * Soft-dependency entry point for the Lootr mod.
 * <p>
 * Lootr is optional (not bundled, not required). Detection of Lootr containers is done purely via the
 * {@code lootr:containers} block tag, so it needs no Lootr classes and is naturally inert (empty tag)
 * when Lootr is absent. The only thing that genuinely needs Lootr's API is reading the client-side
 * per-player "looted" state ({@code IClientOpeners}); that lives in the platform implementation and is
 * only reached once {@link #LOADED} is confirmed true, so none of Lootr's classes load on a pack
 * without Lootr.
 */
public final class LootrIntegration {
    public static final String MOD_ID = "lootr";

    public static final boolean LOADED = Platform.isModLoaded(MOD_ID);

    // All Lootr loot containers (chests, trapped chests, barrels, shulkers, pots, suspicious
    // sand/gravel). Built from the id rather than Lootr's LootrTags so this references no Lootr class.
    public static final TagKey<Block> CONTAINERS = TagKey.create(Registries.BLOCK,
        ResourceLocation.fromNamespaceAndPath(MOD_ID, "containers"));

    // Highlight color for unopened Lootr loot.
    public static final int GOLD = 0xFFC400;

    private LootrIntegration() {
    }

    public static boolean isContainer(final BlockState state) {
        return state.is(CONTAINERS);
    }

    /**
     * Whether the Lootr container at {@code pos} has already been looted by {@code player} on this
     * client. Returns {@code false} when Lootr is absent, the player is unknown, or the block entity
     * doesn't expose opener state - i.e. "not looted", so the highlight stays.
     */
    public static boolean isClientLooted(final Level level, final BlockPos pos, @Nullable final Player player) {
        if (!LOADED || player == null) {
            return false;
        }
        return isLootedHook(level.getBlockEntity(pos), player);
    }

    // Platform hook: the ONLY place Lootr's API is referenced. Implemented in the neoforge module and
    // never invoked unless LOADED is true.
    @ExpectPlatform
    public static boolean isLootedHook(@Nullable final BlockEntity blockEntity, final Player player) {
        throw new AssertionError();
    }
}
