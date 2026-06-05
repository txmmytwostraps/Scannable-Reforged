package li.cil.scannable.client.neoforge;

import li.cil.scannable.api.API;
import li.cil.scannable.client.ClientSetup;
import li.cil.scannable.client.ScanManager;
import li.cil.scannable.client.gui.ConfigurableBlockScannerModuleContainerScreen;
import li.cil.scannable.client.gui.ConfigurableEntityScannerModuleContainerScreen;
import li.cil.scannable.client.gui.ScannerContainerScreen;
import li.cil.scannable.client.renderer.OverlayRenderer;
import li.cil.scannable.client.renderer.ScannerRenderer;
import li.cil.scannable.common.container.Containers;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = API.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetupNeoForge {
    @SubscribeEvent
    public static void handleSetupEvent(final FMLClientSetupEvent event) {
        ClientSetup.initialize();

        NeoForge.EVENT_BUS.addListener(ClientSetupNeoForge::handleClientTickEvent);
        NeoForge.EVENT_BUS.addListener(ClientSetupNeoForge::handleRenderLevelEvent);
    }

    @SubscribeEvent
    public static void handleRegisterMenuScreens(final RegisterMenuScreensEvent event) {
        event.register(Containers.SCANNER_CONTAINER.get(), ScannerContainerScreen::new);
        event.register(Containers.BLOCK_MODULE_CONTAINER.get(), ConfigurableBlockScannerModuleContainerScreen::new);
        event.register(Containers.ENTITY_MODULE_CONTAINER.get(), ConfigurableEntityScannerModuleContainerScreen::new);
    }

    @SubscribeEvent
    public static void handleRegisterLayersEvent(final RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(API.MOD_ID, "scanner_results"), (guiGraphics, deltaTracker) -> {
            final float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
            ScanManager.renderGui(partialTick);
            OverlayRenderer.render(guiGraphics, partialTick);
        });
    }

    public static void handleClientTickEvent(final ClientTickEvent.Post event) {
        ScanManager.tick();
    }

    public static void handleRenderLevelEvent(final RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            // Render the scan-effect wave from this stable hook (NeoForge's
            // RenderLevelStageEvent) rather than a mid-renderLevel mixin, so the
            // depth grab and fullscreen blit see a consistent framebuffer every
            // frame. This is the same hook the scan results render from.
            ScannerRenderer.render(event.getModelViewMatrix(), event.getProjectionMatrix());

            ScanManager.setMatrices(event.getModelViewMatrix(), event.getProjectionMatrix());
            ScanManager.renderLevel(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        }
    }
}
