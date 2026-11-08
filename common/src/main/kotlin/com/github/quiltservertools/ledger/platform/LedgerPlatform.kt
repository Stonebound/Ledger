package com.github.quiltservertools.ledger.platform

import com.github.quiltservertools.ledger.actionutils.SearchResults
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.level.ServerPlayer
import java.nio.file.Path
import java.util.ServiceLoader

/** What the shared code needs from the mod loader. Each loader module provides one implementation. */
interface LedgerPlatform {
    val configDir: Path
    val modVersion: String

    fun hasPermission(source: CommandSourceStack, node: String, level: Int): Boolean

    fun hasPermission(player: ServerPlayer, node: String, level: Int): Boolean

    /** Called while the server starts, after Ledger's own listeners are registered. */
    fun onServerStarting()

    /** Sends results to a compatible client mod. Returns false if the source doesn't have one. */
    suspend fun sendSearchResultsToClientMod(source: CommandSourceStack, results: SearchResults): Boolean

    companion object {
        @JvmStatic
        val instance: LedgerPlatform by lazy {
            ServiceLoader.load(LedgerPlatform::class.java, LedgerPlatform::class.java.classLoader).first()
        }
    }
}
