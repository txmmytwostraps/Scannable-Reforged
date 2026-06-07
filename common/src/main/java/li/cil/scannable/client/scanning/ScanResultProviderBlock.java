package li.cil.scannable.client.scanning;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
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
import li.cil.scannable.client.shader.Shaders;
import li.cil.scannable.common.integration.lootr.LootrIntegration;
import li.cil.scannable.common.item.ScannerModuleItem;
import li.cil.scannable.common.scanning.ConfigurableSpawnerScannerModule;
import li.cil.scannable.common.scanning.filter.IgnoredBlocks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

@Environment(EnvType.CLIENT)
public final class ScanResultProviderBlock extends AbstractScanResultProvider {
    private static final Logger LOGGER = LogManager.getLogger();

    // Sanity performance check. Maybe some day I'll do some research on how to
    // do the clustering more efficiently, but for now this is good enough. We
    // really only need this when scanning for stupid stuff like stone.
    private static final int MAX_RESULTS_PER_BLOCK = 8192;
    private static final int DEFAULT_COLOR = 0x4466CC;
    // Above this cell count we don't do the live hide-broken / Lootr-looted world re-check (it would
    // mean a getBlockState per cell on big clusters, e.g. a block module on stone) - those just keep
    // their static highlight.
    private static final int MAX_LIVE_CELLS = 4096;

    private final List<ScanFilterLayer> scanFilterLayers = new ArrayList<>();
    private final List<ChunkSectionPos> pendingChunkSections = new ArrayList<>();
    private int currentChunkSection, chunkSectionsPerTick;
    private final Map<Block, Map<BlockPos, BlockScanResult>> resultClusters = new HashMap<>();
    private final List<BlockScanResult> results = new ArrayList<>();

    private long renderStartTime;

    // Spawner mob narrowing, gathered from configurable spawner modules. matchAll = an unconfigured
    // spawner module is present (highlight every spawner); otherwise only spawners whose mob is in
    // mobFilter pass.
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

                // Spawner mob narrowing: when a mob filter is active, drop spawners that don't spawn a
                // configured mob (including empty spawners). Non-spawner results are unaffected.
                if (result.block instanceof SpawnerBlock && !spawnerMatchAll && !spawnerMobFilter.isEmpty()
                    && (result.spawnerType == null || !spawnerMobFilter.contains(result.spawnerType))) {
                    continue;
                }

                callback.accept(result);
            }
        }

        renderStartTime = System.currentTimeMillis();
    }

    @Override
    public void render(final ScanResultRenderContext context, final MultiBufferSource bufferSource, final PoseStack poseStack, final Camera renderInfo, final float partialTicks, final List<ScanResult> results) {
        switch (context) {
            case WORLD -> renderBlocks(poseStack, renderInfo, partialTicks, results);
            case GUI -> renderBlockIcons(bufferSource, poseStack, renderInfo, results);
        }
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

    public static RenderType getBlockScanResultRenderLayer() {
        return RenderType.create("scan_result",
            DefaultVertexFormat.POSITION_TEX_COLOR,
            VertexFormat.Mode.QUADS,
            65536,
            false,
            false,
            RenderType.CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(Shaders::getScanResultShader))
                .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .setCullState(RenderStateShard.NO_CULL)
                .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                .createCompositeState(false));
    }

    private void renderBlocks(final PoseStack poseStack, final Camera renderInfo, final float partialTicks, final List<ScanResult> results) {
        final ShaderInstance shader = Shaders.getScanResultShader();
        if (shader == null) {
            return;
        }

        // Re-render hands into depth buffer to avoid rendering overlay on top of player hands.
        if (Minecraft.getInstance().gameRenderer.renderHand) {
            RenderSystem.backupProjectionMatrix();
            RenderSystem.colorMask(false, false, false, false);
            poseStack.pushPose();
            try {
                Minecraft.getInstance().gameRenderer.renderItemInHand(renderInfo, partialTicks, poseStack.last().pose());
            } catch (final Throwable e) {
                LOGGER.catching(e);
            }
            poseStack.popPose();
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.restoreProjectionMatrix();
        }

        shader.safeGetUniform("time").set((System.currentTimeMillis() - renderStartTime) / 1000.0f);

        // Live hide-broken-blocks / Lootr-looted update: prune cells that no longer match and rebuild
        // the affected VBOs. Done before setting up the render state since it uses the Tesselator.
        final Level level = Minecraft.getInstance().level;
        final Player viewer = Minecraft.getInstance().player;
        if (level != null) {
            for (final ScanResult result : results) {
                final BlockScanResult blockResult = (BlockScanResult) result;
                if (blockResult.needsLiveRefresh()) {
                    blockResult.refreshVisible(level, viewer);
                }
            }
        }

        final RenderType renderType = getBlockScanResultRenderLayer();
        renderType.setupRenderState();
        for (final ScanResult result : results) {
            final BlockScanResult blockResult = (BlockScanResult) result;
            final VertexBuffer vbo = blockResult.vbo;
            if (vbo == null) {
                continue; // Fully pruned (all cells mined / looted).
            }
            vbo.bind();
            vbo.drawWithShader(poseStack.last().pose(), RenderSystem.getProjectionMatrix(), shader);
            VertexBuffer.unbind();
        }
        renderType.clearRenderState();
    }

    private void renderBlockIcons(final MultiBufferSource bufferSource, final PoseStack poseStack, final Camera renderInfo, final List<ScanResult> results) {
        final Vec3 lookVec = new Vec3(renderInfo.getLookVector());
        final Vec3 viewerEyes = renderInfo.getPosition();
        final float yaw = renderInfo.getYRot();
        final float pitch = renderInfo.getXRot();
        final boolean showDistance = renderInfo.getEntity().isShiftKeyDown();

        // Order by deviation from the look vector (ascending) so the most-centered results are last.
        results.sort(Comparator.comparing(result ->
            lookVec.dot(result.getPosition().subtract(viewerEyes).normalize())));

        // Cap the icons (a dense ore field shouldn't fill the screen) and show a single name: icons for
        // the MAX_ICONS most-centered results within the ~0.98 cone, name on only the most-centered.
        renderIconLabels(bufferSource, poseStack, yaw, pitch, lookVec, viewerEyes, showDistance, results,
            ScanResult::getPosition,
            result -> API.ICON_INFO,
            result -> {
                final BlockScanResult blockResult = (BlockScanResult) result;
                return blockResult.label != null ? blockResult.label : blockResult.block.getName();
            },
            result -> ((BlockScanResult) result).hasVisible(),
            MAX_ICONS, ICON_CONE_DOT);
    }

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
        private VertexBuffer vbo;
        // Cells currently represented by the VBO. Equals `blocks` unless the live hide-broken-blocks
        // (or Lootr looted) check has pruned some; null until baked.
        @Nullable private Set<BlockPos> visibleBlocks;
        private long lastVisibleCheck;
        // Overrides the generic block name in the looking-at label (e.g. "Zombie Spawner").
        @Nullable private Component label;
        // The mob this result's spawner spawns (null otherwise); drives the spawner mob filter.
        @Nullable private EntityType<?> spawnerType;
        // This result is a Lootr loot container: gold highlight + live looted-hide.
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

            // Lootr loot containers: force the gold highlight + flag them for the live looted-hide.
            // Tag-only detection, so this is a no-op on packs without Lootr.
            if (LootrIntegration.isContainer(blockState)) {
                lootr = true;
                color = LootrIntegration.GOLD;
            }

            // Spawners: label with the mob they spawn ("Zombie Spawner") instead of the generic block
            // name, and record the type for the spawner mob filter. An empty/un-set spawner leaves the
            // label null (keeps the generic "Monster Spawner").
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

            visibleBlocks = blocks;
            buildVbo();
        }

        private void buildVbo() {
            if (visibleBlocks == null || visibleBlocks.isEmpty()) {
                return;
            }
            final BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            render(buffer, new PoseStack());
            if (vbo == null) {
                vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
            }
            vbo.bind();
            vbo.upload(buffer.buildOrThrow());
            VertexBuffer.unbind();
        }

        // Recompute which cells are still present (hide-broken-blocks) and, for Lootr containers, not
        // yet looted by this player; rebuild the VBO if that set changed. Throttled to ~10x/s and
        // skipped for very large clusters to bound per-cell world lookups. A fully-pruned cluster
        // drops its VBO (and is skipped when rendering).
        void refreshVisible(final Level level, @Nullable final Player viewer) {
            if (blocks.size() > MAX_LIVE_CELLS) {
                return;
            }
            final long now = System.currentTimeMillis();
            if (now - lastVisibleCheck < 100L) {
                return;
            }
            lastVisibleCheck = now;

            final Set<BlockPos> present = new HashSet<>();
            for (final BlockPos cell : blocks) {
                if (cellPresent(level, viewer, cell)) {
                    present.add(cell);
                }
            }
            if (present.equals(visibleBlocks)) {
                return;
            }
            visibleBlocks = present;
            if (present.isEmpty()) {
                if (vbo != null) {
                    vbo.close();
                    vbo = null;
                }
            } else {
                buildVbo();
            }
        }

        private boolean cellPresent(final Level level, @Nullable final Player viewer, final BlockPos cell) {
            if (!level.hasChunkAt(cell)) {
                return true; // Unloaded -> unknown, assume present.
            }
            if (lootr) {
                return LootrIntegration.isContainer(level.getBlockState(cell))
                    && !LootrIntegration.isClientLooted(level, cell, viewer);
            }
            return level.getBlockState(cell).is(block);
        }

        boolean needsLiveRefresh() {
            return lootr || ClientConfig.hideBrokenBlocks;
        }

        boolean hasVisible() {
            return visibleBlocks == null || !visibleBlocks.isEmpty();
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

        void render(final VertexConsumer buffer, final PoseStack poseStack) {
            final var matrix = poseStack.last().pose();

            final float colorNormalizer = 1 / 255f;
            final float r = ((color >> 16) & 0xFF) * colorNormalizer;
            final float g = ((color >> 8) & 0xFF) * colorNormalizer;
            final float b = (color & 0xFF) * colorNormalizer;

            final float sizeUvX = (float) (1.0 / bounds.getXsize());
            final float sizeUvY = (float) (1.0 / bounds.getYsize());
            final float sizeUvZ = (float) (1.0 / bounds.getZsize());
            final Set<BlockPos> cells = visibleBlocks != null ? visibleBlocks : blocks;
            for (final BlockPos cell : cells) {
                if (!cells.contains(cell.offset(-1, 0, 0))) {
                    final float x = cell.getX();
                    final float minY = cell.getY();
                    final float maxY = cell.getY() + 1;
                    final float minZ = cell.getZ();
                    final float maxZ = cell.getZ() + 1;
                    final float u0 = (minY - (float) bounds.minY) * sizeUvY;
                    final float u1 = u0 + sizeUvY;
                    final float v0 = (minZ - (float) bounds.minZ) * sizeUvZ;
                    final float v1 = v0 + sizeUvZ;
                    buffer.addVertex(matrix, x, minY, minZ).setUv(u0, v0).setColor(r, g, b, 0.8f);
                    buffer.addVertex(matrix, x, minY, maxZ).setUv(u0, v1).setColor(r, g, b, 0.8f);
                    buffer.addVertex(matrix, x, maxY, maxZ).setUv(u1, v1).setColor(r, g, b, 0.8f);
                    buffer.addVertex(matrix, x, maxY, minZ).setUv(u1, v0).setColor(r, g, b, 0.8f);
                }
                if (!cells.contains(cell.offset(1, 0, 0))) {
                    final float x = cell.getX() + 1;
                    final float minY = cell.getY();
                    final float maxY = cell.getY() + 1;
                    final float minZ = cell.getZ();
                    final float maxZ = cell.getZ() + 1;
                    final float u0 = (minY - (float) bounds.minY) * sizeUvY;
                    final float u1 = u0 + sizeUvY;
                    final float v0 = (minZ - (float) bounds.minZ) * sizeUvZ;
                    final float v1 = v0 + sizeUvZ;
                    buffer.addVertex(matrix, x, minY, minZ).setUv(u0, v0).setColor(r, g, b, 0.8f);
                    buffer.addVertex(matrix, x, maxY, minZ).setUv(u1, v0).setColor(r, g, b, 0.8f);
                    buffer.addVertex(matrix, x, maxY, maxZ).setUv(u1, v1).setColor(r, g, b, 0.8f);
                    buffer.addVertex(matrix, x, minY, maxZ).setUv(u0, v1).setColor(r, g, b, 0.8f);
                }
                if (!cells.contains(cell.offset(0, -1, 0))) {
                    final float y = cell.getY();
                    final float minX = cell.getX();
                    final float maxX = cell.getX() + 1;
                    final float minZ = cell.getZ();
                    final float maxZ = cell.getZ() + 1;
                    final float u0 = (minX - (float) bounds.minX) * sizeUvX;
                    final float u1 = u0 + sizeUvX;
                    final float v0 = (minZ - (float) bounds.minZ) * sizeUvZ;
                    final float v1 = v0 + sizeUvZ;
                    buffer.addVertex(matrix, minX, y, minZ).setUv(u0, v0).setColor(r, g, b, 0.7f);
                    buffer.addVertex(matrix, maxX, y, minZ).setUv(u1, v0).setColor(r, g, b, 0.7f);
                    buffer.addVertex(matrix, maxX, y, maxZ).setUv(u1, v1).setColor(r, g, b, 0.7f);
                    buffer.addVertex(matrix, minX, y, maxZ).setUv(u0, v1).setColor(r, g, b, 0.7f);
                }
                if (!cells.contains(cell.offset(0, 1, 0))) {
                    final float y = cell.getY() + 1;
                    final float minX = cell.getX();
                    final float maxX = cell.getX() + 1;
                    final float minZ = cell.getZ();
                    final float maxZ = cell.getZ() + 1;
                    final float u0 = (minX - (float) bounds.minX) * sizeUvX;
                    final float u1 = u0 + sizeUvX;
                    final float v0 = (minZ - (float) bounds.minZ) * sizeUvZ;
                    final float v1 = v0 + sizeUvZ;
                    buffer.addVertex(matrix, minX, y, minZ).setUv(u0, v0).setColor(r, g, b, 1.0f);
                    buffer.addVertex(matrix, minX, y, maxZ).setUv(u0, v1).setColor(r, g, b, 1.0f);
                    buffer.addVertex(matrix, maxX, y, maxZ).setUv(u1, v1).setColor(r, g, b, 1.0f);
                    buffer.addVertex(matrix, maxX, y, minZ).setUv(u1, v0).setColor(r, g, b, 1.0f);
                }
                if (!cells.contains(cell.offset(0, 0, -1))) {
                    final float z = cell.getZ();
                    final float minX = cell.getX();
                    final float maxX = cell.getX() + 1;
                    final float minY = cell.getY();
                    final float maxY = cell.getY() + 1;
                    final float u0 = (minX - (float) bounds.minX) * sizeUvX;
                    final float u1 = u0 + sizeUvX;
                    final float v0 = (minY - (float) bounds.minY) * sizeUvY;
                    final float v1 = v0 + sizeUvY;
                    buffer.addVertex(matrix, minX, minY, z).setUv(u0, v0).setColor(r, g, b, 0.9f);
                    buffer.addVertex(matrix, minX, maxY, z).setUv(u0, v1).setColor(r, g, b, 0.9f);
                    buffer.addVertex(matrix, maxX, maxY, z).setUv(u1, v1).setColor(r, g, b, 0.9f);
                    buffer.addVertex(matrix, maxX, minY, z).setUv(u1, v0).setColor(r, g, b, 0.9f);
                }
                if (!cells.contains(cell.offset(0, 0, 1))) {
                    final float z = cell.getZ() + 1;
                    final float minX = cell.getX();
                    final float maxX = cell.getX() + 1;
                    final float minY = cell.getY();
                    final float maxY = cell.getY() + 1;
                    final float u0 = (minX - (float) bounds.minX) * sizeUvX;
                    final float u1 = u0 + sizeUvX;
                    final float v0 = (minY - (float) bounds.minY) * sizeUvY;
                    final float v1 = v0 + sizeUvY;
                    buffer.addVertex(matrix, minX, minY, z).setUv(u0, v0).setColor(r, g, b, 0.9f);
                    buffer.addVertex(matrix, maxX, minY, z).setUv(u1, v0).setColor(r, g, b, 0.9f);
                    buffer.addVertex(matrix, maxX, maxY, z).setUv(u1, v1).setColor(r, g, b, 0.9f);
                    buffer.addVertex(matrix, minX, maxY, z).setUv(u0, v1).setColor(r, g, b, 0.9f);
                }
            }
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
            if (vbo != null) {
                vbo.close();
                vbo = null;
            }
        }
    }
}
