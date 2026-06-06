package li.cil.scannable.data.neoforge;

import li.cil.scannable.api.API;
import li.cil.scannable.common.item.Items;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.util.Objects;

public final class ModItemModelProvider extends ModelProvider {
    // The two shared layers under every module's distinguishing overlay.
    private static final Material BASE_LAYER = layer("blank_module");
    private static final Material SLOT_LAYER = layer("module_slot");

    public ModItemModelProvider(final PackOutput output) {
        super(output, API.MOD_ID);
    }

    @Override
    protected void registerModels(final BlockModelGenerators blockModels, final ItemModelGenerators itemModels) {
        // Plain single-layer items.
        itemModels.generateFlatItem(Items.SCANNER.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(Items.BLANK_MODULE.get(), ModelTemplates.FLAT_ITEM);

        // Modules: blank-module base + module-slot frame + the module's own overlay (three layers).
        module(itemModels, Items.RANGE_MODULE.get());
        module(itemModels, Items.ENTITY_MODULE.get());
        module(itemModels, Items.FRIENDLY_ENTITY_MODULE.get());
        module(itemModels, Items.HOSTILE_ENTITY_MODULE.get());
        module(itemModels, Items.BLOCK_MODULE.get());
        module(itemModels, Items.COMMON_ORES_MODULE.get());
        module(itemModels, Items.RARE_ORES_MODULE.get());
        module(itemModels, Items.FLUID_MODULE.get());
        module(itemModels, Items.CHEST_MODULE.get());
        module(itemModels, Items.SPAWNER_MODULE.get());
    }

    private static void module(final ItemModelGenerators itemModels, final Item item) {
        final String path = Objects.requireNonNull(BuiltInRegistries.ITEM.getKey(item)).getPath();
        final TextureMapping textures = TextureMapping.layered(BASE_LAYER, SLOT_LAYER, layer(path));
        final Identifier model = ModelTemplates.THREE_LAYERED_ITEM.create(item, textures, itemModels.modelOutput);
        itemModels.itemModelOutput.accept(item, ItemModelUtils.plainModel(model));
    }

    private static Material layer(final String texture) {
        return new Material(Identifier.fromNamespaceAndPath(API.MOD_ID, "item/" + texture));
    }
}
