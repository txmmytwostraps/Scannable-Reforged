package li.cil.scannable.common.energy;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.Optional;

/**
 * Thin long-based view over the scanner's 26.1 transfer-API {@link EnergyHandler} capability,
 * keeping the rest of the mod's call sites (tooltip, durability bar, scan cost) unchanged. The
 * {@code simulate} flag maps onto a transaction that is committed only for real transfers.
 */
public interface ItemEnergyStorage {
    static Optional<ItemEnergyStorage> of(final ItemStack stack) {
        final EnergyHandler handler = ItemAccess.forStack(stack).getCapability(Capabilities.Energy.ITEM);
        if (handler == null) {
            return Optional.empty();
        }

        return Optional.of(new ItemEnergyStorage() {
            @Override
            public long receiveEnergy(final long amount, final boolean simulate) {
                return transfer(amount, simulate, true);
            }

            @Override
            public long extractEnergy(final long amount, final boolean simulate) {
                return transfer(amount, simulate, false);
            }

            @Override
            public long getEnergyStored() {
                return handler.getAmountAsLong();
            }

            @Override
            public long getMaxEnergyStored() {
                return handler.getCapacityAsLong();
            }

            private long transfer(final long amount, final boolean simulate, final boolean insert) {
                final int clamped = (int) Math.max(0, Math.min(amount, Integer.MAX_VALUE));
                try (final Transaction transaction = Transaction.openRoot()) {
                    final int moved = insert ? handler.insert(clamped, transaction) : handler.extract(clamped, transaction);
                    if (!simulate) {
                        transaction.commit();
                    }
                    return moved;
                }
            }
        });
    }

    long receiveEnergy(long amount, boolean simulate);

    long extractEnergy(long amount, boolean simulate);

    long getEnergyStored();

    long getMaxEnergyStored();
}
