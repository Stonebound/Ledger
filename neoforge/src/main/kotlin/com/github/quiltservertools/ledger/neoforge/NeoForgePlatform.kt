package com.github.quiltservertools.ledger.neoforge

import com.github.quiltservertools.ledger.Ledger
import com.github.quiltservertools.ledger.actionutils.SearchResults
import com.github.quiltservertools.ledger.listeners.onBlockAttack
import com.github.quiltservertools.ledger.listeners.onJoin
import com.github.quiltservertools.ledger.listeners.onUseBlock
import com.github.quiltservertools.ledger.listeners.onWorldLoad
import com.github.quiltservertools.ledger.logWarn
import com.github.quiltservertools.ledger.platform.LedgerPlatform
import net.minecraft.commands.CommandSourceStack
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.neoforged.fml.ModList
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import net.neoforged.neoforge.event.level.LevelEvent
import net.neoforged.neoforge.server.permission.PermissionAPI
import java.nio.file.Path

class NeoForgePlatform : LedgerPlatform {
    override val configDir: Path
        get() = FMLPaths.CONFIGDIR.get()

    override val modVersion: String
        get() = ModList.get().getModContainerById(Ledger.MOD_ID).get().modInfo.version.toString()

    override fun hasPermission(source: CommandSourceStack, node: String, level: Int): Boolean {
        val player = source.player ?: return NeoForgePermissions.hasLevel(source.permissions(), level)
        return hasPermission(player, node, level)
    }

    override fun hasPermission(player: ServerPlayer, node: String, level: Int): Boolean {
        val permissionNode = NeoForgePermissions.nodes[node]
        if (permissionNode == null) {
            logWarn("Undeclared permission node $node, falling back to level $level")
            return NeoForgePermissions.hasLevel(player.permissions(), level)
        }
        return PermissionAPI.getPermission(player, permissionNode)
    }

    override fun onServerStarting() {
        val bus = NeoForge.EVENT_BUS
        bus.addListener(PlayerEvent.PlayerLoggedInEvent::class.java) { event ->
            (event.entity as? ServerPlayer)?.let(::onJoin)
        }
        bus.addListener(LevelEvent.Load::class.java) { event ->
            (event.level as? ServerLevel)?.let { onWorldLoad(it.server, it) }
        }
        bus.addListener(PlayerInteractEvent.LeftClickBlock::class.java) { event ->
            if (event.action == PlayerInteractEvent.LeftClickBlock.Action.START) {
                val face = event.face ?: Direction.DOWN
                if (onBlockAttack(event.entity, event.level, event.hand, event.pos, face) != InteractionResult.PASS) {
                    event.isCanceled = true
                }
            }
        }
        bus.addListener(PlayerInteractEvent.RightClickBlock::class.java) { event ->
            val result = onUseBlock(event.entity, event.level, event.hand, event.hitVec)
            if (result != InteractionResult.PASS) {
                event.cancellationResult = result
                event.isCanceled = true
            }
        }
    }

    // Ledger's client mod protocol isn't available on NeoForge yet, so results always go to chat.
    override suspend fun sendSearchResultsToClientMod(source: CommandSourceStack, results: SearchResults) = false
}
