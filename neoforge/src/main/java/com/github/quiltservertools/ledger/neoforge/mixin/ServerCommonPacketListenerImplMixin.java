package com.github.quiltservertools.ledger.neoforge.mixin;

import com.github.quiltservertools.ledger.neoforge.ServerTranslations;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Resolves Ledger's translatable text in chat messages for the receiving player's language. */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPacketListenerImplMixin {
    @ModifyVariable(
            method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    private Packet<?> ledger$translateChat(Packet<?> packet) {
        if (packet instanceof ClientboundSystemChatPacket chat
                && (Object) this instanceof ServerGamePacketListenerImpl game) {
            String language = game.player.clientInformation().language();
            Component translated = ServerTranslations.translate(chat.content(), language);
            if (translated != chat.content()) {
                return new ClientboundSystemChatPacket(translated, chat.overlay());
            }
        }
        return packet;
    }
}
