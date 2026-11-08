package com.github.quiltservertools.ledger.neoforge.mixin;

import com.github.quiltservertools.ledger.actionutils.LocationalInventory;
import com.github.quiltservertools.ledger.listeners.PlayerEventListenerKt;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Logs player block breaks once they have happened. NeoForge's BreakEvent fires before the break and can be
 * cancelled afterwards by other mods, so it can't stand in for Fabric's "after break" event.
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {
    @Shadow
    protected ServerLevel level;

    @Shadow
    @Final
    protected ServerPlayer player;

    @WrapMethod(method = "destroyBlock")
    private boolean ledger$logBlockBreak(BlockPos pos, Operation<Boolean> original) {
        BlockState state = level.getBlockState(pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        // Modded storage can move its contents into the dropped item as it breaks, so they have to be saved first.
        // Vanilla-style containers log what they drop separately, and would be restored twice.
        String dataBeforeBreak = blockEntity != null && !(blockEntity instanceof LocationalInventory)
                ? blockEntity.saveWithoutMetadata(level.registryAccess()).toString()
                : null;

        boolean destroyed = original.call(pos);

        // destroyBlock can return true without removing the block, so check that it actually changed.
        if (destroyed && level.getBlockState(pos) != state) {
            if (dataBeforeBreak != null) {
                PlayerEventListenerKt.onBlockBreak(level, player, pos, state, dataBeforeBreak);
            } else {
                PlayerEventListenerKt.onBlockBreak(level, player, pos, state, blockEntity);
            }
        }
        return destroyed;
    }
}
