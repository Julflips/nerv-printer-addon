package com.julflips.nerv_printer.utils;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.network.packet.s2c.play.UnloadChunkS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;

import java.util.HashMap;
import java.util.Map;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public final class MapAreaCache {
    private static BlockPos mapCorner = null;
    private static Map<ChunkPos, Chunk> cachedChunks = new HashMap<>();

    public static boolean isWithingMap(BlockPos pos) {
        BlockPos relativePos = pos.subtract(mapCorner);
        return relativePos.getX() >= 0 && relativePos.getX() < 128 && relativePos.getZ() >= 0 && relativePos.getZ() < 128;
    }

    public static boolean isMapAreaClear() {
        for (int x = 0; x < 128; x++) {
            for (int z = 0; z < 128; z++) {
                BlockState blockState = mc.world.getBlockState(mapCorner.add(x, 0, z));
                if (!blockState.isAir() || !blockState.getFluidState().isEmpty()) return false;
            }
        }
        return true;
    }

    public static void reset(BlockPos newCorner) {
        mapCorner = new BlockPos(newCorner);
        cachedChunks.clear();
    }

    public static BlockState getCachedBlockState(BlockPos blockPos) {
        int chunkX = blockPos.getX() >> 4;
        int chunkZ = blockPos.getZ() >> 4;
        if (mc.world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) {
            return mc.world.getBlockState(blockPos);
        }
        ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
        if (cachedChunks.containsKey(chunkPos)) {
            Chunk chunk = cachedChunks.get(chunkPos);
            return chunk.getBlockState(blockPos);
        }
        ChatUtils.warning("Could not fetch Block at " + blockPos.toShortString() + ". Try loading the entire Map Area first.");
        return mc.world.getBlockState(blockPos);
    }

    @EventHandler()
    private static void onReceivePacket(PacketEvent.Receive event) {
        if (mapCorner != null && event.packet instanceof UnloadChunkS2CPacket packet) {
            BlockPos chunkCorner = packet.pos().getStartPos();
            if (isWithingMap(chunkCorner)) {
                cachedChunks.put(packet.pos(), mc.world.getChunk(packet.pos().getStartPos()));
            }
        }
    }
}
