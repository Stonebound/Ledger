package com.github.quiltservertools.ledger.neoforge

import com.github.quiltservertools.ledger.Ledger
import com.github.quiltservertools.ledger.commands.CommandConsts
import com.github.quiltservertools.ledger.config.SearchSpec
import net.minecraft.server.permissions.Permission
import net.minecraft.server.permissions.PermissionLevel
import net.minecraft.server.permissions.PermissionSet
import net.neoforged.neoforge.server.permission.nodes.PermissionNode
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes

/** NeoForge requires permission nodes to be declared up front, unlike fabric-permissions-api. */
object NeoForgePermissions {
    val nodes: Map<String, PermissionNode<Boolean>> = buildMap {
        listOf("root", "inspect", "player", "preview", "rollback", "search", "status", "tp").forEach { name ->
            put("ledger.commands.$name", node(name) { CommandConsts.PERMISSION_LEVEL })
        }
        put("ledger.commands.purge", node("purge") { Ledger.config[SearchSpec.purgePermissionLevel] })
    }

    fun hasLevel(permissions: PermissionSet, level: Int) =
        permissions.hasPermission(Permission.HasCommandLevel(PermissionLevel.byId(level)))

    private fun node(name: String, level: () -> Int) = PermissionNode(
        Ledger.MOD_ID,
        "commands.$name",
        PermissionTypes.BOOLEAN,
        PermissionNode.PermissionResolver { player, _, _ -> player != null && hasLevel(player.permissions(), level()) },
    )
}
