package li.cil.scannable.common.integration.lootr.neoforge;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import noobanidus.mods.lootr.common.api.IClientOpeners;

import javax.annotation.Nullable;

/**
 * Platform implementation of {@link li.cil.scannable.common.integration.lootr.LootrIntegration}'s
 * Lootr hook. This is the only class that references Lootr's API; it is only ever invoked once Lootr
 * is confirmed loaded, so a pack without Lootr never links these symbols.
 */
public final class LootrIntegrationImpl {
    private LootrIntegrationImpl() {
    }

    public static boolean isLootedHook(@Nullable final BlockEntity blockEntity, final Player player) {
        // Lootr's container block entities implement IClientOpeners on the client; the openers are
        // synced so Lootr can render a container as already-looted for that specific player. We reuse
        // exactly that state to decide whether to keep highlighting it.
        return blockEntity instanceof final IClientOpeners openers && openers.hasClientOpened(player);
    }
}
