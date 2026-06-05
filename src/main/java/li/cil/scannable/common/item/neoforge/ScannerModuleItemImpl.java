package li.cil.scannable.common.item.neoforge;

import li.cil.scannable.api.scanning.ScannerModule;
import net.minecraft.world.item.ItemStack;
import li.cil.scannable.common.neoforge.capabilities.Capabilities;

import java.util.Optional;

public final class ScannerModuleItemImpl {
    public static Optional<ScannerModule> getModule(final ItemStack stack) {
        return Optional.ofNullable(stack.getCapability(Capabilities.ScannerModule.ITEM));
    }
}
