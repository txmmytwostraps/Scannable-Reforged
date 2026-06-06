package li.cil.scannable.client.neoforge;

import li.cil.scannable.api.API;
import li.cil.scannable.client.ClientSetup;
import li.cil.scannable.client.ScanManager;
import li.cil.scannable.client.gui.ConfigurableBlockScannerModuleContainerScreen;
import li.cil.scannable.client.gui.ConfigurableEntityScannerModuleContainerScreen;
import li.cil.scannable.client.gui.ConfigurableSpawnerScannerModuleContainerScreen;
import li.cil.scannable.client.gui.ScannerContainerScreen;
import li.cil.scannable.client.renderer.OverlayRenderer;
import li.cil.scannable.client.renderer.ScanResultRenderType;
import li.cil.scannable.client.renderer.ScannerRenderer;
import li.cil.scannable.common.container.Containers;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

@EventBusSubscriber(modid = API.MOD_ID, value = Dist.CLIENT)
public final class ClientSetupNeoForge {
    @SubscribeEvent
    public static void handleSetupEvent(final FMLClientSetupEvent event) {
        ClientSetup.initialize();

        NeoForge.EVENT_BUS.addListener(ClientSetupNeoForge::handleClientTickEvent);
        NeoForge.EVENT_BUS.addListener(ClientSetupNeoForge::handleRenderLevel);
    }

    // 26.1 RenderLevelStageEvent is split into per-stage subclasses; AfterTranslucentBlocks provides
    // a non-null pose and renders after translucent terrain (good for the scan result boxes).
    // TODO(Phase 3c): re-add the fullscreen scan-effect + GUI overlay (OverlayRenderer / result GUI).
    public static void handleRenderLevel(final RenderLevelStageEvent.AfterTranslucentBlocks event) {
        final float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        ScanManager.renderLevel(event.getPoseStack(), partialTick);
        // The scan-reveal wave is a separate fullscreen pass (depth-buffer reconstruction); it plays
        // for the ping duration regardless of how many results are currently shown.
        ScannerRenderer.INSTANCE.render(event.getModelViewMatrix());
    }

    @SubscribeEvent
    public static void handleRegisterRenderPipelines(final RegisterRenderPipelinesEvent event) {
        event.registerPipeline(ScanResultRenderType.PIPELINE);
        event.registerPipeline(ScanResultRenderType.LINES_PIPELINE);
        event.registerPipeline(ScanResultRenderType.ICON_PIPELINE);
        event.registerPipeline(ScanResultRenderType.SCAN_EFFECT_PIPELINE);
        event.registerPipeline(ScanResultRenderType.SHIMMER_PIPELINE);
        event.registerPipeline(ScanResultRenderType.SCAN_PROGRESS_PIPELINE);
    }

    @SubscribeEvent
    public static void handleRegisterGuiLayers(final RegisterGuiLayersEvent event) {
        // The radial scan-progress ring shown while channeling the scanner.
        event.registerAboveAll(Identifier.fromNamespaceAndPath(API.MOD_ID, "scan_progress"),
            (graphics, deltaTracker) -> OverlayRenderer.render(graphics, deltaTracker.getGameTimeDeltaPartialTick(false)));
    }

    @SubscribeEvent
    public static void handleRegisterMenuScreens(final RegisterMenuScreensEvent event) {
        event.register(Containers.SCANNER_CONTAINER.get(), ScannerContainerScreen::new);
        event.register(Containers.BLOCK_MODULE_CONTAINER.get(), ConfigurableBlockScannerModuleContainerScreen::new);
        event.register(Containers.ENTITY_MODULE_CONTAINER.get(), ConfigurableEntityScannerModuleContainerScreen::new);
        event.register(Containers.SPAWNER_MODULE_CONTAINER.get(), ConfigurableSpawnerScannerModuleContainerScreen::new);
    }

    public static void handleClientTickEvent(final ClientTickEvent.Post event) {
        ScanManager.tick();
    }
}
