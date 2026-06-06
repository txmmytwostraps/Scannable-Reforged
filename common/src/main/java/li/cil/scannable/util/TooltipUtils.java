package li.cil.scannable.util;

import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class TooltipUtils {
    public static void tryAddDescription(final ItemStack stack, final List<Component> tooltip) {
        if (stack.isEmpty()) {
            return;
        }

        final String translationKey = stack.getDescriptionId() + ".desc";
        final Language language = Language.getInstance();
        if (!language.has(translationKey)) {
            return;
        }

        // Emit one tooltip line per newline. A single translated component with '\n' does NOT
        // hard-break in the tooltip wrapping (it reflows as one paragraph), so split explicitly.
        for (final String line : Component.translatable(translationKey).getString().split("\n")) {
            tooltip.add(Component.literal(line).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
