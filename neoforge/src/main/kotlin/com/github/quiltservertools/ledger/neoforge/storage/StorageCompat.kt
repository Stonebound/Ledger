package com.github.quiltservertools.ledger.neoforge.storage

import net.minecraft.core.BlockPos
import net.minecraft.core.GlobalPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack
import net.neoforged.fml.ModList
import net.neoforged.neoforge.transfer.ResourceHandler
import net.neoforged.neoforge.transfer.item.ItemResource

/** A whole storage network. Amounts are how much was, or with [simulate] would be, moved. */
interface NetworkStorage {
    fun insert(stack: ItemStack, simulate: Boolean): Long
    fun extract(stack: ItemStack, simulate: Boolean): Long
}

/** A mod whose terminals show a whole storage network rather than a block's own slots. */
interface NetworkMod {
    fun isTerminal(menu: AbstractContainerMenu): Boolean

    /** For a wireless terminal, a block on its network. Null if it isn't one, or isn't known yet. */
    fun locate(menu: AbstractContainerMenu): GlobalPos?

    /** The network the block at [pos] is part of. */
    fun find(level: ServerLevel, pos: BlockPos): NetworkStorage?
}

/** A mod with storage that lives in an item, which can be found again by the marker saved with each action. */
interface ItemStorageMod {
    val prefix: String

    /** The marker for what the menu shows. Null if it isn't this mod's. */
    fun identify(menu: AbstractContainerMenu): String?

    /** The storage for a marker, if it can be reached right now. */
    fun find(server: MinecraftServer, marker: String): ResourceHandler<ItemResource>?
}

/**
 * The optional mods that need their own handling. Each mod's code is only touched if that mod is installed, so that
 * its classes are never looked for otherwise. Every other mod's storage is reached through NeoForge's item capability.
 */
object StorageCompat {
    /** Saved with actions that went into a storage network. */
    const val NETWORK_MARKER = "network"

    /** Saved with actions on storage that can be logged but not found again, so they can't be rolled back. */
    const val UNREACHABLE_MARKER = "unreachable"

    // Terminals of networks Ledger has no way into, so they're logged but can't be rolled back.
    private val logOnlyTerminals = setOf(
        "org.cyclops.integratedterminals.inventory.container.ContainerTerminalStorageBase",
    )

    // Portable storage opened from the item, identified by mod. Logged at the player, and can't be rolled back.
    private val logOnlyItemStorageMods = setOf("dankstorage", "toolbelt")

    private val networkMods: List<NetworkMod> by lazy {
        buildList {
            if (ModList.get().isLoaded("ae2")) add(Ae2Compat())
            if (ModList.get().isLoaded("refinedstorage")) add(RefinedStorageCompat())
        }
    }

    private val itemStorageMods: List<ItemStorageMod> by lazy {
        buildList {
            if (ModList.get().isLoaded("sophisticatedbackpacks")) add(SophisticatedBackpacksCompat())
        }
    }

    fun isTerminal(menu: AbstractContainerMenu) = networkMods.any { it.isTerminal(menu) } || isLogOnlyTerminal(menu)

    private fun isLogOnlyTerminal(menu: AbstractContainerMenu) =
        generateSequence<Class<*>>(menu.javaClass) { it.superclass }.any { it.name in logOnlyTerminals }

    fun locateTerminal(menu: AbstractContainerMenu): GlobalPos? = networkMods.firstNotNullOfOrNull { it.locate(menu) }

    fun network(level: ServerLevel, pos: BlockPos): NetworkStorage? = networkMods.firstNotNullOfOrNull {
        it.find(
            level,
            pos,
        )
    }

    /** The marker for an item's storage shown in this menu. Null if it isn't one. */
    fun identifyItemStorage(menu: AbstractContainerMenu): String? {
        itemStorageMods.firstNotNullOfOrNull { it.identify(menu) }?.let { return it }
        val menuId = runCatching { BuiltInRegistries.MENU.getKey(menu.type) }.getOrNull()
        return if (menuId?.namespace in logOnlyItemStorageMods) UNREACHABLE_MARKER else null
    }

    /** The item storage a marker refers to, if it can be reached right now. */
    fun itemStorage(server: MinecraftServer, marker: String): ResourceHandler<ItemResource>? =
        itemStorageMods.firstOrNull { marker.startsWith(it.prefix) }?.find(server, marker)
}
