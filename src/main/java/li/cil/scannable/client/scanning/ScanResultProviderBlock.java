package li.cil.scannable.client.scanning;

import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import li.cil.scannable.api.prefab.AbstractScanResultProvider;
import li.cil.scannable.api.scanning.BlockScannerModule;
import li.cil.scannable.api.scanning.ScanResult;
import li.cil.scannable.api.scanning.ScanResultRenderContext;
import li.cil.scannable.api.scanning.ScannerModule;
import li.cil.scannable.client.ClientConfig;
import li.cil.scannable.common.item.ScannerModuleItem;
import li.cil.scannable.common.scanning.filter.IgnoredBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
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

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

@OnlyIn(Dist.CLIENT)
public final class ScanResultProviderBlock extends AbstractScanResultProvider {
    // Sanity performance check. Maybe some day I'll do some research on how to
    // do the clustering more efficiently, but for now this is good enough. We
    // really only need this when scanning for stupid stuff like stone.
    private static final int MAX_RESULTS_PER_BLOCK = 8192;
    private static final int DEFAULT_COLOR = 0x4466CC;

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
            final ChunkPos minChunkPos = new ChunkPos(minBlockPos);
            final ChunkPos maxChunkPos = new ChunkPos(maxBlockPos);

            final int minChunkSectionIndex = Math.max(player.level().getSectionIndex(minBlockPos.getY()), 0);
            final int maxChunkSectionIndex = Math.min(player.level().getSectionIndex(maxBlockPos.getY()), player.level().getSectionsCount() - 1);

            for (int chunkSectionIndex = minChunkSectionIndex; chunkSectionIndex <= maxChunkSectionIndex; chunkSectionIndex++) {
                for (int chunkZ = minChunkPos.z; chunkZ <= maxChunkPos.z; chunkZ++) {
                    for (int chunkX = minChunkPos.x; chunkX <= maxChunkPos.x; chunkX++) {
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
        // TODO(Phase 3b): rebuild result-box (WORLD) + icon-label (GUI) rendering. The old path used
        // a custom RenderType + per-cluster VertexBuffer (VBO) drawn with a ShaderInstance, all
        // removed in 1.21.5/1.21.6. Stubbed to a no-op for the launchable build.
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

            // TODO(Phase 3b): bake the cluster geometry into a render buffer. The 1.21.1 path built a
            // VertexBuffer here; VertexBuffer / Tesselator immediate mode were removed in 1.21.6.
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
