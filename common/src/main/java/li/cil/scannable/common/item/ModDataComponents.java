package li.cil.scannable.common.item;

import com.mojang.serialization.Codec;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import li.cil.scannable.util.RegistryUtils;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.List;

public final class ModDataComponents {
    private static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS = RegistryUtils.get(Registries.DATA_COMPONENT_TYPE);

    // --------------------------------------------------------------------- //

    // Configured entity-type ids for the configurable entity scanner module.
    public static final RegistrySupplier<DataComponentType<List<ResourceLocation>>> ENTITY_TYPES = DATA_COMPONENTS.register("entities", () ->
        DataComponentType.<List<ResourceLocation>>builder()
            .persistent(ResourceLocation.CODEC.listOf())
            .networkSynchronized(ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()))
            .build());

    // Configured block ids for the configurable block scanner module.
    public static final RegistrySupplier<DataComponentType<List<ResourceLocation>>> BLOCKS = DATA_COMPONENTS.register("blocks", () ->
        DataComponentType.<List<ResourceLocation>>builder()
            .persistent(ResourceLocation.CODEC.listOf())
            .networkSynchronized(ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()))
            .build());

    // Whether a configurable module's contents are locked against editing.
    public static final RegistrySupplier<DataComponentType<Boolean>> LOCKED = DATA_COMPONENTS.register("locked", () ->
        DataComponentType.<Boolean>builder()
            .persistent(Codec.BOOL)
            .networkSynchronized(ByteBufCodecs.BOOL)
            .build());

    // Modules installed in a scanner item.
    public static final RegistrySupplier<DataComponentType<ItemContainerContents>> MODULES = DATA_COMPONENTS.register("modules", () ->
        DataComponentType.<ItemContainerContents>builder()
            .persistent(ItemContainerContents.CODEC)
            .networkSynchronized(ItemContainerContents.STREAM_CODEC)
            .build());

    // --------------------------------------------------------------------- //

    public static void initialize() {
    }

    private ModDataComponents() {
    }
}
