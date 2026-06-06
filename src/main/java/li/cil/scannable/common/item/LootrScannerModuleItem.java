package li.cil.scannable.common.item;

import li.cil.scannable.api.scanning.ScannerModule;
import li.cil.scannable.common.integration.lootr.LootrIntegration;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * The Lootr module item. Identical to a plain {@link ScannerModuleItem} except that, when the Lootr
 * mod is NOT installed, it adds a red "Lootr mod required" line so it's obvious the module can't do
 * anything (and isn't craftable) on this pack. With Lootr present the warning is omitted entirely.
 */
public final class LootrScannerModuleItem extends ScannerModuleItem {
    LootrScannerModuleItem(final ScannerModule module, final Item.Properties properties) {
        super(module, properties);
    }

    @Override
    public void appendHoverText(final ItemStack stack, final Item.TooltipContext context, final TooltipDisplay tooltipDisplay, final Consumer<Component> tooltip, final TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltipDisplay, tooltip, flag);

        if (!LootrIntegration.LOADED) {
            tooltip.accept(Component.translatable("item.scannable.lootr_module.required").withStyle(ChatFormatting.RED));
        }
    }
}
