package com.github.quiltservertools.ledger.mixin.blocks;

import com.github.quiltservertools.ledger.callbacks.BlockBreakCallback;
import com.github.quiltservertools.ledger.utility.Sources;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FireBlock.class)
public abstract class FireBlockMixin {
    @Inject(
            method = "checkBurnOut",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"
            )
    )
    private void ledgerBlockBurnBreakInvoker(CallbackInfo ci, @Local(argsOnly = true) Level world, @Local(argsOnly = true) BlockPos pos, @Local BlockState blockState) {
        if (blockState.getBlock() != Blocks.FIRE) {
            BlockBreakCallback.EVENT.invoker().breakBlock(world, pos, blockState, world.getBlockEntity(pos) != null ? world.getBlockEntity(pos) : null, Sources.FIRE);
        }
    }

    @Inject(
            method = "checkBurnOut",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"
            )
    )
    private void ledgerBlockBurnReplaceInvoker(CallbackInfo ci, @Local(argsOnly = true) Level world, @Local(argsOnly = true) BlockPos pos, @Local BlockState blockState) {
        if (blockState.getBlock() != Blocks.FIRE) {
            BlockBreakCallback.EVENT.invoker().breakBlock(world, pos, blockState, world.getBlockEntity(pos) != null ? world.getBlockEntity(pos) : null, Sources.FIRE);
        }
    }
}
