package li.cil.scannable.client.gui;

import li.cil.scannable.api.API;
import li.cil.scannable.common.container.ScannerContainerMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

@OnlyIn(Dist.CLIENT)
public class ScannerContainerScreen extends AbstractContainerScreen<ScannerContainerMenu> {
    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(API.MOD_ID, "textures/gui/container/scanner.png");
    private static final Component SCANNER_MODULES_TEXT = Component.translatable("gui.scannable.scanner.active_modules");
    private static final Component SCANNER_MODULES_TOOLTIP = Component.translatable("gui.scannable.scanner.active_modules.desc");
    private static final Component SCANNER_MODULES_INACTIVE_TEXT = Component.translatable("gui.scannable.scanner.inactive_modules");
    private static final Component SCANNER_MODULES_INACTIVE_TOOLTIP = Component.translatable("gui.scannable.scanner.inactive_modules.desc");

    // --------------------------------------------------------------------- //

    public ScannerContainerScreen(final ScannerContainerMenu container, final Inventory inventory, final Component title) {
        super(container, inventory, title, 176, 159);
        inventoryLabelX = 8;
        inventoryLabelY = 65;
    }

    // --------------------------------------------------------------------- //
    // 1.21.6 extract-model GUI: draw the background in extractContents (no more renderBg),
    // labels via extractLabels/graphics.text, custom tooltips via extractTooltip.

    @Override
    public void extractContents(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float partialTick) {
        final int x = (width - imageWidth) / 2;
        final int y = (height - imageHeight) / 2;
        graphics.blit(BACKGROUND, x, y, x + imageWidth, y + imageHeight, 0.0f, imageWidth / 256.0f, 0.0f, imageHeight / 256.0f);
        super.extractContents(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void extractLabels(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);

        graphics.text(font, SCANNER_MODULES_TEXT, 8, 23, 0xFF404040, false);
        graphics.text(font, SCANNER_MODULES_INACTIVE_TEXT, 8, 49, 0xFF404040, false);
    }

    @Override
    protected void extractTooltip(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);

        if (isHovering(8, 23, font.width(SCANNER_MODULES_TEXT), font.lineHeight, mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(SCANNER_MODULES_TOOLTIP, mouseX, mouseY);
        }
        if (isHovering(8, 49, font.width(SCANNER_MODULES_INACTIVE_TEXT), font.lineHeight, mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(SCANNER_MODULES_INACTIVE_TOOLTIP, mouseX, mouseY);
        }
    }

    @Override
    protected void slotClicked(@Nullable final Slot slot, final int slotId, final int mouseButton, final ContainerInput type) {
        if (slot != null) {
            final ItemStack scannerItemStack = menu.getPlayer().getItemInHand(menu.getHand());
            if (slot.getItem() == scannerItemStack) {
                return;
            }
            if (type == ContainerInput.SWAP && menu.getPlayer().getInventory().getItem(mouseButton) == scannerItemStack) {
                return;
            }
        }

        //noinspection ConstantConditions Missing nullable annotation, base method tests for null.
        super.slotClicked(slot, slotId, mouseButton, type);
    }
}
