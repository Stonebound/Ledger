package com.github.quiltservertools.ledger.neoforge.storage

import com.github.quiltservertools.ledger.logWarn
import com.refinedmods.refinedstorage.api.core.Action
import com.refinedmods.refinedstorage.api.network.Network
import com.refinedmods.refinedstorage.api.network.node.GraphNetworkComponent
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent
import com.refinedmods.refinedstorage.api.storage.Actor
import com.refinedmods.refinedstorage.common.api.support.network.InWorldNetworkNodeContainer
import com.refinedmods.refinedstorage.common.api.support.network.item.NetworkItemContext
import com.refinedmods.refinedstorage.common.grid.AbstractGridContainerMenu
import com.refinedmods.refinedstorage.common.grid.WirelessGridContainerMenu
import com.refinedmods.refinedstorage.common.storage.portablegrid.AbstractPortableGridContainerMenu
import com.refinedmods.refinedstorage.common.support.resource.ItemResource
import com.refinedmods.refinedstorage.neoforge.api.RefinedStorageNeoForgeApi
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.GlobalPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack
import java.lang.reflect.Field

/** Refined Storage. Only loaded when it's installed. */
class RefinedStorageCompat : NetworkMod {
    // A wireless grid's network can only be reached through private fields, so a future version may need this updating.
    private val menuGrid: Field? = field(AbstractGridContainerMenu::class.java, "grid")
    private val wirelessGridContext: Field? = runCatching {
        field(Class.forName("com.refinedmods.refinedstorage.common.grid.WirelessGrid"), "context")
    }.getOrNull()
    private var warnedAboutWireless = false

    private fun field(owner: Class<*>, name: String): Field? = runCatching {
        owner.getDeclaredField(name).apply { isAccessible = true }
    }.onFailure {
        logWarn(
            "Refined Storage's ${owner.name}.$name wasn't found, so wireless grids won't be logged",
        )
    }.getOrNull()

    // Portable grids are excluded: their storage is their own disk, not a network.
    override fun isTerminal(menu: AbstractContainerMenu) =
        menu is AbstractGridContainerMenu && menu !is AbstractPortableGridContainerMenu

    @Suppress("TooGenericExceptionCaught") // A problem here mustn't stop the player using their terminal.
    override fun locate(menu: AbstractContainerMenu): GlobalPos? {
        if (menu !is WirelessGridContainerMenu || menuGrid == null || wirelessGridContext == null) return null
        try {
            val grid = menuGrid.get(menu) ?: return null
            if (!wirelessGridContext.declaringClass.isInstance(grid)) return null
            val context = wirelessGridContext.get(grid) as NetworkItemContext
            val network: Network = context.resolveNetwork().orElse(null) ?: return null
            return network.getComponent(GraphNetworkComponent::class.java).containers
                .filterIsInstance<InWorldNetworkNodeContainer>()
                .firstOrNull { !it.isRemoved }
                ?.position
        } catch (e: Exception) {
            if (!warnedAboutWireless) {
                warnedAboutWireless = true
                logWarn("Couldn't find the network of a Refined Storage wireless grid, so it won't be logged", e)
            }
            return null
        }
    }

    override fun find(level: ServerLevel, pos: BlockPos): NetworkStorage? {
        val capability = RefinedStorageNeoForgeApi.INSTANCE.networkNodeContainerProviderCapability
        val provider = level.getCapability(capability, pos, null)
            ?: Direction.entries.firstNotNullOfOrNull { level.getCapability(capability, pos, it) }
            ?: return null
        val network = provider.containers.firstNotNullOfOrNull { it.node.network } ?: return null
        val storage = network.getComponent(StorageNetworkComponent::class.java)
        return object : NetworkStorage {
            private fun action(simulate: Boolean) = if (simulate) Action.SIMULATE else Action.EXECUTE

            override fun insert(stack: ItemStack, simulate: Boolean) =
                storage.insert(ItemResource.ofItemStack(stack), stack.count.toLong(), action(simulate), Actor.EMPTY)

            override fun extract(stack: ItemStack, simulate: Boolean) =
                storage.extract(ItemResource.ofItemStack(stack), stack.count.toLong(), action(simulate), Actor.EMPTY)
        }
    }
}
