package com.github.quiltservertools.ledger.fabric

import com.github.quiltservertools.ledger.Ledger
import com.github.quiltservertools.ledger.actionutils.SearchResults
import com.github.quiltservertools.ledger.database.DatabaseManager
import com.github.quiltservertools.ledger.listeners.onBlockAttack
import com.github.quiltservertools.ledger.listeners.onBlockBreak
import com.github.quiltservertools.ledger.listeners.onJoin
import com.github.quiltservertools.ledger.listeners.onUseBlock
import com.github.quiltservertools.ledger.listeners.onWorldLoad
import com.github.quiltservertools.ledger.network.Networking
import com.github.quiltservertools.ledger.network.Networking.disableNetworking
import com.github.quiltservertools.ledger.network.Networking.hasNetworking
import com.github.quiltservertools.ledger.network.packet.action.ActionS2CPacket
import com.github.quiltservertools.ledger.platform.LedgerPlatform
import com.github.quiltservertools.ledger.utility.hasPlayer
import me.lucko.fabric.api.permissions.v0.Permissions
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents
import net.fabricmc.fabric.api.event.player.AttackBlockCallback
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.level.ServerPlayer
import java.nio.file.Path

class FabricPlatform : LedgerPlatform {
    override val configDir: Path
        get() = FabricLoader.getInstance().configDir

    override val modVersion: String
        get() = FabricLoader.getInstance().getModContainer(Ledger.MOD_ID).get().metadata.version.friendlyString

    override fun hasPermission(source: CommandSourceStack, node: String, level: Int) =
        Permissions.check(source, node, level)

    override fun hasPermission(player: ServerPlayer, node: String, level: Int) = Permissions.check(player, node, level)

    override fun onServerStarting() {
        PlayerBlockBreakEvents.AFTER.register(::onBlockBreak)
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ -> onJoin(handler.player) }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> handler.player.disableNetworking() }
        AttackBlockCallback.EVENT.register(::onBlockAttack)
        UseBlockCallback.EVENT.register(::onUseBlock)
        ServerLevelEvents.LOAD.register(::onWorldLoad)
        Networking
    }

    override suspend fun sendSearchResultsToClientMod(source: CommandSourceStack, results: SearchResults): Boolean {
        if (!source.hasPlayer() || !source.playerOrException.hasNetworking()) return false

        for (n in results.page..results.pages) {
            val networkResults = DatabaseManager.searchActions(results.searchParams, n)
            networkResults.actions.forEach {
                ServerPlayNetworking.send(source.playerOrException, ActionS2CPacket(it))
            }
        }
        return true
    }
}
