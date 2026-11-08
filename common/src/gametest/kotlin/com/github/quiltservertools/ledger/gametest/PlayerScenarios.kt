package com.github.quiltservertools.ledger.gametest

import com.github.quiltservertools.ledger.actions.AbstractActionType
import com.github.quiltservertools.ledger.actionutils.ActionFactory
import com.github.quiltservertools.ledger.database.ActionQueueService
import com.github.quiltservertools.ledger.database.DatabaseManager
import com.mojang.authlib.GameProfile
import kotlinx.coroutines.runBlocking
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ClientInformation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.Blocks
import java.util.UUID

private fun GameTestHelper.assertName(expected: String) {
    val found = loggedNear(CENTER, 0).firstOrNull()?.sourceProfile?.name
    assertTrue(found == expected, Component.literal("action shows player '$found' instead of '$expected'"))
}

private fun GameTestHelper.breakStoneAs(player: ServerPlayer) {
    setBlock(CENTER, Blocks.STONE)
    breakAs(player, CENTER)
}

/** An action that can't be saved. */
private class UnsavableAction : AbstractActionType() {
    override val identifier = "block-break"
    override fun getTranslationType() = "block"
    override var extraData: String?
        get() = error("This failure is deliberate: it tests that the queue survives it")
        set(_) = Unit
}

val PLAYER_SCENARIOS = listOf(
    Scenario("player_name_taken_by_someone_else") { helper ->
        val name = uniqueName("reused")
        helper.breakStoneAs(helper.uniqueMockPlayer(name))
        helper.whenLogged(CENTER, Expected("block-break", player = name)) {
            val other = helper.uniqueMockPlayer(name)
            runBlocking { DatabaseManager.logPlayer(other.uuid, name) }
            helper.assertName(name)
            // The same has to hold after a restart reads the players back.
            runBlocking { DatabaseManager.setupCache() }
            helper.assertName(name)
            helper.succeed()
        }
    },
    Scenario("player_renamed") { helper ->
        val player = helper.uniqueMockPlayer(uniqueName("before"))
        helper.breakStoneAs(player)
        val renamed = uniqueName("after")
        helper.whenLogged(CENTER, Expected("block-break", player = player.scoreboardName)) {
            runBlocking { DatabaseManager.logPlayer(player.uuid, renamed) }
            helper.assertName(renamed)
            runBlocking { DatabaseManager.setupCache() }
            helper.assertName(renamed)
            helper.succeed()
        }
    },
    Scenario("player_who_never_joined") { helper ->
        // Happens when an action beats the player's own record to the database.
        val name = uniqueName("ghost")
        val ghost = ServerPlayer(helper.level.server, helper.level, GameProfile(UUID.randomUUID(), name), ClientInformation.createDefault())
        helper.setBlock(CENTER, Blocks.STONE)
        val pos = helper.absolutePos(CENTER)
        ActionQueueService.addToQueue(ActionFactory.blockBreakAction(helper.level, pos, helper.level.getBlockState(pos), ghost))
        helper.succeedWhenLogged(CENTER, Expected("block-break", player = name))
    },
    Scenario("queue_survives_an_action_that_fails") { helper ->
        ActionQueueService.addToQueue(UnsavableAction())
        helper.afterQueueFlush {
            helper.setBlock(CENTER, Blocks.STONE)
            helper.breakAs(helper.player(CENTER.north()), CENTER)
            helper.succeedWhenLogged(CENTER, Expected("block-break", objectId = id("stone")))
        }
    },
)
