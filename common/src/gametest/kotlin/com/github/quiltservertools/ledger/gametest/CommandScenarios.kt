package com.github.quiltservertools.ledger.gametest

import com.github.quiltservertools.ledger.Ledger
import com.github.quiltservertools.ledger.utility.isInspecting
import net.minecraft.commands.CommandSource
import net.minecraft.commands.CommandSourceStack
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.permissions.LevelBasedPermissionSet
import net.minecraft.server.players.NameAndId
import net.minecraft.world.entity.EntityType
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraft.world.phys.Vec3
import java.util.Optional
import java.util.concurrent.CopyOnWriteArrayList

private const val ACTION_LINE = "text.ledger.action_message"
private const val NO_RESULTS = "error.ledger.command.no_results"
private const val SEARCH_FOOTER = "text.ledger.footer.search"

/** Collects what a command sends back. Compares translation keys, which don't depend on the loaded language. */
private class CapturedOutput : CommandSource {
    val messages = CopyOnWriteArrayList<Component>()

    val keys: List<String> get() = messages.flatMap(::keysOf)

    private fun keysOf(component: Component): List<String> {
        val contents = component.contents
        val own = if (contents is TranslatableContents) {
            listOf(contents.key) + contents.args.filterIsInstance<Component>().flatMap(::keysOf)
        } else {
            emptyList()
        }
        return own + component.siblings.flatMap(::keysOf)
    }

    operator fun contains(key: String) = key in keys

    override fun sendSystemMessage(message: Component) {
        messages.add(message)
    }

    override fun acceptsSuccess() = true
    override fun acceptsFailure() = true
    override fun shouldInformAdmins() = false
}

/** A console-level source standing at [pos]. */
private fun GameTestHelper.console(output: CapturedOutput, pos: BlockPos = CENTER): CommandSourceStack =
    level.server.createCommandSourceStack()
        .withSource(output)
        .withLevel(level)
        .withPosition(absoluteVec(Vec3.atCenterOf(pos)))

private fun GameTestHelper.run(source: CommandSourceStack, command: String) =
    level.server.commands.performPrefixedCommand(source, command)

private fun GameTestHelper.opped(player: ServerPlayer): ServerPlayer {
    level.server.playerList.op(NameAndId(player.gameProfile), Optional.of(LevelBasedPermissionSet.OWNER), Optional.empty())
    return player
}

/** Runs [command] and continues when [key] comes back. */
private fun GameTestHelper.runUntil(
    command: String,
    key: String,
    output: CapturedOutput = CapturedOutput(),
    source: CommandSourceStack = console(output),
    then: (CapturedOutput) -> Unit,
) {
    run(source, command)
    waitFor("'$key' from /$command", condition = { key in output }) { then(output) }
}

/** Checks how many actions each search finds, one after another. */
private fun GameTestHelper.expectCounts(queries: List<Pair<String, Int>>, then: () -> Unit) {
    if (queries.isEmpty()) {
        then()
        return
    }
    val (query, count) = queries.first()
    val output = CapturedOutput()
    run(console(output), "lg search $query")
    waitFor("results of '$query'", condition = { SEARCH_FOOTER in output || NO_RESULTS in output }) {
        val found = output.keys.count { it == ACTION_LINE }
        assertTrue(found == count, Component.literal("'$query' found $found actions instead of $count"))
        expectCounts(queries.drop(1), then)
    }
}

/** Breaks a stone block and places planks, then runs [then] once both are saved. */
private fun GameTestHelper.griefAs(player: ServerPlayer, then: () -> Unit) {
    floor(2)
    setBlock(CENTER.east(), Blocks.STONE)
    breakAs(player, CENTER.east())
    useOn(player, FLOOR.west(), ItemStack(Items.OAK_PLANKS))
    assertBlockPresent(Blocks.OAK_PLANKS, CENTER.west())
    whenLogged(CENTER.east(), Expected("block-break", player = player.scoreboardName)) {
        whenLogged(CENTER.west(), Expected("block-place", player = player.scoreboardName)) { then() }
    }
}

private fun GameTestHelper.assertGriefed() {
    assertBlockPresent(Blocks.AIR, CENTER.east())
    assertBlockPresent(Blocks.OAK_PLANKS, CENTER.west())
}

private fun GameTestHelper.assertRolledBack() {
    assertBlockPresent(Blocks.STONE, CENTER.east())
    assertBlockPresent(Blocks.AIR, CENTER.west())
}

private fun clickPacket(id: Identifier, tag: CompoundTag) = ServerboundCustomClickActionPacket(id, Optional.of(tag))

val COMMAND_SCENARIOS = listOf(
    Scenario("command_search_filters") { helper ->
        val player = helper.player(CENTER.north())
        val name = player.scoreboardName
        val base = "source:$name range:4"
        helper.griefAs(player) {
            helper.expectCounts(
                listOf(
                    base to 2,
                    "$base action:block-break" to 1,
                    "$base action:!block-break" to 1,
                    "$base object:minecraft:stone" to 1,
                    "$base object:!minecraft:stone" to 1,
                    "$base after:1h" to 2,
                    "$base before:1h" to 0,
                    "$base rolledback:true" to 0,
                    "$base rolledback:false" to 2,
                    "$base world:minecraft:overworld" to 2,
                    "$base world:minecraft:the_nether" to 0,
                    "source:!$name range:4" to 0,
                    "source:@no_such_source range:4" to 0,
                    "source:nobody_by_this_name range:4" to 0,
                ),
            ) { helper.succeed() }
        }
    },
    Scenario("command_page") { helper ->
        val player = helper.player(CENTER.north())
        helper.griefAs(player) {
            val output = CapturedOutput()
            val source = helper.console(output)
            helper.runUntil("lg search source:${player.scoreboardName} range:4", SEARCH_FOOTER, output, source) {
                val next = CapturedOutput()
                helper.runUntil("lg page 99", "error.ledger.no_more_pages", next, helper.console(next)) { helper.succeed() }
            }
        }
    },
    Scenario("command_rollback_and_restore") { helper ->
        val player = helper.player(CENTER.north())
        val query = "source:${player.scoreboardName} range:4"
        helper.griefAs(player) {
            helper.runUntil("lg rollback $query", "text.ledger.rollback.finish") {
                helper.assertRolledBack()
                // Marking actions as rolled back is saved separately, so wait for it.
                helper.waitFor("rollback to be saved", condition = { helper.loggedNear(CENTER, 2).all { it.rolledBack } }) {
                    helper.runUntil("lg restore $query", "text.ledger.restore.finish") {
                        helper.assertGriefed()
                        helper.waitFor("restore to be saved", condition = { helper.loggedNear(CENTER, 2).none { it.rolledBack } }) {
                            helper.succeed()
                        }
                    }
                }
            }
        }
    },
    Scenario("command_rollback_chest_with_contents") { helper ->
        helper.setBlock(CENTER, Blocks.CHEST)
        helper.getBlockEntity(CENTER, ChestBlockEntity::class.java).setItem(0, ItemStack(Items.DIAMOND, 5))
        val player = helper.player(CENTER.north())
        helper.breakAs(player, CENTER)
        // The contents are logged as the block breaking them, not as the player, so this searches by time.
        val query = "after:1h range:2"
        helper.whenLogged(CENTER, Expected("block-break", objectId = id("chest"))) {
            helper.whenLogged(CENTER, Expected("item-remove", objectId = id("diamond"))) {
                helper.runUntil("lg rollback $query", "text.ledger.rollback.finish") { output ->
                    helper.assertBlockPresent(Blocks.CHEST, CENTER)
                    val stack = helper.getBlockEntity(CENTER, ChestBlockEntity::class.java).getItem(0)
                    helper.assertTrue(
                        stack.`is`(Items.DIAMOND) && stack.count == 5,
                        Component.literal("expected 5 diamonds, found $stack; output ${output.keys}"),
                    )
                    helper.assertTrue("text.ledger.rollback.fail" !in output, Component.literal("rollback reported failures"))
                    helper.succeed()
                }
            }
        }
    },
    Scenario("command_rollback_needs_specific_search") { helper ->
        helper.runUntil("lg rollback range:4", "error.ledger.unspecific.source_or_time") { helper.succeed() }
    },
    Scenario("command_rollback_nothing_found") { helper ->
        helper.runUntil("lg rollback source:@no_such_source range:4", NO_RESULTS) { helper.succeed() }
    },
    Scenario("command_rollback_continues_after_failed_action") { helper ->
        // A chest that's gone can't get its items back, which mustn't stop the rest of the rollback.
        helper.setBlock(CENTER, Blocks.CHEST)
        val player = helper.player(CENTER.north())
        val chest = helper.getBlockEntity(CENTER, ChestBlockEntity::class.java)
        player.openMenu(chest)
        player.containerMenu.setCarried(ItemStack(Items.DIAMOND, 3))
        player.containerMenu.clicked(0, 0, ContainerInput.PICKUP, player)
        player.closeContainer()
        helper.floor(1)
        helper.setBlock(CENTER.south(), Blocks.STONE)
        helper.whenLogged(CENTER, Expected("item-insert", objectId = id("diamond"))) {
            helper.breakAs(player, CENTER.south())
            helper.setBlock(CENTER, Blocks.AIR)
            helper.whenLogged(CENTER.south(), Expected("block-break", objectId = id("stone"))) {
                helper.runUntil("lg rollback source:${player.scoreboardName} range:3", "text.ledger.rollback.finish") { output ->
                    helper.assertBlockPresent(Blocks.STONE, CENTER.south())
                    helper.assertTrue("text.ledger.rollback.fail" in output, Component.literal("the failure wasn't reported"))
                    helper.succeed()
                }
            }
        }
    },
    Scenario("command_setblock_and_fill") { helper ->
        helper.floor(2)
        val player = helper.opped(helper.player(CENTER.north()))
        val output = CapturedOutput()
        val source = player.createCommandSourceStack().withSource(output)
        val single = helper.absolutePos(CENTER)
        val from = helper.absolutePos(CENTER.east(2))
        val to = helper.absolutePos(CENTER.east(3))
        helper.run(source, "setblock ${single.x} ${single.y} ${single.z} minecraft:stone")
        helper.run(source, "fill ${from.x} ${from.y} ${from.z} ${to.x} ${to.y} ${to.z} minecraft:oak_planks")
        val name = player.scoreboardName
        helper.whenLogged(CENTER, Expected("block-place", "command", id("stone"), name)) {
            helper.succeedWhenLogged(CENTER.east(2), Expected("block-place", "command", id("oak_planks"), name))
        }
    },
    Scenario("command_setblock_block_entity_rollback_and_restore") { helper ->
        // Commands log block entities being placed without any of their data.
        val player = helper.opped(helper.player(CENTER.north()))
        val pos = helper.absolutePos(CENTER)
        helper.run(player.createCommandSourceStack().withSource(CapturedOutput()), "setblock ${pos.x} ${pos.y} ${pos.z} minecraft:chest")
        helper.whenLogged(CENTER, Expected("block-place", "command", id("chest"))) { action ->
            helper.checkRollback(
                action,
                { helper.assertBlockPresent(Blocks.AIR, CENTER) },
                { helper.assertBlockPresent(Blocks.CHEST, CENTER) },
            )
        }
    },
    Scenario("command_purge") { helper ->
        val player = helper.player(CENTER.north())
        val query = "source:${player.scoreboardName} range:4"
        helper.griefAs(player) {
            helper.runUntil("lg purge $query", "text.ledger.purge.complete") {
                helper.expectCounts(listOf(query to 0)) { helper.succeed() }
            }
        }
    },
    Scenario("command_status") { helper ->
        helper.runUntil("lg status", "text.ledger.status.wiki") { output ->
            helper.assertTrue("text.ledger.status.db_type" in output, Component.literal("no database line in ${output.keys}"))
            helper.succeed()
        }
    },
    Scenario("command_player") { helper ->
        val player = helper.player(CENTER.north())
        // Joining saves the player in the background.
        helper.afterQueueFlush {
            helper.runUntil("lg player ${player.scoreboardName}", "text.ledger.player.result") { helper.succeed() }
        }
    },
    Scenario("command_inspect_position") { helper ->
        val player = helper.player(CENTER.north())
        helper.griefAs(player) {
            val pos = helper.absolutePos(CENTER.east())
            helper.runUntil("lg inspect ${pos.x} ${pos.y} ${pos.z}", ACTION_LINE) { helper.succeed() }
        }
    },
    Scenario("inspect_mode_stops_placing_and_breaking") { helper ->
        helper.floor(2)
        helper.setBlock(CENTER.east(), Blocks.STONE)
        val player = helper.opped(helper.player(CENTER.north()))
        helper.run(player.createCommandSourceStack().withSource(CapturedOutput()), "lg inspect on")
        helper.assertTrue(player.isInspecting(), Component.literal("inspect mode didn't turn on"))
        helper.useOn(player, FLOOR, ItemStack(Items.OAK_PLANKS))
        helper.assertBlockPresent(Blocks.AIR, CENTER)
        player.gameMode.handleBlockBreakAction(
            helper.absolutePos(CENTER.east()),
            ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
            Direction.UP,
            helper.level.maxY,
            0,
        )
        helper.assertBlockPresent(Blocks.STONE, CENTER.east())
        helper.run(player.createCommandSourceStack().withSource(CapturedOutput()), "lg inspect off")
        helper.assertTrue(!player.isInspecting(), Component.literal("inspect mode didn't turn off"))
        helper.succeed()
    },
    Scenario("command_tp") { helper ->
        val player = helper.opped(helper.player(CENTER.north()))
        val target = helper.absolutePos(CENTER.east(3))
        helper.run(player.createCommandSourceStack().withSource(CapturedOutput()), "lg tp minecraft:overworld ${target.x} ${target.y} ${target.z}")
        helper.assertTrue(player.blockPosition() == target, Component.literal("player is at ${player.blockPosition()} instead of $target"))
        helper.succeed()
    },
    Scenario("clicking_a_position_in_chat_teleports") { helper ->
        val player = helper.opped(helper.player(CENTER.north()))
        val target = helper.absolutePos(CENTER.east(3))
        val tag = CompoundTag().apply {
            putInt("x", target.x)
            putInt("y", target.y)
            putInt("z", target.z)
            putString("world", "minecraft:overworld")
        }
        player.connection.handleCustomClickAction(clickPacket(Ledger.identifier("teleport"), tag))
        helper.assertTrue(player.blockPosition() == target, Component.literal("player is at ${player.blockPosition()} instead of $target"))
        helper.succeed()
    },
    Scenario("clicking_a_page_in_chat_turns_the_page") { helper ->
        val player = helper.opped(helper.player(CENTER.north()))
        helper.griefAs(player) {
            val name = player.scoreboardName
            // Results go to the player, so this checks the search was made and the click is accepted.
            helper.run(player.createCommandSourceStack(), "lg search source:$name range:4")
            helper.waitFor("search to be cached", condition = { Ledger.searchCache.containsKey(name) }) {
                player.connection.handleCustomClickAction(clickPacket(Ledger.identifier("page-change"), CompoundTag().apply { putInt("page", 1) }))
                helper.succeed()
            }
        }
    },
    Scenario("preview_apply") { helper ->
        val player = helper.opped(helper.player(CENTER.north()))
        val source = player.createCommandSourceStack().withSource(CapturedOutput())
        helper.griefAs(player) {
            helper.run(source, "lg preview rollback source:${player.scoreboardName} range:4")
            helper.waitFor("preview to start", condition = { Ledger.previewCache.containsKey(player.uuid) }) {
                // A preview only changes what the player sees.
                helper.assertGriefed()
                val output = CapturedOutput()
                helper.runUntil("lg preview apply", "text.ledger.rollback.finish", output, player.createCommandSourceStack().withSource(output)) {
                    helper.assertRolledBack()
                    helper.succeed()
                }
            }
        }
    },
    Scenario("preview_cancel") { helper ->
        val player = helper.opped(helper.player(CENTER.north()))
        val source = player.createCommandSourceStack().withSource(CapturedOutput())
        helper.griefAs(player) {
            helper.run(source, "lg preview rollback source:${player.scoreboardName} range:4")
            helper.waitFor("preview to start", condition = { Ledger.previewCache.containsKey(player.uuid) }) {
                helper.run(source, "lg preview cancel")
                helper.assertTrue(!Ledger.previewCache.containsKey(player.uuid), Component.literal("preview wasn't cancelled"))
                helper.assertGriefed()
                helper.succeed()
            }
        }
    },
    Scenario("preview_of_entities_and_containers") { helper ->
        helper.floor(2)
        helper.setBlock(CENTER, Blocks.CHEST)
        val player = helper.opped(helper.player(CENTER.north()))
        val name = player.scoreboardName
        val chest = helper.getBlockEntity(CENTER, ChestBlockEntity::class.java)
        player.openMenu(chest)
        player.containerMenu.setCarried(ItemStack(Items.DIAMOND))
        player.containerMenu.clicked(0, 0, ContainerInput.PICKUP, player)
        player.closeContainer()
        val pig = helper.spawnWithNoFreeWill(EntityType.PIG, CENTER.east())
        pig.hurtServer(helper.level, helper.level.damageSources().playerAttack(player), 1000f)
        helper.whenLogged(CENTER, Expected("item-insert", player = name)) {
            helper.whenLogged(CENTER, Expected("entity-kill", player = name), radius = 2) {
                val source = player.createCommandSourceStack().withSource(CapturedOutput())
                helper.run(source, "lg preview rollback source:$name range:4")
                helper.waitFor("preview to start", condition = { Ledger.previewCache.containsKey(player.uuid) }) {
                    // The menu shows the previewed contents, without changing what's really inside.
                    player.openMenu(chest)
                    player.closeContainer()
                    helper.assertTrue(!chest.isEmpty, Component.literal("the preview changed the real chest"))
                    helper.run(source, "lg preview cancel")
                    helper.succeed()
                }
            }
        }
    },
    Scenario("non_op_cannot_use_commands") { helper ->
        val output = CapturedOutput()
        val player = helper.player(CENTER.north())
        helper.run(player.createCommandSourceStack().withSource(output), "lg status")
        helper.afterQueueFlush {
            helper.assertTrue("text.ledger.status.wiki" !in output, Component.literal("a non-op ran a command"))
            helper.succeed()
        }
    },
)

