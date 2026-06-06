package li.cil.scannable.data.neoforge;

import li.cil.scannable.api.API;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

@EventBusSubscriber(modid = API.MOD_ID)
public final class DataGenerators {
    // 26.1 split datagen into a Server event (data pack: recipes, tags) and a Client event
    // (resource pack: item models). Providers are built from the event's pack output / lookup.
    @SubscribeEvent
    public static void gatherServerData(final GatherDataEvent.Server event) {
        event.createProvider(ModRecipeProvider.Runner::new);
        event.createProvider(ModBlockTagsProvider::new);
        event.createProvider(ModItemTagsProvider::new);
    }

    @SubscribeEvent
    public static void gatherClientData(final GatherDataEvent.Client event) {
        event.createProvider(ModItemModelProvider::new);
    }
}
