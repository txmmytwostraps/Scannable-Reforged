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
import com.mojang.blaze3d.vertex.PoseStack;
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

    // Render the scan overlays in AfterLevel — i.e. AFTER the whole level, including the shader
    // composite under Iris/Oculus — so they draw on top of the final frame and stay visible with
    // shaders (this is what the 1.21.1 line does via AFTER_LEVEL; rendering mid-level in an earlier
    // stage gets discarded by the shader pipeline). getPoseStack() is null here, so build the pose
    // from the camera modelview matrix.
    public static void handleRenderLevel(final RenderLevelStageEvent.AfterLevel event) {
        final float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        final PoseStack poseStack = new PoseStack();
        poseStack.last().pose().set(event.getModelViewMatrix());
        ScanManager.renderLevel(poseStack, partialTick);
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
