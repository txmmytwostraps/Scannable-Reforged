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
import li.cil.scannable.common.item.ScannerModuleItem;
import li.cil.scannable.common.scanning.filter.IgnoredBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import li.cil.scannable.client.renderer.ScanResultRenderType;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShapeRenderer;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

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

    // --------------------------------------------------------------------- //
    // ScanResultProvider

    @Override
    public void initialize(final Player player, final Collection<ItemStack> modules, final Vec3 center, final float radius, final int scanTicks) {
        super.initialize(player, modules, center, radius, scanTicks);

        scanFilterLayers.clear();

        final IntObjectMap<List<Predicate<BlockState>>> filterByRadius = new IntObjectHashMap<>();
        for (final ItemStack stack : modules) {
            final Optional<ScannerModule> capability = ScannerModuleItem.getModule(stack);
            capability.ifPresent(module -> {
                if (module instanceof BlockScannerModule blockModule) {
                    final Predicate<BlockState> filter = blockModule.getFilter(stack);
                    final int localRadius = (int) Math.ceil(blockModule.adjustLocalRange(this.radius));
                    filterByRadius.computeIfAbsent(localRadius, r -> new ArrayList<>()).add(filter);
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

        // Pass 1: animated shimmer fill (additive scanlines + pulse + edge glow), contouring the
        // actual ore cells. Additive blend uses the colour magnitude, so pass the ore colour at full
        // intensity and let the shader modulate it.
        final VertexConsumer fill = bufferSource.getBuffer(ScanResultRenderType.SHIMMER_TYPE);
        for (final ScanResult result : results) {
            final BlockScanResult br = (BlockScanResult) result;
            final int c = br.color;
            final float r = ((c >> 16) & 0xFF) / 255.0f, g = ((c >> 8) & 0xFF) / 255.0f, b = (c & 0xFF) / 255.0f;
            if (br.blocks.size() <= MAX_CONTOUR_CELLS) {
                addClusterFill(fill, pose, br.blocks, r, g, b, 1.0f);
            } else {
                addBox(fill, pose, br.bounds, r, g, b, 1.0f);
            }
        }

        // Pass 2: bright edges outlining the cluster contour.
        final VertexConsumer edges = bufferSource.getBuffer(ScanResultRenderType.LINES_TYPE);
        for (final ScanResult result : results) {
            final BlockScanResult br = (BlockScanResult) result;
            if (br.shape == null) {
                continue;
            }
            ShapeRenderer.renderShape(poseStack, edges, br.shape, br.bounds.minX, br.bounds.minY, br.bounds.minZ, 0xFF000000 | br.color, 2.0f);
        }
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
            final Vec3 resultPos = result.getPosition();
            final float lookDirDot = (float) lookVec.dot(resultPos.subtract(viewerEyes).normalize());

            final Component label = blockResult.block.getName();
            if (lookDirDot > 0.98f && !label.getString().isEmpty()) {
                final float distance = showDistance ? (float) resultPos.subtract(viewerEyes).length() : 0f;
                renderIconLabel(bufferSource, poseStack, yaw, pitch, lookVec, viewerEyes, distance, resultPos, API.ICON_INFO, label);
            }
        }
    }

    private static void addBox(final VertexConsumer buffer, final PoseStack.Pose pose, final AABB box, final float r, final float g, final float b, final float a) {
        final float x0 = (float) box.minX, y0 = (float) box.minY, z0 = (float) box.minZ;
        final float x1 = (float) box.maxX, y1 = (float) box.maxY, z1 = (float) box.maxZ;
        // Six quad faces (cull is off, so winding is irrelevant). POSITION_TEX_COLOR.
        quad(buffer, pose, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r, g, b, a);
        quad(buffer, pose, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, r, g, b, a);
        quad(buffer, pose, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, b, a);
        quad(buffer, pose, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, r, g, b, a);
        quad(buffer, pose, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, r, g, b, a);
        quad(buffer, pose, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r, g, b, a);
    }

    private static void addClusterFill(final VertexConsumer buffer, final PoseStack.Pose pose, final Set<BlockPos> cells, final float r, final float g, final float b, final float a) {
        for (final BlockPos cell : cells) {
            final float x0 = cell.getX(), y0 = cell.getY(), z0 = cell.getZ();
            final float x1 = x0 + 1.0f, y1 = y0 + 1.0f, z1 = z0 + 1.0f;
            // Only emit faces on the outside of the cluster (neighbour cell not part of it).
            if (!cells.contains(cell.west()))  quad(buffer, pose, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r, g, b, a);
            if (!cells.contains(cell.east()))  quad(buffer, pose, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, r, g, b, a);
            if (!cells.contains(cell.below())) quad(buffer, pose, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, b, a);
            if (!cells.contains(cell.above())) quad(buffer, pose, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, r, g, b, a);
            if (!cells.contains(cell.north())) quad(buffer, pose, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, r, g, b, a);
            if (!cells.contains(cell.south())) quad(buffer, pose, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r, g, b, a);
        }
    }

    private static void quad(final VertexConsumer buffer, final PoseStack.Pose pose,
                             final float ax, final float ay, final float az, final float bx, final float by, final float bz,
                             final float cx, final float cy, final float cz, final float dx, final float dy, final float dz,
                             final float r, final float g, final float b, final float a) {
        // POSITION_TEX_COLOR with a full 0..1 UV per face so the shimmer's edge glow tracks the face border.
        buffer.addVertex(pose, ax, ay, az).setUv(0, 1).setColor(r, g, b, a);
        buffer.addVertex(pose, bx, by, bz).setUv(1, 1).setColor(r, g, b, a);
        buffer.addVertex(pose, cx, cy, cz).setUv(1, 0).setColor(r, g, b, a);
        buffer.addVertex(pose, dx, dy, dz).setUv(0, 0).setColor(r, g, b, a);
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
        @Nullable private VoxelShape shape;

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

            // Build the edge-outline shape relative to the bounds min (keeps VoxelShape coords
            // small). Small clusters contour the real cells; huge ones fall back to the bounding box.
            final int ox = (int) Math.floor(bounds.minX);
            final int oy = (int) Math.floor(bounds.minY);
            final int oz = (int) Math.floor(bounds.minZ);
            if (blocks.size() <= MAX_CONTOUR_CELLS) {
                VoxelShape s = Shapes.empty();
                for (final BlockPos cell : blocks) {
                    s = Shapes.or(s, Shapes.box(
                        cell.getX() - ox, cell.getY() - oy, cell.getZ() - oz,
                        cell.getX() - ox + 1, cell.getY() - oy + 1, cell.getZ() - oz + 1));
                }
                shape = s;
            } else {
                shape = Shapes.create(bounds.move(-ox, -oy, -oz));
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
