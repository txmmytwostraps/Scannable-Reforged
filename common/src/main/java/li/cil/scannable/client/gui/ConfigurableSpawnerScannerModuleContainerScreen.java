package li.cil.scannable.client.gui;

import li.cil.scannable.common.config.Strings;
import li.cil.scannable.common.container.EntityModuleContainerMenu;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Config screen for the spawner module. Identical to the living-entity screen (mob slots + 3D
 * previews - the spawner is configured by the mobs its target spawners spawn) but with a "Spawners"
 * list caption.
 */
@Environment(EnvType.CLIENT)
public final class ConfigurableSpawnerScannerModuleContainerScreen extends ConfigurableEntityScannerModuleContainerScreen {
    // The parameter is the wider EntityModuleContainerMenu (the runtime instance is a
    // SpawnerModuleContainerMenu) so the menu-screen registration's generics line up.
    public ConfigurableSpawnerScannerModuleContainerScreen(final EntityModuleContainerMenu container, final Inventory inventory, final Component title) {
        super(container, inventory, title, Strings.GUI_SPAWNERS_LIST_CAPTION);
    }
}
