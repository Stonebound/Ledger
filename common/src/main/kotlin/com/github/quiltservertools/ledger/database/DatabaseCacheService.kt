package com.github.quiltservertools.ledger.database

import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap
import net.minecraft.resources.Identifier
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object DatabaseCacheService {
    val actionIdentifierKeys: BiMap<String, Int> = HashBiMap.create()

    val worldIdentifierKeys: BiMap<Identifier, Int> = HashBiMap.create()

    val objectIdentifierKeys: BiMap<Identifier, Int> = HashBiMap.create()

    val sourceKeys: BiMap<String, Int> = HashBiMap.create()

    val playerKeys: BiMap<UUID, Int> = HashBiMap.create()

    // Not one-to-one: a player's old name can later belong to someone else.
    val playerNames: MutableMap<Int, String> = ConcurrentHashMap()
}
