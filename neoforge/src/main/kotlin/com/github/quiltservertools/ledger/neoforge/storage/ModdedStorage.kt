package com.github.quiltservertools.ledger.neoforge.storage

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.transfer.ResourceHandler
import net.neoforged.neoforge.transfer.ResourceHandlerUtil
import net.neoforged.neoforge.transfer.item.ItemResource
import net.neoforged.neoforge.transfer.transaction.Transaction

/** Reaches a block's inventory through NeoForge's item capability, which vanilla and modded blocks expose alike. */
object ModdedStorage {
    private fun handler(level: ServerLevel, pos: BlockPos): ResourceHandler<ItemResource>? =
        level.getCapability(Capabilities.Item.BLOCK, pos, null)
            ?: Direction.entries.firstNotNullOfOrNull { level.getCapability(Capabilities.Item.BLOCK, pos, it) }

    /** How much of each item the block holds, or null if it has no inventory. */
    fun contents(level: ServerLevel, pos: BlockPos): Map<ItemResource, Long>? {
        val handler = handler(level, pos) ?: return null
        val contents = HashMap<ItemResource, Long>()
        for (index in 0 until handler.size()) {
            val resource = handler.getResource(index)
            if (!resource.isEmpty) contents.merge(resource, handler.getAmountAsLong(index), Long::plus)
        }
        return contents
    }

    private fun storage(level: ServerLevel, pos: BlockPos, marker: String?): ResourceHandler<ItemResource>? =
        if (marker == null) handler(level, pos) else StorageCompat.itemStorage(level.server, marker)

    /** Adds all of [stack] to where [marker] says it went, or nothing if it doesn't all fit. */
    fun insert(level: ServerLevel, pos: BlockPos, stack: ItemStack, marker: String? = null): Boolean {
        if (marker == StorageCompat.NETWORK_MARKER) {
            val network = StorageCompat.network(level, pos) ?: return false
            val count = stack.count.toLong()
            return network.insert(stack, simulate = true) == count && network.insert(stack, simulate = false) == count
        }
        val handler = storage(level, pos, marker) ?: return false
        Transaction.openRoot().use { transaction ->
            val inserted = ResourceHandlerUtil.insertStacking(handler, ItemResource.of(stack), stack.count, transaction)
            if (inserted != stack.count) return false
            transaction.commit()
            return true
        }
    }

    /** Takes all of [stack] from where [marker] says it went, or nothing if there isn't that much. */
    fun extract(level: ServerLevel, pos: BlockPos, stack: ItemStack, marker: String? = null): Boolean {
        if (marker == StorageCompat.NETWORK_MARKER) {
            val network = StorageCompat.network(level, pos) ?: return false
            val count = stack.count.toLong()
            return network.extract(stack, simulate = true) == count && network.extract(stack, simulate = false) == count
        }
        val handler = storage(level, pos, marker) ?: return false
        Transaction.openRoot().use { transaction ->
            val extracted = handler.extract(ItemResource.of(stack), stack.count, transaction)
            if (extracted != stack.count) return false
            transaction.commit()
            return true
        }
    }
}
