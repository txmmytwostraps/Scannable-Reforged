package li.cil.scannable.common.integration.lootr;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import noobanidus.mods.lootr.common.api.interfaces.IClientHasOpeners;

import javax.annotation.Nullable;

/**
 * The ONLY class that references Lootr's API directly. It is loaded lazily — never touched unless
 * {@link LootrIntegration#LOADED} is true — so a pack without Lootr never links these symbols.
 */
final class LootrHooks {
    private LootrHooks() {
    }

    static boolean isClientLooted(@Nullable final BlockEntity blockEntity, final Player player) {
        // Lootr's container block entities implement IClientHasOpeners on the client; the set of
        // openers is synced so Lootr can render a container as already-looted for that specific
        // player. We reuse exactly that state to decide whether to keep highlighting it.
        return blockEntity instanceof final IClientHasOpeners openers && openers.hasClientOpened(player);
    }
}
