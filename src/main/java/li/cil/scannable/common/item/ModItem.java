package li.cil.scannable.common.item;

import li.cil.scannable.util.TooltipUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.List;
import java.util.function.Consumer;

public class ModItem extends Item {
    protected ModItem(final Properties properties) {
        super(properties);
    }

    protected ModItem() {
        this(new Properties());
    }

    // --------------------------------------------------------------------- //

    @OnlyIn(Dist.CLIENT)
    @Override
    public void appendHoverText(final ItemStack stack, final Item.TooltipContext context, final TooltipDisplay tooltipDisplay, final Consumer<Component> tooltip, final TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltipDisplay, tooltip, flag);
        TooltipUtils.tryAddDescription(stack, tooltip);
    }
}
