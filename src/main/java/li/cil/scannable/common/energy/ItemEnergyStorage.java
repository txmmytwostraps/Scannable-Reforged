package li.cil.scannable.common.energy;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;

import java.util.Optional;

public interface ItemEnergyStorage {
    static Optional<ItemEnergyStorage> of(final ItemStack stack) {
        return Optional.ofNullable(stack.getCapability(Capabilities.EnergyStorage.ITEM)).map(capability -> new ItemEnergyStorage() {
            @Override
            public long receiveEnergy(final long amount, final boolean simulate) {
                final int clampedAmount = (int) Math.min(amount, Integer.MAX_VALUE);
                return capability.receiveEnergy(clampedAmount, simulate);
            }

            @Override
            public long extractEnergy(final long amount, final boolean simulate) {
                final int clampedAmount = (int) Math.min(amount, Integer.MAX_VALUE);
                return capability.extractEnergy(clampedAmount, simulate);
            }

            @Override
            public long getEnergyStored() {
                return capability.getEnergyStored();
            }

            @Override
            public long getMaxEnergyStored() {
                return capability.getMaxEnergyStored();
            }
        });
    }

    long receiveEnergy(long amount, boolean simulate);

    long extractEnergy(long amount, boolean simulate);

    long getEnergyStored();

    long getMaxEnergyStored();
}
