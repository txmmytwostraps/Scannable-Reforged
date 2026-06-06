package li.cil.scannable.common.energy;

import net.minecraft.world.item.ItemStack;

import java.util.Optional;

public interface ItemEnergyStorage {
    static Optional<ItemEnergyStorage> of(final ItemStack stack) {
        // TODO(26.1 capability rework): re-expose the scanner energy via Capabilities.Energy
        // (EnergyHandler/ItemAccess) — the 1.21.1 IEnergyStorage holder was removed in 26.1.
        // See Capabilities.initialize(). Stubbed to compile; energy is inert until rewritten.
        return Optional.empty();
    }

    long receiveEnergy(long amount, boolean simulate);

    long extractEnergy(long amount, boolean simulate);

    long getEnergyStored();

    long getMaxEnergyStored();
}
