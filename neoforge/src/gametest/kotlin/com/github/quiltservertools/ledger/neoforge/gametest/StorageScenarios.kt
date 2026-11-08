package com.github.quiltservertools.ledger.neoforge.gametest

import appeng.blockentity.storage.MEChestBlockEntity
import appeng.helpers.InventoryAction
import appeng.menu.me.common.MEStorageMenu
import com.github.quiltservertools.ledger.gametest.CENTER
import com.github.quiltservertools.ledger.gametest.Expected
import com.github.quiltservertools.ledger.gametest.Scenario
import com.github.quiltservertools.ledger.gametest.afterQueueFlush
import com.github.quiltservertools.ledger.gametest.checkRollback
import com.github.quiltservertools.ledger.gametest.floor
import com.github.quiltservertools.ledger.gametest.id
import com.github.quiltservertools.ledger.gametest.loggedNear
import com.github.quiltservertools.ledger.gametest.player
import com.github.quiltservertools.ledger.gametest.waitFor
import com.github.quiltservertools.ledger.gametest.whenLogged
import com.github.quiltservertools.ledger.neoforge.storage.ModdedStorage
import com.github.quiltservertools.ledger.neoforge.storage.StorageCompat
import com.refinedmods.refinedstorage.api.network.node.grid.GridInsertMode
import com.refinedmods.refinedstorage.common.grid.AbstractGridContainerMenu
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import net.minecraft.commands.arguments.EntityAnchorArgument
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.network.HashedStack
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.resources.Identifier
import net.minecraft.server.RunningOnDifferentThreadException
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.ServerGamePacketListenerImpl
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.neoforged.fml.ModList
import net.neoforged.neoforge.network.handling.ServerPayloadContext
import net.neoforged.neoforge.transfer.item.ItemResource
import net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackContainer

/** Where a player's eyes are level with the block at the centre, so that looking at it hits its front. */
private val EYE_LEVEL_WITH_CENTER = CENTER.north().below()

private fun block(id: String): Block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(id))

/**
 * Hands a packet to the player's connection from another thread, like a real client's packets arrive. The game then
 * queues it for the server thread, which is the route Ledger's storage tracking hooks into.
 */
private fun fromNetwork(handle: () -> Unit) {
    val thread = Thread {
        try {
            handle()
        } catch (_: RunningOnDifferentThreadException) {
            // How the game leaves the network thread after queueing a packet.
        }
    }
    thread.start()
    thread.join()
}

/** Does what a real client does after joining, without which the game ignores its clicks. */
private fun ServerPlayer.finishLoading() {
    val awaiting = ServerGamePacketListenerImpl::class.java.getDeclaredField("awaitingTeleport")
    awaiting.isAccessible = true
    connection.handleAcceptTeleportPacket(ServerboundAcceptTeleportationPacket(awaiting.getInt(connection)))
    connection.handleAcceptPlayerLoad(ServerboundPlayerLoadedPacket())
}

/** A player north of the centre, ready to send packets. */
private fun GameTestHelper.clickingPlayer(at: BlockPos = CENTER.north()): ServerPlayer {
    val player = player(at)
    player.connection.teleport(player.x, player.y, player.z, 0f, 0f)
    player.finishLoading()
    return player
}

private fun ServerPlayer.sendClick(slot: Int, input: ContainerInput = ContainerInput.PICKUP) {
    val menu = containerMenu
    val packet = ServerboundContainerClickPacket(
        menu.containerId,
        menu.stateId,
        slot.toShort(),
        0,
        input,
        Int2ObjectOpenHashMap(),
        HashedStack.EMPTY,
    )
    fromNetwork { connection.handleContainerClick(packet) }
}

private fun GameTestHelper.sendUse(player: ServerPlayer, pos: BlockPos, item: ItemStack, face: Direction = Direction.UP) {
    player.setItemInHand(InteractionHand.MAIN_HAND, item)
    val target = absolutePos(pos)
    val center = Vec3.atCenterOf(target)
    // Some blocks, like drawers, work out what was clicked from where the player is looking.
    player.lookAt(EntityAnchorArgument.Anchor.EYES, center)
    val hit = BlockHitResult(center.add(Vec3(face.step()).scale(0.5)), face, target, false)
    fromNetwork { player.connection.handleUseItemOn(ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, hit, 0)) }
}

private fun GameTestHelper.count(pos: BlockPos, item: ItemStack): Long =
    ModdedStorage.contents(level, absolutePos(pos))?.get(ItemResource.of(item)) ?: 0

private fun GameTestHelper.assertCount(pos: BlockPos, item: ItemStack, expected: Long, moment: String) {
    val found = count(pos, item)
    assertTrue(found == expected, Component.literal("$moment: expected $expected of $item but found $found"))
}

/** Opens a menu on a block, puts diamonds in its first slot, takes them out again, and rolls that back. */
private fun menuStorageScenario(name: String, blockId: String) = Scenario(name) { helper ->
    val diamonds = ItemStack(Items.DIAMOND, 3)
    helper.floor(1)
    helper.setBlock(CENTER, block(blockId))
    val player = helper.clickingPlayer()
    val who = player.scoreboardName
    helper.sendUse(player, CENTER, ItemStack.EMPTY)
    helper.waitFor("the menu to open", condition = { player.containerMenu !== player.inventoryMenu }) {
        player.containerMenu.setCarried(diamonds.copy())
        player.sendClick(0)
        helper.waitFor("the diamonds to go in", condition = { player.containerMenu.carried.isEmpty }) {
            helper.assertCount(CENTER, diamonds, 3, "after inserting")
            helper.whenLogged(CENTER, Expected("item-insert", "player", id("diamond"), who)) { inserted ->
                player.sendClick(0)
                helper.waitFor("the diamonds to come out", condition = { !player.containerMenu.carried.isEmpty }) {
                    helper.whenLogged(CENTER, Expected("item-remove", "player", id("diamond"), who)) { removed ->
                        fromNetwork { player.connection.handleContainerClose(ServerboundContainerClosePacket(player.containerMenu.containerId)) }
                        // Undoing the removal puts them back, and undoing the insertion takes them out.
                        helper.assertTrue(removed.rollback(helper.level.server), Component.literal("couldn't undo the removal"))
                        helper.assertCount(CENTER, diamonds, 3, "after undoing the removal")
                        helper.checkRollback(
                            inserted,
                            { helper.assertCount(CENTER, diamonds, 0, "after undoing the insertion") },
                            { helper.assertCount(CENTER, diamonds, 3, "after redoing the insertion") },
                        )
                    }
                }
            }
        }
    }
}

/** A storage's contents have to survive it being broken and rolled back. */
private fun brokenStorageScenario(name: String, blockId: String) = Scenario(name) { helper ->
    val diamonds = ItemStack(Items.DIAMOND, 5)
    helper.floor(1)
    helper.setBlock(CENTER, block(blockId))
    val pos = helper.absolutePos(CENTER)
    helper.assertTrue(ModdedStorage.insert(helper.level, pos, diamonds), Component.literal("couldn't fill the storage"))
    val player = helper.player(CENTER.north())
    player.gameMode.destroyBlock(pos)
    helper.whenLogged(CENTER, Expected("block-break", "player", player = player.scoreboardName)) { action ->
        helper.assertTrue(action.rollback(helper.level.server), Component.literal("rollback returned false"))
        helper.assertBlockPresent(block(blockId), CENTER)
        helper.assertCount(CENTER, diamonds, 5, "after rolling back the break")
        helper.succeed()
    }
}

/** Placed by a player, a drawer faces them. Otherwise it faces down. */
private fun northFacingDrawer(): BlockState {
    val drawer = block("functionalstorage:oak_1")
    @Suppress("UNCHECKED_CAST")
    val facing = drawer.stateDefinition.getProperty("facing") as EnumProperty<Direction>
    return drawer.defaultBlockState().setValue(facing, Direction.NORTH)
}

private fun sophisticatedStorageScenarios() = listOf(
    menuStorageScenario("sophisticated_chest_items", "sophisticatedstorage:chest"),
    menuStorageScenario("sophisticated_barrel_items", "sophisticatedstorage:barrel"),
    brokenStorageScenario("sophisticated_chest_broken", "sophisticatedstorage:chest"),
)

private fun functionalStorageScenarios() = listOf(
    Scenario("drawer_right_click_insert") { helper ->
        val diamonds = ItemStack(Items.DIAMOND, 3)
        helper.setBlock(CENTER, northFacingDrawer())
        val player = helper.clickingPlayer(EYE_LEVEL_WITH_CENTER)
        helper.sendUse(player, CENTER, diamonds.copy(), Direction.NORTH)
        helper.whenLogged(CENTER, Expected("item-insert", "player", id("diamond"), player.scoreboardName)) { action ->
            helper.checkRollback(
                action,
                { helper.assertCount(CENTER, diamonds, 0, "after rollback") },
                { helper.assertCount(CENTER, diamonds, 3, "after restore") },
            )
        }
    },
    Scenario("drawer_left_click_extract") { helper ->
        val diamonds = ItemStack(Items.DIAMOND, 64)
        helper.setBlock(CENTER, northFacingDrawer())
        val pos = helper.absolutePos(CENTER)
        helper.assertTrue(ModdedStorage.insert(helper.level, pos, diamonds), Component.literal("couldn't fill the drawer"))
        val player = helper.clickingPlayer(EYE_LEVEL_WITH_CENTER)
        // Left-clicking breaks the block in creative mode, so this has to be a survival player looking at the drawer.
        player.setGameMode(GameType.SURVIVAL)
        player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(pos))
        fromNetwork {
            player.connection.handlePlayerAction(
                ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, Direction.NORTH),
            )
        }
        helper.whenLogged(CENTER, Expected("item-remove", "player", id("diamond"), player.scoreboardName)) { action ->
            val left = helper.count(CENTER, diamonds)
            helper.assertTrue(left < 64, Component.literal("the drawer wasn't emptied"))
            helper.assertTrue(action.rollback(helper.level.server), Component.literal("rollback returned false"))
            helper.assertTrue(helper.count(CENTER, diamonds) > left, Component.literal("rollback didn't put anything back"))
            helper.succeed()
        }
    },
    brokenStorageScenario("drawer_broken", "functionalstorage:oak_1"),
)

private fun GameTestHelper.network(pos: BlockPos) = StorageCompat.network(level, absolutePos(pos))

private fun GameTestHelper.networkHolds(pos: BlockPos, stack: ItemStack): Boolean =
    network(pos)?.extract(stack, simulate = true) == stack.count.toLong()

/** Runs work the way a modded packet handler schedules it, which is how a terminal's clicks arrive. */
private fun ServerPlayer.asModdedPacket(task: () -> Unit) {
    ServerPayloadContext(connection, Identifier.parse("ledger_gametest:test")).enqueueWork(Runnable { task() })
}

/**
 * Checks what's common to every network terminal. [insert] should move what the player is carrying into the network,
 * and [opened] says when the terminal is ready.
 */
private fun GameTestHelper.checkTerminal(player: ServerPlayer, terminal: BlockPos, stack: ItemStack, insert: (ServerPlayer) -> Unit) {
    player.containerMenu.setCarried(stack.copy())
    player.asModdedPacket { insert(player) }
    waitFor("the items to go in", condition = { player.containerMenu.carried.isEmpty }) {
        assertTrue(networkHolds(terminal, stack), Component.literal("the network doesn't hold the items"))
        whenLogged(terminal, Expected("item-insert", "player", id("diamond"), player.scoreboardName)) { action ->
            assertTrue(action.objectState == StorageCompat.NETWORK_MARKER, Component.literal("not marked as going to a network"))
            checkRollback(
                action,
                { assertTrue(!networkHolds(terminal, stack), Component.literal("rollback left the items in the network")) },
                { assertTrue(networkHolds(terminal, stack), Component.literal("restore didn't put the items back")) },
            )
        }
    }
}

private fun refinedStorageScenarios() = listOf(
    Scenario("refined_storage_grid_insert", maxTicks = 2000) { helper ->
        val stack = ItemStack(Items.DIAMOND, 3)
        helper.floor(2)
        helper.setBlock(CENTER, block("refinedstorage:grid"))
        helper.setBlock(CENTER.west(), block("refinedstorage:creative_controller"))
        helper.setBlock(CENTER.east(), block("refinedstorage:disk_drive"))
        val player = helper.clickingPlayer()
        // Networks take a moment to form.
        helper.runAfterDelay(40) {
            helper.sendUse(player, CENTER.east(), ItemStack.EMPTY)
            helper.waitFor("the disk drive to open", condition = { player.containerMenu !== player.inventoryMenu }) {
                val disk = BuiltInRegistries.ITEM.getValue(Identifier.parse("refinedstorage:1k_storage_disk"))
                val diskStack = ItemStack(disk)
                // A disk gets its storage while it's in a player's inventory.
                diskStack.inventoryTick(helper.level, player, EquipmentSlot.MAINHAND)
                player.containerMenu.setCarried(diskStack)
                player.sendClick(0)
                helper.waitFor("the disk to go in", condition = { player.containerMenu.carried.isEmpty }) {
                    fromNetwork { player.connection.handleContainerClose(ServerboundContainerClosePacket(player.containerMenu.containerId)) }
                    helper.runAfterDelay(40) {
                        helper.sendUse(player, CENTER, ItemStack.EMPTY)
                        helper.waitFor("the grid to open", condition = { player.containerMenu is AbstractGridContainerMenu }) {
                            helper.checkTerminal(player, CENTER, stack) { p ->
                                (p.containerMenu as AbstractGridContainerMenu).onInsert(GridInsertMode.ENTIRE_RESOURCE, false)
                            }
                        }
                    }
                }
            }
        }
    },
)

private fun ae2Scenarios() = listOf(
    Scenario("ae2_chest_terminal_insert", maxTicks = 2000) { helper ->
        val stack = ItemStack(Items.DIAMOND, 3)
        helper.floor(2)
        helper.setBlock(CENTER, block("ae2:me_chest"))
        helper.setBlock(CENTER.west(), block("ae2:creative_energy_cell"))
        val cell = BuiltInRegistries.ITEM.getValue(Identifier.parse("ae2:item_storage_cell_1k"))
        helper.getBlockEntity(CENTER, MEChestBlockEntity::class.java).setCell(ItemStack(cell))
        val player = helper.clickingPlayer()
        val chest = helper.getBlockEntity(CENTER, MEChestBlockEntity::class.java)
        helper.waitFor("the chest to get power", condition = { chest.isPowered && chest.mainNode.isActive }) {
            // The top is the terminal. Any other side opens the slot for its storage cell.
            helper.sendUse(player, CENTER, ItemStack.EMPTY, chest.top)
            helper.waitFor("the terminal to open", condition = { player.containerMenu is MEStorageMenu }) {
                helper.checkTerminal(player, CENTER, stack) { p ->
                    (p.containerMenu as MEStorageMenu).handleInteraction(-1, InventoryAction.PICKUP_OR_SET_DOWN)
                }
            }
        }
    },
)

private fun backpackScenarios() = listOf(
    Scenario("backpack_items") { helper ->
        val diamonds = ItemStack(Items.DIAMOND, 3)
        helper.floor(2)
        val player = helper.clickingPlayer()
        val backpack = ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("sophisticatedbackpacks:backpack")))
        player.setItemInHand(InteractionHand.MAIN_HAND, backpack)
        fromNetwork { player.connection.handleUseItem(ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 0, 0f, 0f)) }
        helper.waitFor("the backpack to open", condition = { player.containerMenu is BackpackContainer }) {
            val contents = (player.containerMenu as BackpackContainer).storageWrapper.inventoryHandler
            fun held() = (0 until contents.size()).sumOf { if (contents.getResource(it).`is`(Items.DIAMOND)) contents.getAmountAsLong(it) else 0L }
            player.containerMenu.setCarried(diamonds.copy())
            player.sendClick(0)
            helper.waitFor("the diamonds to go in", condition = { player.containerMenu.carried.isEmpty }) {
                // Logged where the player is, since the backpack could be anywhere later.
                helper.whenLogged(CENTER.north(), Expected("item-insert", "player", id("diamond"), player.scoreboardName), radius = 1) { action ->
                    helper.assertTrue(action.objectState?.startsWith("backpack:") == true, Component.literal("not marked as a backpack"))
                    fromNetwork { player.connection.handleContainerClose(ServerboundContainerClosePacket(player.containerMenu.containerId)) }
                    helper.waitFor("the backpack to close", condition = { player.containerMenu === player.inventoryMenu }) {
                        // With the menu closed, rollback has to find the backpack in the player's inventory.
                        helper.checkRollback(
                            action,
                            { helper.assertTrue(held() == 0L, Component.literal("rollback left ${held()} diamonds")) },
                            { helper.assertTrue(held() == 3L, Component.literal("restore left ${held()} diamonds")) },
                        )
                    }
                }
            }
        }
    },
)

private fun dankStorageScenarios() = listOf(
    Scenario("dank_storage_is_logged") { helper ->
        helper.floor(2)
        val player = helper.clickingPlayer()
        val dank = ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("dankstorage:dank_1")))
        player.setItemInHand(InteractionHand.MAIN_HAND, dank)
        fromNetwork { player.connection.handleUseItem(ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 0, 0f, 0f)) }
        helper.waitFor("the dank to open", condition = { player.containerMenu !== player.inventoryMenu }) {
            player.containerMenu.setCarried(ItemStack(Items.DIAMOND, 3))
            player.sendClick(0)
            helper.waitFor("the diamonds to go in", condition = { player.containerMenu.carried.isEmpty }) {
                helper.whenLogged(CENTER.north(), Expected("item-insert", "player", id("diamond"), player.scoreboardName), radius = 1) { action ->
                    helper.assertTrue(action.objectState == StorageCompat.UNREACHABLE_MARKER, Component.literal("should be marked as unreachable"))
                    helper.assertTrue(!action.rollback(helper.level.server), Component.literal("rollback should report that it can't do this"))
                    helper.succeed()
                }
            }
        }
    },
)

/** How many of an item the saved actions say went in, across all of them. */
private fun GameTestHelper.loggedInserts(pos: BlockPos, name: String) = loggedNear(pos, 0)
    .filter { it.identifier == "item-insert" && it.objectIdentifier.toString() == id(name) }
    .also { actions -> actions.forEach { require(it.extraData != null) } }

/** Mistakes the tracking could make, which matter as much as what it logs. */
private fun trackingMistakeScenarios() = listOf(
    Scenario("vanilla_chest_is_only_logged_once") { helper ->
        helper.floor(1)
        helper.setBlock(CENTER, Blocks.CHEST)
        val player = helper.clickingPlayer()
        helper.sendUse(player, CENTER, ItemStack.EMPTY)
        helper.waitFor("the chest to open", condition = { player.containerMenu !== player.inventoryMenu }) {
            player.containerMenu.setCarried(ItemStack(Items.DIAMOND, 3))
            player.sendClick(0)
            helper.waitFor("the diamonds to go in", condition = { player.containerMenu.carried.isEmpty }) {
                fromNetwork { player.connection.handleContainerClose(ServerboundContainerClosePacket(player.containerMenu.containerId)) }
                helper.waitFor("the chest to close", condition = { player.containerMenu === player.inventoryMenu }) {
                    helper.afterQueueFlush {
                        val logged = helper.loggedInserts(CENTER, "diamond").size
                        helper.assertTrue(logged == 1, Component.literal("logged $logged times instead of once"))
                        helper.succeed()
                    }
                }
            }
        }
    },
    Scenario("throwing_items_away_from_a_menu_is_not_logged_as_storing_them") { helper ->
        helper.floor(1)
        helper.setBlock(CENTER, block("sophisticatedstorage:chest"))
        val player = helper.clickingPlayer()
        helper.sendUse(player, CENTER, ItemStack.EMPTY)
        helper.waitFor("the chest to open", condition = { player.containerMenu !== player.inventoryMenu }) {
            player.containerMenu.setCarried(ItemStack(Items.DIAMOND, 3))
            // Clicking outside the window throws away what the player is carrying.
            player.sendClick(AbstractContainerMenu.SLOT_CLICKED_OUTSIDE)
            helper.waitFor("the diamonds to be thrown", condition = { player.containerMenu.carried.isEmpty }) {
                helper.afterQueueFlush {
                    val logged = helper.loggedInserts(CENTER, "diamond").size
                    helper.assertTrue(logged == 0, Component.literal("thrown items were logged as stored"))
                    helper.succeed()
                }
            }
        }
    },
    Scenario("placing_a_block_against_storage_is_not_logged_as_storing_it") { helper ->
        helper.setBlock(CENTER, northFacingDrawer())
        val player = helper.clickingPlayer(EYE_LEVEL_WITH_CENTER)
        // Sneaking places the block against the drawer instead of storing it.
        player.setShiftKeyDown(true)
        helper.sendUse(player, CENTER, ItemStack(Items.STONE), Direction.UP)
        helper.waitFor("the block to be placed", condition = { !helper.getBlockState(CENTER.above()).isAir }) {
            helper.afterQueueFlush {
                val logged = helper.loggedInserts(CENTER, "stone").size
                helper.assertTrue(logged == 0, Component.literal("a placed block was logged as stored"))
                helper.succeed()
            }
        }
    },
    Scenario("rollback_reports_when_the_storage_is_gone") { helper ->
        helper.floor(1)
        helper.setBlock(CENTER, block("sophisticatedstorage:chest"))
        val player = helper.clickingPlayer()
        helper.sendUse(player, CENTER, ItemStack.EMPTY)
        helper.waitFor("the chest to open", condition = { player.containerMenu !== player.inventoryMenu }) {
            player.containerMenu.setCarried(ItemStack(Items.DIAMOND, 3))
            player.sendClick(0)
            helper.whenLogged(CENTER, Expected("item-insert", "player", id("diamond"), player.scoreboardName)) { action ->
                helper.setBlock(CENTER, Blocks.AIR)
                helper.assertTrue(!action.rollback(helper.level.server), Component.literal("rollback claimed to work with no storage there"))
                helper.succeed()
            }
        }
    },
)

/** Tests for storage mods, which only exist when those mods are loaded, in the storageGameTests run. */
fun storageScenarios(): List<Scenario> = buildList {
    val loaded = ModList.get()
    if (loaded.isLoaded("sophisticatedstorage") && loaded.isLoaded("functionalstorage")) addAll(trackingMistakeScenarios())
    if (loaded.isLoaded("refinedstorage")) addAll(refinedStorageScenarios())
    if (loaded.isLoaded("ae2")) addAll(ae2Scenarios())
    if (loaded.isLoaded("sophisticatedbackpacks")) addAll(backpackScenarios())
    if (loaded.isLoaded("dankstorage")) addAll(dankStorageScenarios())
    if (loaded.isLoaded("sophisticatedstorage")) addAll(sophisticatedStorageScenarios())
    if (loaded.isLoaded("functionalstorage")) addAll(functionalStorageScenarios())
}
