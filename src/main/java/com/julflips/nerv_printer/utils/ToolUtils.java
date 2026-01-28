package com.julflips.nerv_printer.utils;

import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ShearsItem;
import net.minecraft.registry.tag.ItemTags;

import java.util.Set;

public final class ToolUtils {

    public static ItemStack getBestTool(Set<ItemStack> tools, BlockState targetBlock) {
        // 1 is the default mining multiplier
        float bestScore = 1;
        ItemStack bestStack = null;
        for (ItemStack tool : tools) {
            if (tool.getMiningSpeedMultiplier(targetBlock) > bestScore) {
                bestScore = tool.getMiningSpeedMultiplier(targetBlock);
                bestStack = tool;
            }
        }
        // Default to Pickaxe if no tool increases the mining speed
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
