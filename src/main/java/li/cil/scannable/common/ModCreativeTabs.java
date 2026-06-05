package li.cil.scannable.common;

import li.cil.scannable.api.API;
import li.cil.scannable.common.config.CommonConfig;
import li.cil.scannable.common.config.Strings;
import li.cil.scannable.common.energy.ItemEnergyStorage;
import li.cil.scannable.common.item.Items;
import li.cil.scannable.common.item.ModItem;
import li.cil.scannable.common.neoforge.ModEventBus;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, API.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> COMMON = TABS.register("common", () ->
            CreativeModeTab.builder()
                .icon(() -> new ItemStack(Items.SCANNER.get()))
                .title(Strings.CREATIVE_TAB_TITLE)
                .displayItems((parameters, output) -> {
                    if (CommonConfig.useEnergy) {
                        final var stack = new ItemStack(Items.SCANNER.get());
                        ItemEnergyStorage.of(stack).ifPresent(energy -> {
                            energy.receiveEnergy(Integer.MAX_VALUE, false);
                            output.accept(stack);
                        });
                    }

                    BuiltInRegistries.ITEM.stream()
                            .filter(item -> item instanceof ModItem)
                            .forEach(item -> output.accept(new ItemStack(item)));
                })
                .build());

    public static void initialize() {
        TABS.register(ModEventBus.INSTANCE);
    }
}
