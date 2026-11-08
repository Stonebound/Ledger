package com.github.quiltservertools.ledger.neoforge.storage

import net.minecraft.server.MinecraftServer
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.transfer.ResourceHandler
import net.neoforged.neoforge.transfer.item.ItemResource
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackItem
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.IBackpackWrapper
import net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackContainer
import java.util.UUID

/**
 * Sophisticated Backpacks. Only loaded when it's installed. A backpack's contents are kept under a UUID on the item,
 * which is what the marker saves.
 */
class SophisticatedBackpacksCompat : ItemStorageMod {
    override val prefix = "backpack:"

    override fun identify(menu: AbstractContainerMenu): String? {
        val backpack = menu as? BackpackContainer ?: return null
        return backpack.storageWrapper.contentsUuid.map { prefix + it }.orElse(null)
    }

    /**
     * Looks for the real backpack, open or in an online player's inventory, rather than building a separate view of
     * its contents that could overwrite the live one, or be overwritten by it.
     */
    override fun find(server: MinecraftServer, marker: String): ResourceHandler<ItemResource>? {
        val uuid = runCatching { UUID.fromString(marker.removePrefix(prefix)) }.getOrNull() ?: return null
        for (player in server.playerList.players) {
            val open = player.containerMenu as? BackpackContainer
            if (open != null && open.storageWrapper.contentsUuid.filter { it == uuid }.isPresent) {
                return open.storageWrapper.inventoryHandler
            }
            val inventory = player.inventory
            for (slot in 0 until inventory.containerSize) {
                wrapperWith(inventory.getItem(slot), uuid)?.let { return it.inventoryHandler }
            }
        }
        return null
    }

    private fun wrapperWith(stack: ItemStack, uuid: UUID): IBackpackWrapper? {
        if (stack.item !is BackpackItem) return null
        return BackpackWrapper.fromExistingData(stack).filter { wrapper ->
            wrapper.contentsUuid.filter { it == uuid }.isPresent
        }.orElse(null)
    }
}
