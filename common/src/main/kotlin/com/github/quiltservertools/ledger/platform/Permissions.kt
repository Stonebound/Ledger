package com.github.quiltservertools.ledger.platform

import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.level.ServerPlayer
import java.util.function.Predicate

/** Same call shape as fabric-permissions-api, routed through the current loader. */
object Permissions {
    fun require(node: String, level: Int): Predicate<CommandSourceStack> =
        Predicate { LedgerPlatform.instance.hasPermission(it, node, level) }

    fun check(player: ServerPlayer, node: String, level: Int): Boolean =
        LedgerPlatform.instance.hasPermission(player, node, level)
}
