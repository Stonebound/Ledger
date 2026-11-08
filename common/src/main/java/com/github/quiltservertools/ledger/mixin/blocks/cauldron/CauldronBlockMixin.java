package com.github.quiltservertools.ledger.mixin.blocks.cauldron;

import com.github.quiltservertools.ledger.callbacks.BlockChangeCallback;
import com.github.quiltservertools.ledger.utility.Sources;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.CauldronBlock;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CauldronBlock.class)
public abstract class CauldronBlockMixin {

    @WrapMethod(method = "receiveStalactiteDrip")
    private void ledgerLogIncrementLevelCauldron(BlockState state, Level world, BlockPos pos, Fluid fluid, Operation<Void> original) {
        original.call(state, world, pos, fluid);
        BlockState newState = world.getBlockState(pos);
        if (newState != state) {
            BlockChangeCallback.EVENT.invoker().changeBlock(
                    world,
                    pos,
                    state,
                    newState,
                    null,
                    null,
                    Sources.DRIP);
        }
    }

    @Inject(method = "handlePrecipitation", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z", shift = At.Shift.AFTER))
    private void ledgerLogIncrementLevelCauldron(BlockState state, Level world, BlockPos pos, Biome.Precipitation precipitation, CallbackInfo ci) {
        BlockChangeCallback.EVENT.invoker().changeBlock(
                world,
                pos,
                state,
                world.getBlockState(pos),
                null,
                null,
                Sources.SNOW);
    }

}
