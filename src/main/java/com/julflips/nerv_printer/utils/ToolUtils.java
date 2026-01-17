package com.julflips.nerv_printer.utils;

import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ShearsItem;
import net.minecraft.registry.tag.ItemTags;

import java.util.Set;

public final class ToolUtils {

    public static ItemStack getBestTool(Set<ItemStack> tools, BlockState targetBlock) {
        float bestScore = -Float.MAX_VALUE;
        ItemStack bestStack = null;
        for (ItemStack tool : tools) {
            if (!tool.isSuitableFor(targetBlock)) continue;
            if (tool.getMiningSpeedMultiplier(targetBlock) > bestScore) {
                bestScore = tool.getMiningSpeedMultiplier(targetBlock);
                bestStack = tool;
            }
        }
        if (bestStack == null) {
            for (ItemStack tool : tools) {
                if (tool.isIn(ItemTags.PICKAXES)) {
                    return tool;
                }
            }
        }
        return bestStack;
    }

    public static boolean isTool(ItemStack itemStack) {
        if (itemStack.isIn(ItemTags.PICKAXES)
            || itemStack.isIn(ItemTags.AXES)
            || itemStack.isIn(ItemTags.SHOVELS)
            || itemStack.isIn(ItemTags.HOES)
            || itemStack.getItem() instanceof ShearsItem) {
            return true;
        }
        return false;
    }
}
