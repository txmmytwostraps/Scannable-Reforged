package li.cil.scannable.client.neoforge;

import li.cil.scannable.api.API;
import li.cil.scannable.client.ClientSetup;
import li.cil.scannable.client.ScanManager;
import li.cil.scannable.client.renderer.OverlayRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterGuiOverlaysEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TickEvent;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = API.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetupNeoForge {
    @SubscribeEvent
    public static void handleSetupEvent(final FMLClientSetupEvent event) {
        ClientSetup.initialize();

        NeoForge.EVENT_BUS.addListener(ClientSetupNeoForge::handleClientTickEvent);
        NeoForge.EVENT_BUS.addListener(ClientSetupNeoForge::handleRenderLevelEvent);
    }

    @SubscribeEvent
    public static void handleRegisterOverlaysEvent(final RegisterGuiOverlaysEvent event) {
        event.registerAboveAll(new ResourceLocation(API.MOD_ID, "scanner_results"), (gui, poseStack, partialTick, width, height) -> {
            ScanManager.renderGui(partialTick);
            OverlayRenderer.render(poseStack, partialTick);
        });
    }

    public static void handleClientTickEvent(final TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ScanManager.tick();
        }
    }

    public static void handleRenderLevelEvent(final RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            ScanManager.setMatrices(event.getPoseStack(), event.getProjectionMatrix());
            ScanManager.renderLevel(event.getPartialTick());
        }
    }
}
