package com.github.quiltservertools.ledger.database

import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap
import net.minecraft.resources.Identifier
import java.util.*
import java.util.concurrent.ConcurrentHashMap

// Reloading builds new maps and swaps them in, so a search running meanwhile never sees a half-filled one.
object DatabaseCacheService {
    @Volatile
    var actionIdentifierKeys: BiMap<String, Int> = HashBiMap.create()

    @Volatile
    var worldIdentifierKeys: BiMap<Identifier, Int> = HashBiMap.create()

    @Volatile
    var objectIdentifierKeys: BiMap<Identifier, Int> = HashBiMap.create()

    @Volatile
    var sourceKeys: BiMap<String, Int> = HashBiMap.create()

    @Volatile
    var playerKeys: BiMap<UUID, Int> = HashBiMap.create()

    // Not one-to-one: a player's old name can later belong to someone else.
    @Volatile
    var playerNames: MutableMap<Int, String> = ConcurrentHashMap()
}
