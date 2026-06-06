package li.cil.scannable.data.neoforge;

import net.minecraft.advancements.criterion.InventoryChangeTrigger;
import net.minecraft.advancements.criterion.LocationPredicate;
import net.minecraft.advancements.criterion.PlayerTrigger;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.neoforged.neoforge.common.Tags;

import java.util.concurrent.CompletableFuture;

import static li.cil.scannable.common.item.Items.*;

public final class ModRecipeProvider extends RecipeProvider {
    private ModRecipeProvider(final HolderLookup.Provider registries, final RecipeOutput output) {
        super(registries, output);
    }

    @Override
    protected void buildRecipes() {
        shaped(RecipeCategory.TOOLS, SCANNER.get())
            .pattern("i i")
            .pattern("brb")
            .pattern("gqg")
            .define('i', Tags.Items.INGOTS_IRON)
            .define('b', Items.IRON_BARS)
            .define('r', Tags.Items.DUSTS_REDSTONE)
            .define('g', Tags.Items.INGOTS_GOLD)
            .define('q', Tags.Items.GEMS_QUARTZ)
            .group("scanner")
            .unlockedBy("is_delving", PlayerTrigger.TriggerInstance.located(LocationPredicate.Builder.inStructure(registries.lookupOrThrow(Registries.STRUCTURE).getOrThrow(BuiltinStructures.MINESHAFT))))
            .save(output);

        shaped(RecipeCategory.MISC, BLANK_MODULE.get())
            .pattern("ggg")
            .pattern("crc")
            .pattern("cnc")
            .define('g', Tags.Items.DYES_GREEN)
            .define('c', Items.CLAY_BALL)
            .define('r', Tags.Items.DUSTS_GLOWSTONE)
            .define('n', Tags.Items.NUGGETS_GOLD)
            .group("blank_module")
            .unlockedBy("has_scanner", InventoryChangeTrigger.TriggerInstance.hasItems(SCANNER.get()))
            .save(output);

        module(RANGE_MODULE.get(), Tags.Items.ENDER_PEARLS);
        module(ENTITY_MODULE.get(), Items.LEAD);
        module(FRIENDLY_ENTITY_MODULE.get(), Tags.Items.LEATHERS);
        module(HOSTILE_ENTITY_MODULE.get(), Tags.Items.BONES);
        module(BLOCK_MODULE.get(), Tags.Items.STONES);
        module(COMMON_ORES_MODULE.get(), Items.COAL);
        module(RARE_ORES_MODULE.get(), Tags.Items.GEMS_DIAMOND);
        module(FLUID_MODULE.get(), Items.WATER_BUCKET);
        module(CHEST_MODULE.get(), Items.CHEST);

        shaped(RecipeCategory.MISC, SPAWNER_MODULE.get())
            .pattern("iii")
            .pattern("ibi")
            .pattern("iii")
            .define('i', Items.IRON_BARS)
            .define('b', BLANK_MODULE.get())
            .group("scanner_module")
            .unlockedBy("has_blank_module", InventoryChangeTrigger.TriggerInstance.hasItems(BLANK_MODULE.get()))
            .save(output);
    }

    private void module(final Item item, final TagKey<Item> ingredient) {
        shapeless(RecipeCategory.MISC, item)
            .requires(BLANK_MODULE.get())
            .requires(ingredient)
            .group("scanner_module")
            .unlockedBy("has_blank_module", InventoryChangeTrigger.TriggerInstance.hasItems(BLANK_MODULE.get()))
            .save(output);
    }

    private void module(final Item item, final Item ingredient) {
        shapeless(RecipeCategory.MISC, item)
            .requires(BLANK_MODULE.get())
            .requires(ingredient)
            .group("scanner_module")
            .unlockedBy("has_blank_module", InventoryChangeTrigger.TriggerInstance.hasItems(BLANK_MODULE.get()))
            .save(output);
    }

    // 26.1 datagen runs recipe providers via a Runner that supplies the registry lookup.
    public static final class Runner extends RecipeProvider.Runner {
        public Runner(final PackOutput output, final CompletableFuture<HolderLookup.Provider> registries) {
            super(output, registries);
        }

        @Override
        protected RecipeProvider createRecipeProvider(final HolderLookup.Provider registries, final RecipeOutput output) {
            return new ModRecipeProvider(registries, output);
        }

        @Override
        public String getName() {
            return "Scannable Recipes";
        }
    }
}
