package com.github.quiltservertools.ledger.neoforge.storage

import appeng.api.config.Actionable
import appeng.api.implementations.menuobjects.ItemMenuHost
import appeng.api.networking.GridHelper
import appeng.api.networking.IGridNode
import appeng.api.networking.security.IActionSource
import appeng.api.stacks.AEItemKey
import appeng.helpers.WirelessTerminalMenuHost
import appeng.menu.me.common.MEStorageMenu
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.GlobalPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity

/** Applied Energistics 2. Only loaded when it's installed. */
class Ae2Compat : NetworkMod {
    // Portable cells are excluded: their storage is the cell in the item, not a network.
    override fun isTerminal(menu: AbstractContainerMenu) =
        menu is MEStorageMenu && (menu.host !is ItemMenuHost<*> || menu.host is WirelessTerminalMenuHost<*>)

    override fun locate(menu: AbstractContainerMenu): GlobalPos? {
        val host = (menu as? MEStorageMenu)?.host as? WirelessTerminalMenuHost<*> ?: return null
        val grid = host.actionableNode?.grid ?: return null
        for (node in grid.nodes) {
            val owner = node.owner as? BlockEntity ?: continue
            val level = owner.level
            if (level != null && !owner.isRemoved) return GlobalPos.of(level.dimension(), owner.blockPos)
        }
        return null
    }

    override fun find(level: ServerLevel, pos: BlockPos): NetworkStorage? {
        val host = GridHelper.getNodeHost(level, pos) ?: return null
        // Cable buses have their parts on different sides, but any of them is on the same network.
        val node: IGridNode = host.getGridNode(null)
            ?: Direction.entries.firstNotNullOfOrNull { host.getGridNode(it) }
            ?: return null
        val storage = node.grid?.storageService?.inventory ?: return null
        return object : NetworkStorage {
            private fun mode(simulate: Boolean) = if (simulate) Actionable.SIMULATE else Actionable.MODULATE

            override fun insert(stack: ItemStack, simulate: Boolean): Long {
                val key = AEItemKey.of(stack) ?: return 0
                return storage.insert(key, stack.count.toLong(), mode(simulate), IActionSource.empty())
            }

            override fun extract(stack: ItemStack, simulate: Boolean): Long {
                val key = AEItemKey.of(stack) ?: return 0
                return storage.extract(key, stack.count.toLong(), mode(simulate), IActionSource.empty())
            }
        }
    }
}
