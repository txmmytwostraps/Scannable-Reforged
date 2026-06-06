package li.cil.scannable.client.scanning;

import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import li.cil.scannable.api.API;
import li.cil.scannable.api.prefab.AbstractScanResultProvider;
import li.cil.scannable.api.scanning.BlockScannerModule;
import li.cil.scannable.api.scanning.ScanResult;
import li.cil.scannable.api.scanning.ScanResultRenderContext;
import li.cil.scannable.api.scanning.ScannerModule;
import li.cil.scannable.client.ClientConfig;
import li.cil.scannable.common.integration.lootr.LootrIntegration;
import li.cil.scannable.common.item.ScannerModuleItem;
import li.cil.scannable.common.scanning.ConfigurableSpawnerScannerModule;
import li.cil.scannable.common.scanning.filter.IgnoredBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import li.cil.scannable.client.renderer.ScanResultRenderType;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SpawnerBlock;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class ScanResultProviderBlock extends AbstractScanResultProvider {
    // Sanity performance check. Maybe some day I'll do some research on how to
    // do the clustering more efficiently, but for now this is good enough. We
    // really only need this when scanning for stupid stuff like stone.
    private static final int MAX_RESULTS_PER_BLOCK = 8192;
    private static final int DEFAULT_COLOR = 0x4466CC;
    // Above this cell count a cluster is highlighted as its bounding box rather than its per-cell
    // surface, to bound the per-frame geometry + shape-build cost (e.g. a block module on stone).
    private static final int MAX_CONTOUR_CELLS = 256;

    private final List<ScanFilterLayer> scanFilterLayers = new ArrayList<>();
    private final List<ChunkSectionPos> pendingChunkSections = new ArrayList<>();
    private int currentChunkSection, chunkSectionsPerTick;
    private final Map<Block, Map<BlockPos, BlockScanResult>> resultClusters = new HashMap<>();
    private final List<BlockScanResult> results = new ArrayList<>();

    // Spawner mob narrowing, gathered from spawner modules. matchAll = an unconfigured spawner module
    // is present (highlight every spawner); otherwise only spawners whose mob is in mobFilter pass.
    private boolean spawnerMatchAll;
    private final Set<EntityType<?>> spawnerMobFilter = new HashSet<>();

    // --------------------------------------------------------------------- //
    // ScanResultProvider

    @Override
    public void initialize(final Player player, final Collection<ItemStack> modules, final Vec3 center, final float radius, final int scanTicks) {
        super.initialize(player, modules, center, radius, scanTicks);

        scanFilterLayers.clear();
        spawnerMatchAll = false;
        spawnerMobFilter.clear();

        final IntObjectMap<List<Predicate<BlockState>>> filterByRadius = new IntObjectHashMap<>();
        for (final ItemStack stack : modules) {
            final Optional<ScannerModule> capability = ScannerModuleItem.getModule(stack);
            capability.ifPresent(module -> {
                if (module instanceof BlockScannerModule blockModule) {
                    final Predicate<BlockState> filter = blockModule.getFilter(stack);
                    final int localRadius = (int) Math.ceil(blockModule.adjustLocalRange(this.radius));
                    filterByRadius.computeIfAbsent(localRadius, r -> new ArrayList<>()).add(filter);
                }
                if (module instanceof final ConfigurableSpawnerScannerModule spawnerModule) {
                    final List<EntityType<?>> types = spawnerModule.getEntityTypes(stack);
                    if (types.isEmpty()) {
                        spawnerMatchAll = true; // unconfigured -> all spawners
                    } else {
                        spawnerMobFilter.addAll(types);
                    }
                }
            });
        }

        final IntList scanFilterKeys = new IntArrayList();
        scanFilterKeys.addAll(filterByRadius.keySet());
        scanFilterKeys.sort((a, b) -> -Integer.compare(a, b));

        if (!scanFilterKeys.isEmpty()) {
            this.radius = scanFilterKeys.getInt(0);
            for (final int r : scanFilterKeys) {
                scanFilterLayers.add(new ScanFilterLayer(r, filterByRadius.get(r)));
            }

            final BlockPos minBlockPos = BlockPos.containing(center).offset(-this.radius, -this.radius, -this.radius);
            final BlockPos maxBlockPos = BlockPos.containing(center).offset(this.radius, this.radius, this.radius);
            final ChunkPos minChunkPos = new ChunkPos(minBlockPos.getX() >> 4, minBlockPos.getZ() >> 4);
            final ChunkPos maxChunkPos = new ChunkPos(maxBlockPos.getX() >> 4, maxBlockPos.getZ() >> 4);

            final int minChunkSectionIndex = Math.max(player.level().getSectionIndex(minBlockPos.getY()), 0);
            final int maxChunkSectionIndex = Math.min(player.level().getSectionIndex(maxBlockPos.getY()), player.level().getSectionsCount() - 1);

            for (int chunkSectionIndex = minChunkSectionIndex; chunkSectionIndex <= maxChunkSectionIndex; chunkSectionIndex++) {
                for (int chunkZ = minChunkPos.z(); chunkZ <= maxChunkPos.z(); chunkZ++) {
                    for (int chunkX = minChunkPos.x(); chunkX <= maxChunkPos.x(); chunkX++) {
                        final ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
                        final int chunkY = player.level().getSectionYFromSectionIndex(chunkSectionIndex);

                        final double dx = Math.min(
                            Math.abs(chunkPos.getMinBlockX() - center.x),
                            Math.abs(chunkPos.getMaxBlockX() - center.x));
                        final double dz = Math.min(
                            Math.abs(chunkPos.getMinBlockZ() - center.z),
                            Math.abs(chunkPos.getMaxBlockZ() - center.z));
                        final double dy = Math.min(
                            Math.abs(SectionPos.sectionToBlockCoord(chunkY, 0) - center.y),
                            Math.abs(SectionPos.sectionToBlockCoord(chunkY, SectionPos.SECTION_MAX_INDEX) - center.y));
                        final double squareDistToCenter = dx * dx + dy * dy + dz * dz;

                        if (squareDistToCenter > radius * radius) {
                            continue;
                        }

                        pendingChunkSections.add(new ChunkSectionPos(chunkX, chunkZ, chunkSectionIndex, squareDistToCenter));
                    }
                }
            }

            pendingChunkSections.sort(Comparator.comparingDouble(p -> p.squareDistToCenter));

            chunkSectionsPerTick = Mth.ceil(pendingChunkSections.size() / (float) scanTicks);
            this.currentChunkSection = 0;
        }
    }

    @Override
    public void computeScanResults() {
        final Level level = player.level();
        for (int i = 0; i < chunkSectionsPerTick; i++) {
            if (currentChunkSection >= pendingChunkSections.size()) {
                return;
            }

            final ChunkSectionPos chunkSectionPos = pendingChunkSections.get(currentChunkSection);
            currentChunkSection++;

            final int chunkX = chunkSectionPos.chunkX;
            final int chunkZ = chunkSectionPos.chunkZ;
            final int chunkSectionIndex = chunkSectionPos.chunkSectionIndex;

            final ChunkAccess chunk = level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
            if (chunk == null) {
                continue;
            }

            final LevelChunkSection[] sections = chunk.getSections();
            final LevelChunkSection section = sections[chunkSectionIndex];
            if (section == null || section.hasOnlyAir()) {
                continue;
            }

            final int bottomBlockY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(chunkSectionIndex));
            final PalettedContainer<BlockState> palette = section.getStates();
            final BlockPos origin = chunk.getPos().getWorldPosition().offset(0, bottomBlockY, 0);
            final int originX = origin.getX();
            final int originY = origin.getY();
            final int originZ = origin.getZ();
            for (int index = 0; index < 16 * 16 * 16; index++) {
                final BlockState state = palette.get(index);
                final Block block = state.getBlock();
                final Map<BlockPos, BlockScanResult> clusters = resultClusters.computeIfAbsent(block, b -> new HashMap<>());
                if (clusters.size() > MAX_RESULTS_PER_BLOCK) {
                    continue;
                }

                if (IgnoredBlocks.contains(state)) {
                    continue;
                }

                final int x = index & 0xf;
                final int z = (index >> 4) & 0xf;
                final int y = (index >> 8) & 0xf;

                final int globalX = originX + x;
                final int globalY = originY + y;
                final int globalZ = originZ + z;

                final double squaredDistance = center.distanceToSqr(globalX + 0.5, globalY + 0.5, globalZ + 0.5);

                outer:
                for (final ScanFilterLayer layer : scanFilterLayers) {
                    if (squaredDistance > layer.radius * layer.radius) {
                        break; // Filters radii only get smaller in the sorted filter list.
                    }

                    for (final Predicate<BlockState> filter : layer.filters) {
                        if (filter.test(state)) {
                            final BlockPos pos = new BlockPos(globalX, globalY, globalZ);
                            if (!tryAddToCluster(clusters, pos)) {
                                final BlockScanResult result = new BlockScanResult(state.getBlock(), pos);
                                clusters.put(pos, result);
                                results.add(result);
                            }
                            break outer;
                        }
                    }
                }
            }
        }
    }

    @Override
    public void collectScanResults(final BlockGetter level, final Consumer<ScanResult> callback) {
        for (final BlockScanResult result : results) {
            if (result.isRoot()) {
                result.bake(level);

                // Spawner mob narrowing: when a mob filter is active, drop spawners that don't spawn a
                // configured mob (including empty spawners). Non-spawner results are unaffected.
                if (result.block instanceof SpawnerBlock && !spawnerMatchAll && !spawnerMobFilter.isEmpty()
                    && (result.spawnerType == null || !spawnerMobFilter.contains(result.spawnerType))) {
                    continue;
                }

                callback.accept(result);
            }
        }
    }

    @Override
    public void render(final ScanResultRenderContext context, final MultiBufferSource bufferSource, final PoseStack poseStack, final Camera renderInfo, final float partialTicks, final List<ScanResult> results) {
        if (context == ScanResultRenderContext.GUI) {
            renderBlockIcons(bufferSource, poseStack, renderInfo, results);
            return;
        }
        if (context != ScanResultRenderContext.WORLD) {
            return;
        }

        final PoseStack.Pose pose = poseStack.last();

        // Animated shimmer fill (additive scanlines + pulse + edge glow), contouring the actual ore
        // cells. The shader's edge glow IS the outline; UVs span the whole cluster so the glow sits on
        // the cluster's outer perimeter (one smooth outline) rather than around every cell. No separate
        // wireframe pass — that's what made multi-block clusters look like a noisy grid of cubes.
        final VertexConsumer fill = bufferSource.getBuffer(ScanResultRenderType.SHIMMER_TYPE);
        for (final ScanResult result : results) {
            final BlockScanResult br = (BlockScanResult) result;
            final int c = br.color;
            final float r = ((c >> 16) & 0xFF) / 255.0f, g = ((c >> 8) & 0xFF) / 255.0f, b = (c & 0xFF) / 255.0f;
            if (br.blocks.size() <= MAX_CONTOUR_CELLS) {
                // Hide-broken-blocks: contour only the cells that are still the scanned block in the
                // world, so a cluster's highlight shrinks live as you mine it (no rescan needed). A
                // fully-mined cluster drops out entirely.
                final Set<BlockPos> cells = visibleCells(br);
                if (cells.isEmpty()) {
                    continue;
                }
                addClusterFill(fill, pose, cells, br.bounds, r, g, b, 1.0f);
            } else {
                addBox(fill, pose, br.bounds, r, g, b, 1.0f);
            }
        }
    }

    // The cells of a cluster still present (and, for Lootr containers, not yet looted) in the world.
    // When neither check applies (or there's no client level), this is the full cell set. Cells in
    // unloaded chunks are treated as present so we don't wrongly clear a highlight the player just
    // walked away from.
    private static Set<BlockPos> visibleCells(final BlockScanResult br) {
        final boolean checkBroken = ClientConfig.hideBrokenBlocks;
        if (!br.lootr && !checkBroken) {
            return br.blocks;
        }
        final Level level = Minecraft.getInstance().level;
        if (level == null) {
            return br.blocks;
        }
        final Player player = Minecraft.getInstance().player;
        final Set<BlockPos> visible = new HashSet<>();
        for (final BlockPos cell : br.blocks) {
            if (cellPresent(level, player, br, cell, checkBroken)) {
                visible.add(cell);
            }
        }
        return visible;
    }

    // Cheap short-circuit form of visibleCells for the label pass: does the cluster still have any
    // visible cell? Avoids allocating a set just to test emptiness.
    private static boolean hasVisibleCells(final BlockScanResult br) {
        final boolean checkBroken = ClientConfig.hideBrokenBlocks;
        if (!br.lootr && !checkBroken) {
            return true;
        }
        final Level level = Minecraft.getInstance().level;
        if (level == null) {
            return true;
        }
        final Player player = Minecraft.getInstance().player;
        for (final BlockPos cell : br.blocks) {
            if (cellPresent(level, player, br, cell, checkBroken)) {
                return true;
            }
        }
        return false;
    }

    private static boolean cellPresent(final Level level, @Nullable final Player player, final BlockScanResult br, final BlockPos cell, final boolean checkBroken) {
        if (!level.hasChunkAt(cell)) {
            return true; // Unloaded -> unknown, assume present.
        }
        if (br.lootr) {
            // Lootr results manage their own presence regardless of the hide-broken-blocks toggle: a
            // container drops out once it's been removed, OR once this player has looted it (live,
            // exactly like Lootr's own per-player "already looted" rendering).
            return LootrIntegration.isContainer(level.getBlockState(cell))
                && !LootrIntegration.isClientLooted(level, cell, player);
        }
        return !checkBroken || level.getBlockState(cell).is(br.block);
    }

    private void renderBlockIcons(final MultiBufferSource bufferSource, final PoseStack poseStack, final Camera renderInfo, final List<ScanResult> results) {
        final org.joml.Vector3fc forward = renderInfo.forwardVector();
        final Vec3 lookVec = new Vec3(forward.x(), forward.y(), forward.z());
        final Vec3 viewerEyes = renderInfo.position();
        final float yaw = renderInfo.yRot();
        final float pitch = renderInfo.xRot();
        final boolean showDistance = renderInfo.entity() != null && renderInfo.entity().isShiftKeyDown();

        // Order results by deviation from the look vector so the one we look at draws in front.
        results.sort(Comparator.comparing(result -> {
            final Vec3 resultPos = result.getPosition();
            return lookVec.dot(resultPos.subtract(viewerEyes).normalize());
        }));

        for (final ScanResult result : results) {
            final BlockScanResult blockResult = (BlockScanResult) result;

            // Don't label a cluster whose blocks have all been mined since the last scan.
            if (!hasVisibleCells(blockResult)) {
                continue;
            }

            final Vec3 resultPos = result.getPosition();
            final float lookDirDot = (float) lookVec.dot(resultPos.subtract(viewerEyes).normalize());

            final Component label = blockResult.label != null ? blockResult.label : blockResult.block.getName();
            if (lookDirDot > 0.98f && !label.getString().isEmpty()) {
                final float distance = showDistance ? (float) resultPos.subtract(viewerEyes).length() : 0f;
                renderIconLabel(bufferSource, poseStack, yaw, pitch, lookVec, viewerEyes, distance, resultPos, API.ICON_INFO, label);
            }
        }
    }

    private static void addBox(final VertexConsumer buffer, final PoseStack.Pose pose, final AABB box, final float r, final float g, final float b, final float a) {
        final float x0 = (float) box.minX, y0 = (float) box.minY, z0 = (float) box.minZ;
        final float x1 = (float) box.maxX, y1 = (float) box.maxY, z1 = (float) box.maxZ;
        // Single box (huge-cluster fallback): 0..1 UV per face spans the whole box = the cluster, so
        // the shimmer's edge glow outlines the box. Cull is off, so winding is irrelevant.
        vertex(buffer, pose, x0, y0, z0, 0, 0, r, g, b, a); vertex(buffer, pose, x0, y0, z1, 0, 1, r, g, b, a); vertex(buffer, pose, x0, y1, z1, 1, 1, r, g, b, a); vertex(buffer, pose, x0, y1, z0, 1, 0, r, g, b, a);
        vertex(buffer, pose, x1, y0, z1, 0, 1, r, g, b, a); vertex(buffer, pose, x1, y0, z0, 0, 0, r, g, b, a); vertex(buffer, pose, x1, y1, z0, 1, 0, r, g, b, a); vertex(buffer, pose, x1, y1, z1, 1, 1, r, g, b, a);
        vertex(buffer, pose, x0, y0, z0, 0, 0, r, g, b, a); vertex(buffer, pose, x1, y0, z0, 1, 0, r, g, b, a); vertex(buffer, pose, x1, y0, z1, 1, 1, r, g, b, a); vertex(buffer, pose, x0, y0, z1, 0, 1, r, g, b, a);
        vertex(buffer, pose, x0, y1, z1, 0, 1, r, g, b, a); vertex(buffer, pose, x1, y1, z1, 1, 1, r, g, b, a); vertex(buffer, pose, x1, y1, z0, 1, 0, r, g, b, a); vertex(buffer, pose, x0, y1, z0, 0, 0, r, g, b, a);
        vertex(buffer, pose, x1, y0, z0, 1, 0, r, g, b, a); vertex(buffer, pose, x0, y0, z0, 0, 0, r, g, b, a); vertex(buffer, pose, x0, y1, z0, 0, 1, r, g, b, a); vertex(buffer, pose, x1, y1, z0, 1, 1, r, g, b, a);
        vertex(buffer, pose, x0, y0, z1, 0, 0, r, g, b, a); vertex(buffer, pose, x1, y0, z1, 1, 0, r, g, b, a); vertex(buffer, pose, x1, y1, z1, 1, 1, r, g, b, a); vertex(buffer, pose, x0, y1, z1, 0, 1, r, g, b, a);
    }

    private static void addClusterFill(final VertexConsumer buffer, final PoseStack.Pose pose, final Set<BlockPos> cells, final AABB bounds, final float r, final float g, final float b, final float a) {
        // UVs span the whole cluster (not each cell), so the shimmer shader's edge glow lands on the
        // cluster's outer perimeter as one smooth outline instead of around every cell face.
        final float sx = (float) (1.0 / bounds.getXsize());
        final float sy = (float) (1.0 / bounds.getYsize());
        final float sz = (float) (1.0 / bounds.getZsize());
        final float bx = (float) bounds.minX, by = (float) bounds.minY, bz = (float) bounds.minZ;
        for (final BlockPos cell : cells) {
            final float x0 = cell.getX(), y0 = cell.getY(), z0 = cell.getZ();
            final float x1 = x0 + 1.0f, y1 = y0 + 1.0f, z1 = z0 + 1.0f;
            final float ux0 = (x0 - bx) * sx, ux1 = (x1 - bx) * sx;
            final float uy0 = (y0 - by) * sy, uy1 = (y1 - by) * sy;
            final float uz0 = (z0 - bz) * sz, uz1 = (z1 - bz) * sz;
            // Only the outer faces (neighbour cell not in the cluster). UV uses the two tangent axes.
            if (!cells.contains(cell.west())) {
                vertex(buffer, pose, x0, y0, z0, uy0, uz0, r, g, b, a);
                vertex(buffer, pose, x0, y0, z1, uy0, uz1, r, g, b, a);
                vertex(buffer, pose, x0, y1, z1, uy1, uz1, r, g, b, a);
                vertex(buffer, pose, x0, y1, z0, uy1, uz0, r, g, b, a);
            }
            if (!cells.contains(cell.east())) {
                vertex(buffer, pose, x1, y0, z1, uy0, uz1, r, g, b, a);
                vertex(buffer, pose, x1, y0, z0, uy0, uz0, r, g, b, a);
                vertex(buffer, pose, x1, y1, z0, uy1, uz0, r, g, b, a);
                vertex(buffer, pose, x1, y1, z1, uy1, uz1, r, g, b, a);
            }
            if (!cells.contains(cell.below())) {
                vertex(buffer, pose, x0, y0, z0, ux0, uz0, r, g, b, a);
                vertex(buffer, pose, x1, y0, z0, ux1, uz0, r, g, b, a);
                vertex(buffer, pose, x1, y0, z1, ux1, uz1, r, g, b, a);
                vertex(buffer, pose, x0, y0, z1, ux0, uz1, r, g, b, a);
            }
            if (!cells.contains(cell.above())) {
                vertex(buffer, pose, x0, y1, z1, ux0, uz1, r, g, b, a);
                vertex(buffer, pose, x1, y1, z1, ux1, uz1, r, g, b, a);
                vertex(buffer, pose, x1, y1, z0, ux1, uz0, r, g, b, a);
                vertex(buffer, pose, x0, y1, z0, ux0, uz0, r, g, b, a);
            }
            if (!cells.contains(cell.north())) {
                vertex(buffer, pose, x1, y0, z0, ux1, uy0, r, g, b, a);
                vertex(buffer, pose, x0, y0, z0, ux0, uy0, r, g, b, a);
                vertex(buffer, pose, x0, y1, z0, ux0, uy1, r, g, b, a);
                vertex(buffer, pose, x1, y1, z0, ux1, uy1, r, g, b, a);
            }
            if (!cells.contains(cell.south())) {
                vertex(buffer, pose, x0, y0, z1, ux0, uy0, r, g, b, a);
                vertex(buffer, pose, x1, y0, z1, ux1, uy0, r, g, b, a);
                vertex(buffer, pose, x1, y1, z1, ux1, uy1, r, g, b, a);
                vertex(buffer, pose, x0, y1, z1, ux0, uy1, r, g, b, a);
            }
        }
    }

    private static void vertex(final VertexConsumer buffer, final PoseStack.Pose pose, final float x, final float y, final float z, final float u, final float v, final float r, final float g, final float b, final float a) {
        buffer.addVertex(pose, x, y, z).setUv(u, v).setColor(r, g, b, a);
    }

    @Override
    public void reset() {
        super.reset();
        scanFilterLayers.clear();
        currentChunkSection = chunkSectionsPerTick = 0;
        pendingChunkSections.clear();
        resultClusters.clear();
        results.clear();
    }

    // --------------------------------------------------------------------- //

    private boolean tryAddToCluster(final Map<BlockPos, BlockScanResult> clusters, final BlockPos pos) {
        BlockScanResult root = null;
        root = tryAddToCluster(clusters, pos, pos.east(), root);
        root = tryAddToCluster(clusters, pos, pos.west(), root);
        root = tryAddToCluster(clusters, pos, pos.north(), root);
        root = tryAddToCluster(clusters, pos, pos.south(), root);
        root = tryAddToCluster(clusters, pos, pos.above(), root);
        root = tryAddToCluster(clusters, pos, pos.below(), root);
        return root != null;
    }

    @Nullable
    private BlockScanResult tryAddToCluster(final Map<BlockPos, BlockScanResult> clusters, final BlockPos pos, final BlockPos clusterPos, @Nullable BlockScanResult root) {
        final BlockScanResult cluster = clusters.get(clusterPos);
        if (cluster == null) {
            return root;
        }

        if (root == null) {
            root = cluster.getRoot();
            root.add(pos);
            clusters.put(pos, root);
        } else {
            cluster.getRoot().setRoot(root);
        }

        return root;
    }

    private record ScanFilterLayer(int radius, List<Predicate<BlockState>> filters) {
    }

    private record ChunkSectionPos(int chunkX, int chunkZ, int chunkSectionIndex, double squareDistToCenter) {
    }

    // --------------------------------------------------------------------- //

    private static final class BlockScanResult implements ScanResult {
        private final Block block;
        private AABB bounds;
        @Nullable private BlockScanResult parent;
        private final Set<BlockPos> blocks;
        private int color;
        // Overrides the generic block name in the looking-at label (e.g. "Zombie Spawner").
        @Nullable private Component label;
        // The mob this result's spawner spawns (null for non-spawners / empty spawners); drives the
        // spawner mob filter.
        @Nullable private EntityType<?> spawnerType;
        // This result is a Lootr loot container: highlight gold and hide live once looted.
        private boolean lootr;

        BlockScanResult(final Block block, final BlockPos pos) {
            this.block = block;
            bounds = new AABB(pos);
            blocks = new HashSet<>();
            blocks.add(pos);
        }

        void bake(final BlockGetter level) {
            final BlockState blockState = block.defaultBlockState();

            color = blockState.getMapColor(level, BlockPos.containing(bounds.getCenter())).col;

            final FluidState fluidState = blockState.getFluidState();
            if (!fluidState.isEmpty()) {
                if (ClientConfig.fluidColors.containsKey(BuiltInRegistries.FLUID.getKey(fluidState.getType()))) {
                    color = ClientConfig.fluidColors.getInt(BuiltInRegistries.FLUID.getKey(fluidState.getType()));
                } else {
                    ClientConfig.fluidTagColors.forEach((k, v) -> {
                        final var tag = TagKey.create(Registries.FLUID, k);
                        if (fluidState.is(tag)) {
                            color = v;
                        }
                    });
                }
            } else {
                if (ClientConfig.blockColors.containsKey(BuiltInRegistries.BLOCK.getKey(blockState.getBlock()))) {
                    color = ClientConfig.blockColors.getInt(BuiltInRegistries.BLOCK.getKey(blockState.getBlock()));
                } else {
                    ClientConfig.blockTagColors.forEach((k, v) -> {
                        final var tag = TagKey.create(Registries.BLOCK, k);
                        if (blockState.is(tag)) {
                            color = v;
                        }
                    });
                }
            }

            if (color == 0) { // E.g. glass.
                color = DEFAULT_COLOR;
            }

            // Spawners: label with the mob they spawn ("Zombie Spawner") instead of the generic block
            // name. Only spawner blocks pay this cost (one already-synced, cached display entity per
            // result); vanilla SpawnerBlockEntity also covers in-place enhancers like Apotheosis. An
            // empty/un-set spawner leaves the label null and keeps the generic "Monster Spawner".
            if (block instanceof SpawnerBlock && level instanceof final Level realLevel) {
                for (final BlockPos pos : blocks) {
                    if (realLevel.getBlockEntity(pos) instanceof final SpawnerBlockEntity spawner) {
                        final Entity display = spawner.getSpawner().getOrCreateDisplayEntity(realLevel, pos);
                        if (display != null) {
                            spawnerType = display.getType();
                            label = Component.translatable("gui.scannable.overlay.spawner", display.getType().getDescription());
                        }
                        break;
                    }
                }
            }

            // Lootr loot containers: force the gold highlight + flag them for the live looted-hide.
            // No custom label, so the looking-at name falls back to the block's own native name
            // ("Loot Chest", "Loot Barrel", ...). Detection is tag-only (no Lootr classes), so this is
            // a no-op without Lootr installed.
            if (LootrIntegration.isContainer(blockState)) {
                lootr = true;
                color = LootrIntegration.GOLD;
            }
        }

        boolean isRoot() {
            return parent == null;
        }

        BlockScanResult getRoot() {
            if (parent != null) {
                return parent.getRoot();
            }
            return this;
        }

        void setRoot(final BlockScanResult root) {
            if (root == this) {
                return;
            }

            assert parent == null;

            root.bounds = root.bounds.minmax(bounds);
            root.blocks.addAll(blocks);
            blocks.clear();
            parent = root;
        }

        void add(final BlockPos pos) {
            assert parent == null : "Trying to add to non-root node.";
            bounds = bounds.minmax(new AABB(pos));
            blocks.add(pos);
        }

        // --------------------------------------------------------------------- //
        // ScanResult

        @Nullable
        @Override
        public AABB getRenderBounds() {
            return bounds;
        }

        @Override
        public Vec3 getPosition() {
            return bounds.getCenter();
        }

        @Override
        public void close() {
        }
    }
}
