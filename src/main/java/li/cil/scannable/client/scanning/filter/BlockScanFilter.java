package li.cil.scannable.client.scanning.filter;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Predicate;

@OnlyIn(Dist.CLIENT)
public record BlockScanFilter(Block block) implements Predicate<BlockState> {
    @Override
    public boolean test(final BlockState state) {
        return block == state.getBlock();
    }
}
