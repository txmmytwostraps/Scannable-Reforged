package li.cil.scannable.common.scanning.neoforge;

import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.Tags;

public final class RareOresBlockScannerModuleImpl {
    public static TagKey<Block> getTopLevelOreTag() {
        return Tags.Blocks.ORES;
    }
}
