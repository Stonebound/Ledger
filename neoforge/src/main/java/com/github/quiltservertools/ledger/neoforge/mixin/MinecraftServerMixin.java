package com.github.quiltservertools.ledger.neoforge.mixin;

import com.github.quiltservertools.ledger.neoforge.ServerTranslations;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Resolves Ledger's translatable text for messages sent to the server console. */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @ModifyVariable(method = "sendSystemMessage", at = @At("HEAD"), argsOnly = true)
    private Component ledger$translateConsoleMessage(Component message) {
        return ServerTranslations.translate(message, "en_us");
    }
}
