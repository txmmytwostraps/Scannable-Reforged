package li.cil.scannable.client.scanning;

import com.mojang.blaze3d.vertex.PoseStack;
import li.cil.scannable.api.API;
import li.cil.scannable.api.prefab.AbstractScanResultProvider;
import li.cil.scannable.api.scanning.EntityScannerModule;
import li.cil.scannable.api.scanning.ScanResult;
import li.cil.scannable.api.scanning.ScanResultRenderContext;
import li.cil.scannable.api.scanning.ScannerModule;
import li.cil.scannable.common.item.ScannerModuleItem;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

@OnlyIn(Dist.CLIENT)
public final class ScanResultProviderEntity extends AbstractScanResultProvider {
    private final List<Predicate<Entity>> filters = new ArrayList<>();
    private final Map<Predicate<Entity>, EntityScannerModule> filterToModule = new HashMap<>();
    private final ArrayList<Entity> entities = new ArrayList<>();
    private int currentEntityIndex, entitiesStep;
    private final List<ScanResultEntity> results = new ArrayList<>();

    // --------------------------------------------------------------------- //
    // ScanResultProvider

    @Override
    public void initialize(final Player player, final Collection<ItemStack> modules, final Vec3 center, final float radius, final int scanTicks) {
        super.initialize(player, modules, center, radius, scanTicks);

        filters.clear();
        filterToModule.clear();
        for (final ItemStack stack : modules) {
            final Optional<ScannerModule> capability = ScannerModuleItem.getModule(stack);
            capability.ifPresent(module -> {
                if (module instanceof EntityScannerModule entityModule) {
                    final Predicate<Entity> filter = entityModule.getFilter(stack);
                    filters.add(filter);
                    filterToModule.put(filter, entityModule);
                }
            });
        }

        entities.clear();
        for (final Entity entity : player.level().getEntities().getAll()) {
            entities.add(entity);
        }
        currentEntityIndex = 0;
        entitiesStep = Mth.ceil(entities.size() / (float) scanTicks);
    }

    @Override
    public void computeScanResults() {
        for (final int end = Math.min(currentEntityIndex + entitiesStep, entities.size()); currentEntityIndex < end; currentEntityIndex++) {
            final Entity entity = entities.get(currentEntityIndex);
            if (!entity.isAlive()) {
                continue;
            }

            final Vec3 position = entity.position();
            if (center.distanceToSqr(position) < radius * radius) {
                Identifier icon = API.ICON_INFO;
                boolean hasMatch = false;
                for (final Predicate<Entity> filter : filters) {
                    if (filter.test(entity)) {
                        hasMatch = true;
                        final Optional<Identifier> filterIcon = filterToModule.get(filter).getIcon(entity);
                        if (filterIcon.isPresent()) {
                            icon = filterIcon.get();
                            break;
                        }
                    }
                }
                if (hasMatch) {
                    results.add(new ScanResultEntity(entity, icon));
                }
            }
        }
    }

    @Override
    public void collectScanResults(final BlockGetter level, final Consumer<ScanResult> callback) {
        results.forEach(callback);
    }

    @Override
    public void render(final ScanResultRenderContext context, final MultiBufferSource bufferSource, final PoseStack poseStack, final Camera renderInfo, final float partialTicks, final List<ScanResult> results) {
        // TODO(Phase 3b): rebuild GUI entity-label rendering once renderIconLabel is restored. The
        // old path read Camera.getYRot/getXRot/getLookVector/getPosition (changed in 26.1) and drew
        // via the removed RenderType path. Stubbed for the launchable build.
    }

    @Override
    public void reset() {
        super.reset();
        filters.clear();
        filterToModule.clear();
        currentEntityIndex = 0;
        entitiesStep = 0;
        entities.clear();
        results.clear();
    }

    // --------------------------------------------------------------------- //

    private record ScanResultEntity(Entity entity, Identifier icon) implements ScanResult {
        public Identifier getIcon() {
            return icon;
        }

        // --------------------------------------------------------------------- //
        // ScanResult

        @Override
        public Vec3 getPosition() {
            return entity.position();
        }

        @Override
        public AABB getRenderBounds() {
            return entity.getBoundingBox();
        }
    }
}
