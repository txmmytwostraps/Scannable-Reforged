package li.cil.scannable.common.item;

import li.cil.scannable.common.integration.lootr.LootrIntegration;
import li.cil.scannable.common.scanning.LootrBlockScannerModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * The Lootr module item. Identical to a plain {@link ScannerModuleItem} except that, when the Lootr
 * mod is NOT installed, it adds a red "Lootr mod required" line so it's obvious the module can't do
 * anything (and isn't craftable) on this pack. With Lootr present the warning is omitted entirely.
 */
public final class LootrScannerModuleItem extends ScannerModuleItem {
    public LootrScannerModuleItem() {
        super(LootrBlockScannerModule.INSTANCE);
    }

    @Environment(EnvType.CLIENT)
    @Override
    public void appendHoverText(final ItemStack stack, final Item.TooltipContext context, final List<Component> tooltip, final TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        if (!LootrIntegration.LOADED) {
            tooltip.add(Component.translatable("item.scannable.lootr_module.required").withStyle(ChatFormatting.RED));
        }
    }
}
