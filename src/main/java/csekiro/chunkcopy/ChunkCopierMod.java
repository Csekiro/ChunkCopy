package csekiro.chunkcopy;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.LightUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.UnloadChunkS2CPacket;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;
import com.mojang.brigadier.suggestion.SuggestionProvider;

/**
 * 复制 / 粘贴 / 重载区块  (Fabric Yarn 1.20.6)
 * /chunks copy  fx fz tx tz
 * /chunks paste dx dz   ——> 粘贴后自动刷新玩家视野
 * /chunks reload
 */
public class ChunkCopierMod implements ModInitializer {

    private static final Map<ChunkPos, ChunkSection[]> BUFFER = new HashMap<>();

    @Override public void onInitialize() {
        CommandRegistrationCallback.EVENT.register(this::register);
    }

    /* ─────────────── 命令注册 ─────────────── */
    private void register(CommandDispatcher<ServerCommandSource> d,
                          net.minecraft.command.CommandRegistryAccess a,
                          CommandManager.RegistrationEnvironment e) {

        /* ========== /chunks <sub> ========== */
        var root = CommandManager.literal("chunks");

        /* --- chunks copy --- */
        root.then(CommandManager.literal("copy")
                .then(CommandManager.argument("fx", IntegerArgumentType.integer())
                        .suggests(SUGGEST_CHUNK_X)
                        .then(CommandManager.argument("fz", IntegerArgumentType.integer())
                                .suggests(SUGGEST_CHUNK_Z)
                                .then(CommandManager.argument("tx", IntegerArgumentType.integer())
                                        .suggests(SUGGEST_CHUNK_X)
                                        .then(CommandManager.argument("tz", IntegerArgumentType.integer())
                                                .suggests(SUGGEST_CHUNK_Z)
                                                .executes(ctx -> {
                                                    int fx = IntegerArgumentType.getInteger(ctx, "fx");
                                                    int fz = IntegerArgumentType.getInteger(ctx, "fz");
                                                    int tx = IntegerArgumentType.getInteger(ctx, "tx");
                                                    int tz = IntegerArgumentType.getInteger(ctx, "tz");

                                                    // 异步执行复制操作
                                                    ctx.getSource().getWorld().getServer().execute(() -> {
                                                        copyWithForceLoading(ctx.getSource().getWorld(), fx, fz, tx, tz, ctx.getSource());
                                                    });

                                                    ctx.getSource().sendFeedback(
                                                            () -> Text.literal("§e开始复制区块，正在强制加载..."), false);
                                                    return 1;
                                                }))))));

        /* --- chunks paste --- */
        root.then(CommandManager.literal("paste")
                .then(CommandManager.argument("dx", IntegerArgumentType.integer())
                        .suggests(SUGGEST_CHUNK_X)
                        .then(CommandManager.argument("dz", IntegerArgumentType.integer())
                                .suggests(SUGGEST_CHUNK_Z)
                                .executes(ctx -> {
                                    int dx = IntegerArgumentType.getInteger(ctx, "dx");
                                    int dz = IntegerArgumentType.getInteger(ctx, "dz");
                                    paste(ctx.getSource().getWorld(), dx, dz);
                                    ctx.getSource().sendFeedback(
                                            () -> Text.literal("§bPasted " + BUFFER.size() + " chunks"), false);
                                    return BUFFER.size();
                                }))));

        /* --- chunks reload --- */
        root.then(CommandManager.literal("reload")
                .requires(src -> src.hasPermissionLevel(2))
                .executes(ctx -> {
                    ServerCommandSource source = ctx.getSource();
                    ServerPlayerEntity player = null;

                    // 检查命令源是否为玩家
                    if (source.getEntity() instanceof ServerPlayerEntity) {
                        player = (ServerPlayerEntity) source.getEntity();
                    } else {
                        // 如果不是玩家执行（比如命令方块），尝试获取最近的玩家或使用其他逻辑
                        // 这里需要根据你的具体需求来处理
                        source.sendMessage(Text.literal("§c此命令只能由玩家执行。"));
                        return 0;
                    }

                    reloadVisibleChunks(player);
                    player.sendMessage(Text.literal("§a已重新加载你可见的所有区块。"), false);
                    return 1;
                }));

        /* 注册根节点 */
        d.register(root);
    }

    /* ─────────────── 带强制加载的复制 ─────────────── */
    private static void copyWithForceLoading(ServerWorld world, int x1, int z1, int x2, int z2, ServerCommandSource source) {
        BUFFER.clear();

        int minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
        int minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);

        // 收集所有需要强制加载的区块坐标
        List<ChunkPos> chunksToForceLoad = new ArrayList<>();
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                chunksToForceLoad.add(new ChunkPos(cx, cz));
            }
        }

        source.sendFeedback(() -> Text.literal("§e正在强制加载 " + chunksToForceLoad.size() + " 个区块..."), false);

        // 强制加载所有区块
        for (ChunkPos pos : chunksToForceLoad) {
            world.setChunkForced(pos.x, pos.z, true);
        }

        // 等待所有区块完全加载
        world.getServer().execute(() -> {
            // 确保所有区块都已加载
            boolean allLoaded = false;
            int attempts = 0;
            final int maxAttempts = 100; // 最大尝试次数，防止无限等待

            while (!allLoaded && attempts < maxAttempts) {
                allLoaded = true;
                for (ChunkPos pos : chunksToForceLoad) {
                    if (!world.isChunkLoaded(pos.x, pos.z)) {
                        allLoaded = false;
                        break;
                    }
                }

                if (!allLoaded) {
                    attempts++;
                    try {
                        Thread.sleep(50); // 等待50ms
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            if (!allLoaded) {
                source.sendFeedback(() -> Text.literal("§c警告：部分区块可能未完全加载，继续复制..."), false);
            }

            // 执行实际复制
            source.sendFeedback(() -> Text.literal("§e区块加载完成，开始复制..."), false);

            try {
                copy(world, minX, minZ, maxX, maxZ);
                source.sendFeedback(() -> Text.literal("§a成功复制 " + BUFFER.size() + " 个区块"), false);
            } catch (Exception e) {
                source.sendFeedback(() -> Text.literal("§c复制过程中发生错误: " + e.getMessage()), false);
            } finally {
                // 无论复制是否成功，都要移除强制加载
                for (ChunkPos pos : chunksToForceLoad) {
                    world.setChunkForced(pos.x, pos.z, false);
                }
                source.sendFeedback(() -> Text.literal("§e已移除区块强制加载状态"), false);
            }
        });
    }

    /* ─────────────── 复制 ─────────────── */
    private static void copy(ServerWorld world, int x1, int z1, int x2, int z2) {
        BUFFER.clear();
        int minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
        int minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                WorldChunk src = world.getChunk(cx, cz);
                ChunkSection[] srcSections = src.getSectionArray();
                ChunkSection[] copiedSections = new ChunkSection[srcSections.length];

                // 深拷贝每个ChunkSection
                for (int i = 0; i < srcSections.length; i++) {
                    if (srcSections[i] != null) {
                        copiedSections[i] = deepCopyChunkSection(srcSections[i]);
                    } else {
                        copiedSections[i] = null;
                    }
                }

                BUFFER.put(new ChunkPos(cx - minX, cz - minZ), copiedSections);
            }
        }
    }

    /* ─────────────── 深拷贝ChunkSection ─────────────── */
    private static ChunkSection deepCopyChunkSection(ChunkSection original) {
        try {
            // 获取原始容器
            var originalBlockContainer = original.getBlockStateContainer();
            var originalBiomeContainer = original.getBiomeContainer();

            // 复制方块容器
            var newBlockContainer = originalBlockContainer.copy();

            // 创建新的ChunkSection，重用原始的生物群系容器（不复制）
            ChunkSection copy = new ChunkSection(newBlockContainer, originalBiomeContainer);

            // 重新计算统计信息
            copy.calculateCounts();
            return copy;

        } catch (Exception e) {
            System.err.println("Could not deep copy ChunkSection: " + e.getMessage());
            // 备用方案：返回原始section
            return original;
        }
    }

    /* ─────────────── 粘贴 + 光照 + 强刷  ─────────────── */
    private static void paste(ServerWorld world, int destX, int destZ) {
        // 确保操作在服务器主线程上执行，防止并发修改问题
        world.getServer().executeSync(() -> {
            ServerChunkManager chunkManager = world.getChunkManager();
            var lightingProvider = chunkManager.getLightingProvider();

            Set<ChunkPos> modifiedChunks = new HashSet<>();

            // Phase 1: 放置方块数据并标记区块进行更新
            for (var entry : BUFFER.entrySet()) {
                ChunkPos offset = entry.getKey();
                int cx = destX + offset.x;
                int cz = destZ + offset.z;
                ChunkPos pos = new ChunkPos(cx, cz);
                WorldChunk destChunk = world.getChunk(cx, cz);

                // 清理旧的方块实体，防止数据冲突或物品复制
                destChunk.getBlockEntities().clear();
                destChunk.getBlockEntityPositions().clear();

                // 使用 System.arraycopy 高效地将区块数据复制到目标区块
                System.arraycopy(entry.getValue(), 0, destChunk.getSectionArray(), 0, entry.getValue().length);

                // 标记区块需要保存
                destChunk.setNeedsSaving(true);

                // 标记所有区块段的光照数据为“无效”。
                // 这是告诉光照引擎这些区域需要重新计算的正确方式。
                int bottomSection = world.getBottomSectionCoord();
                int topSection = world.getTopSectionCoord();
                for (int sy = bottomSection; sy < topSection; sy++) {
                    lightingProvider.setSectionStatus(ChunkSectionPos.from(cx, sy, cz), false);
                }

                modifiedChunks.add(pos);
            }

            // Phase 2: 强制客户端刷新 & 触发光照计算 (非阻塞)
            // 这个循环必须在所有区块数据都放置完毕后执行。
            for (ChunkPos pos : modifiedChunks) {
                WorldChunk chunk = world.getChunk(pos.x, pos.z);

                // 【关键修复】使用正确的方法触发光照更新。
                // 此方法会检查指定区块的光照状态，并在必要时安排更新，然后将光照数据包发送给客户端。
                // 这是一个非阻塞调用，不会冻结服务器。
                lightingProvider.checkBlock(pos.getStartPos());

                // 获取观察该区块的玩家列表
                var viewers = chunkManager.threadedAnvilChunkStorage.getPlayersWatchingChunk(pos);

                // 通过先卸载再发送新数据的方式，强制客户端刷新区块，确保看到最新的方块
                UnloadChunkS2CPacket unloadPacket = new UnloadChunkS2CPacket(pos);
                ChunkDataS2CPacket chunkDataPacket = new ChunkDataS2CPacket(chunk, lightingProvider, null, null);

                for (ServerPlayerEntity player : viewers) {
                    player.networkHandler.sendPacket(unloadPacket);
                    player.networkHandler.sendPacket(chunkDataPacket);
                }
            }
            // 移除了所有阻塞性代码和手动发送光照包的逻辑。
            // `updateChunkStatus` 会处理好后续的光照计算触发和数据包发送。
        });
    }

    /* ─────────────── 重新加载所有可见区块 ─────────────── */
    private static void reloadVisibleChunks(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        int viewDist = world.getServer().getPlayerManager().getSimulationDistance();
        ChunkPos center = new ChunkPos(player.getBlockPos());

        for (int dx = -viewDist; dx <= viewDist; dx++) {
            for (int dz = -viewDist; dz <= viewDist; dz++) {
                ChunkPos pos = new ChunkPos(center.x + dx, center.z + dz);
                WorldChunk chunk = world.getChunkManager().getWorldChunk(pos.x, pos.z, false);
                if (chunk == null) continue;

                player.networkHandler.sendPacket(new UnloadChunkS2CPacket(pos));
                player.networkHandler.sendPacket(new ChunkDataS2CPacket(chunk,
                        world.getLightingProvider(), null, null));
                player.networkHandler.sendPacket(new LightUpdateS2CPacket(pos,
                        world.getLightingProvider(), null, null));
            }
        }
    }
    // x 坐标补全
    private static final SuggestionProvider<ServerCommandSource> SUGGEST_CHUNK_X =
            (ctx, builder) -> {
                int cx = ctx.getSource()
                        .getPlayerOrThrow()          // 只有玩家才能触发补全
                        .getChunkPos().x;            // 当前区块 x
                builder.suggest(cx);                    // 直接给 int
                return builder.buildFuture();
            };

    // z 坐标补全
    private static final SuggestionProvider<ServerCommandSource> SUGGEST_CHUNK_Z =
            (ctx, builder) -> {
                int cz = ctx.getSource()
                        .getPlayerOrThrow()
                        .getChunkPos().z;
                builder.suggest(cz);
                return builder.buildFuture();
            };
}