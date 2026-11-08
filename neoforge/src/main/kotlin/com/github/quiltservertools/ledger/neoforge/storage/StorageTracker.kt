package com.github.quiltservertools.ledger.neoforge.storage

import com.github.quiltservertools.ledger.actionutils.ActionFactory
import com.github.quiltservertools.ledger.actionutils.LocationalInventory
import com.github.quiltservertools.ledger.database.ActionQueueService
import com.github.quiltservertools.ledger.utility.HandlerWithContext
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.PacketListener
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.ServerGamePacketListenerImpl
import net.minecraft.world.Container
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.event.entity.item.ItemTossEvent
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.transfer.item.ItemResource
import java.util.UUID

/**
 * Logs items players move into and out of modded storage.
 *
 * Ledger's own logging reads a menu's slots, which only works for vanilla-style containers. Modded menus can use any
 * kind of slot, or none at all in the case of a storage network's terminal. So instead this compares what a player
 * holds before and after each packet that can move items: what they lost went into the storage, and what they gained
 * came out of it. Clicking a block directly, as with drawers, compares the block's own contents instead.
 *
 * Runs on the server thread only.
 */
object StorageTracker {
    /**
     * A menu the player has open on a storage. [marker] tells rollback where the items went, if not the block itself.
     * [pos] is null for a wireless terminal that hasn't found its network yet, which AE2's do only after opening.
     */
    private class Session(
        val menu: AbstractContainerMenu,
        val level: ServerLevel,
        val pos: BlockPos?,
        val marker: String?,
    )

    /** What a player held when a packet started. */
    private class Capture(val player: ServerPlayer, val session: Session, val pos: BlockPos) {
        val before = playerItems(player)
        val tossed = HashMap<ItemResource, Long>()
    }

    private val sessions = HashMap<UUID, Session>()

    /** The block a player is clicking in the packet being handled. A menu opened meanwhile belongs to it. */
    private val usingOn = HashMap<UUID, BlockPos>()
    private var active: Capture? = null

    fun register(bus: IEventBus) {
        bus.addListener(PlayerContainerEvent.Open::class.java, ::onOpen)
        bus.addListener(PlayerContainerEvent.Close::class.java, ::onClose)
        bus.addListener(EventPriority.LOWEST, ItemTossEvent::class.java, ::onToss)
        bus.addListener(PlayerEvent.PlayerLoggedOutEvent::class.java) {
            sessions.remove(it.entity.uuid)
            usingOn.remove(it.entity.uuid)
        }
    }

    /** Runs the handling of a packet from a player. */
    @JvmStatic
    fun around(packet: Packet<*>, listener: PacketListener, task: Runnable) {
        val player = (listener as? ServerGamePacketListenerImpl)?.player
        when {
            player == null || active != null -> task.run()

            packet is ServerboundUseItemOnPacket -> aroundBlockClick(
                player,
                packet.hitResult.blockPos,
                task,
                tracksMenu = true,
            )

            packet is ServerboundPlayerActionPacket &&
                packet.action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK ->
                aroundBlockClick(player, packet.pos, task, tracksMenu = false)

            packet is ServerboundContainerClickPacket ||
                packet is ServerboundContainerButtonClickPacket ||
                packet is ServerboundPlaceRecipePacket ||
                packet is ServerboundCustomPayloadPacket -> aroundMenu(player, task)

            else -> task.run()
        }
    }

    /** Runs work that a modded packet handler scheduled for later. */
    @JvmStatic
    fun aroundPayload(listener: PacketListener, task: Runnable): Runnable {
        val player = (listener as? ServerGamePacketListenerImpl)?.player ?: return task
        return Runnable { if (active == null) aroundMenu(player, task) else task.run() }
    }

    private fun aroundMenu(player: ServerPlayer, task: Runnable) {
        val session = sessions[player.uuid]?.takeIf { it.menu === player.containerMenu }?.let { resolve(player, it) }
        val pos = session?.pos
        if (pos == null) {
            task.run()
            return
        }
        val capture = Capture(player, session, pos)
        active = capture
        try {
            task.run()
        } finally {
            active = null
            logPlayerChange(capture)
        }
    }

    private fun resolve(player: ServerPlayer, session: Session): Session {
        if (session.pos != null) return session
        val found = StorageCompat.locateTerminal(session.menu) ?: return session
        val level = player.level().server.getLevel(found.dimension()) ?: return session
        return Session(session.menu, level, found.pos(), session.marker).also { sessions[player.uuid] = it }
    }

    private fun aroundBlockClick(player: ServerPlayer, pos: BlockPos, task: Runnable, tracksMenu: Boolean) {
        val level = player.level()
        // Vanilla blocks that hold items are already logged by Ledger's own mixins.
        val modded = level.isLoaded(
            pos,
        ) && BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).block).namespace != "minecraft"
        val blockEntity = if (modded) level.getBlockEntity(pos) else null
        val before = if (blockEntity != null) ModdedStorage.contents(level, pos) else null

        if (tracksMenu) usingOn[player.uuid] = pos.immutable()
        try {
            task.run()
        } finally {
            if (tracksMenu) usingOn.remove(player.uuid)
            if (before != null) logBlockChange(player, level, pos.immutable(), blockEntity, before)
        }
    }

    private fun logBlockChange(
        player: ServerPlayer,
        level: ServerLevel,
        pos: BlockPos,
        blockEntity: BlockEntity?,
        before: Map<ItemResource, Long>,
    ) {
        // If the block was replaced or broken, that's logged as such.
        if (level.getBlockEntity(pos) !== blockEntity) return
        val after = ModdedStorage.contents(level, pos) ?: return
        (before.keys + after.keys).forEach { resource ->
            log(player, level, pos, null, resource, (after[resource] ?: 0) - (before[resource] ?: 0))
        }
    }

    private fun logPlayerChange(capture: Capture) {
        val after = playerItems(capture.player)
        val change = HashMap<ItemResource, Long>()
        capture.before.forEach { (resource, count) -> change.merge(resource, count, Long::plus) }
        after.forEach { (resource, count) -> change.merge(resource, -count, Long::plus) }
        capture.tossed.forEach { (resource, count) -> change.merge(resource, -count, Long::plus) }

        // An item that changed its data, like a tool losing durability, looks like one removed and one added.
        val unstackableNet = HashMap<Any, Long>()
        change.forEach { (resource, count) ->
            if (resource.toStack().maxStackSize == 1) unstackableNet.merge(resource.item, count, Long::plus)
        }
        change.keys.removeIf { it.toStack().maxStackSize == 1 && unstackableNet[it.item] == 0L }

        val session = capture.session
        change.forEach { (resource, count) ->
            log(
                capture.player,
                session.level,
                capture.pos,
                session.marker,
                resource,
                count,
            )
        }
    }

    /** Logs [change] items going into storage, or coming out if negative. */
    private fun log(
        player: ServerPlayer,
        level: ServerLevel,
        pos: BlockPos,
        marker: String?,
        resource: ItemResource,
        change: Long,
    ) {
        val template = resource.toStack()
        var left = Math.abs(change)
        while (left > 0) {
            val stack: ItemStack = template.copyWithCount(minOf(left, template.maxStackSize.toLong()).toInt())
            left -= stack.count
            val action = if (change > 0) {
                ActionFactory.itemInsertAction(level, stack, pos, player)
            } else {
                ActionFactory.itemRemoveAction(level, stack, pos, player)
            }
            action.objectState = marker
            ActionQueueService.addToQueue(action)
        }
    }

    private fun playerItems(player: ServerPlayer): Map<ItemResource, Long> {
        val items = HashMap<ItemResource, Long>()
        fun add(stack: ItemStack) {
            if (!stack.isEmpty) items.merge(ItemResource.of(stack), stack.count.toLong(), Long::plus)
        }
        val inventory = player.inventory
        for (slot in 0 until inventory.containerSize) add(inventory.getItem(slot))
        add(player.containerMenu.carried)
        return items
    }

    private fun onOpen(event: PlayerContainerEvent.Open) {
        val player = event.entity as? ServerPlayer ?: return
        val menu = event.container
        sessions.remove(player.uuid)
        if (ledgerLogsSlots(menu)) return

        val level = player.level()
        val isTerminal = StorageCompat.isTerminal(menu)
        val pos = usingOn[player.uuid]?.takeIf { level.getBlockEntity(it) != null }
        if (pos != null) {
            if (isTerminal) {
                sessions[player.uuid] = Session(menu, level, pos, StorageCompat.NETWORK_MARKER)
            } else if (ModdedStorage.contents(level, pos) != null) {
                sessions[player.uuid] = Session(menu, level, pos, null)
            }
            return
        }

        // Not opened on a block, so it's either storage in an item or a wireless terminal.
        val itemMarker = StorageCompat.identifyItemStorage(menu)
        if (itemMarker != null) {
            sessions[player.uuid] = Session(menu, level, player.blockPosition(), itemMarker)
        } else if (isTerminal) {
            sessions[player.uuid] = resolve(player, Session(menu, level, null, StorageCompat.NETWORK_MARKER))
        }
    }

    /** Whether Ledger's slot logging already covers this menu, so that tracking it too would log it twice. */
    private fun ledgerLogsSlots(menu: AbstractContainerMenu): Boolean = (menu as HandlerWithContext).pos != null &&
        menu.slots.any { (it.container as Container?) is LocationalInventory }

    private fun onClose(event: PlayerContainerEvent.Close) {
        if (sessions[event.entity.uuid]?.menu === event.container) sessions.remove(event.entity.uuid)
    }

    private fun onToss(event: ItemTossEvent) {
        val capture = active ?: return
        if (capture.player === event.player && !event.isCanceled) {
            val stack = event.entity.item
            capture.tossed.merge(ItemResource.of(stack), stack.count.toLong(), Long::plus)
        }
    }
}
