package com.github.quiltservertools.ledger.neoforge.mixin;

import com.github.quiltservertools.ledger.neoforge.storage.StorageTracker;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Every packet from a player is handled here on the server thread, so item changes can be tied to the packet. */
@Mixin(targets = "net.minecraft.network.PacketProcessor$ListenerAndPacket")
public abstract class PacketProcessorMixin {
    @WrapOperation(
            method = "handle",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/Packet;handle(Lnet/minecraft/network/PacketListener;)V")
    )
    private void ledgerTrackStorage(Packet<?> packet, PacketListener listener, Operation<Void> original) {
        StorageTracker.around(packet, listener, () -> original.call(packet, listener));
    }
}
