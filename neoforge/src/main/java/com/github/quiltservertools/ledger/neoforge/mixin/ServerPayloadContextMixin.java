package com.github.quiltservertools.ledger.neoforge.mixin;

import com.github.quiltservertools.ledger.neoforge.storage.StorageTracker;
import net.minecraft.network.protocol.common.ServerCommonPacketListener;
import net.neoforged.neoforge.network.handling.ServerPayloadContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Terminal clicks and other modded packets often do their work later, through this method. */
@Mixin(ServerPayloadContext.class)
public abstract class ServerPayloadContextMixin {
    @Shadow
    @Final
    private ServerCommonPacketListener listener;

    @ModifyVariable(method = "enqueueWork(Ljava/lang/Runnable;)Ljava/util/concurrent/CompletableFuture;", at = @At("HEAD"), argsOnly = true)
    private Runnable ledgerTrackStorage(Runnable task) {
        return StorageTracker.aroundPayload(this.listener, task);
    }
}
