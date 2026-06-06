package li.cil.scannable.common.integration.lootr;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;

import javax.annotation.Nullable;

/**
 * Soft-dependency entry point for the Lootr mod.
 * <p>
 * Lootr is optional: it is not bundled and not a required dependency. Detection of Lootr containers
 * is done purely via the {@code lootr:containers} block tag, so it needs no Lootr classes and is
 * naturally inert (empty tag) when Lootr is absent. The only thing that genuinely needs Lootr's API
 * is reading the client-side per-player "looted" state, which is isolated in {@link LootrHooks} and
 * only ever invoked once {@link #LOADED} has been confirmed true — so none of Lootr's classes are
 * loaded (and cannot fail to link) on a pack without Lootr.
 */
public final class LootrIntegration {
    public static final String MOD_ID = "lootr";

    public static final boolean LOADED = ModList.get().isLoaded(MOD_ID);

    // All Lootr loot containers (chests, trapped chests, barrels, shulkers, pots, suspicious
    // sand/gravel). Built from the id rather than Lootr's LootrTags so this references no Lootr class.
    public static final TagKey<Block> CONTAINERS = TagKey.create(Registries.BLOCK,
        Identifier.fromNamespaceAndPath(MOD_ID, "containers"));

    // Highlight color for unopened Lootr loot.
    public static final int GOLD = 0xFFC400;

    private LootrIntegration() {
    }

    public static boolean isContainer(final BlockState state) {
        return state.is(CONTAINERS);
    }

    /**
     * Whether the given Lootr container at {@code pos} has already been looted by {@code player} on
     * this client. Returns {@code false} when Lootr is absent, the player is unknown, or the block
     * entity doesn't expose opener state — i.e. "not looted", so the highlight stays.
     */
    public static boolean isClientLooted(final Level level, final BlockPos pos, @Nullable final Player player) {
        if (!LOADED || player == null) {
            return false;
        }
        return LootrHooks.isClientLooted(level.getBlockEntity(pos), player);
    }
}
