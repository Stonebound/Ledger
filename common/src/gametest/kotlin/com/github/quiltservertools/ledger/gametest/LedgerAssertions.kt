@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.github.quiltservertools.ledger.gametest

import com.github.quiltservertools.ledger.actions.ActionType
import com.github.quiltservertools.ledger.actionutils.ActionSearchParams
import com.github.quiltservertools.ledger.database.DatabaseManager
import com.mojang.authlib.GameProfile
import io.netty.channel.embedded.EmbeddedChannel
import kotlinx.coroutines.runBlocking
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTestException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.network.Connection
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.PacketFlow
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.CommonListenerCookie
import net.minecraft.world.level.GameType
import net.minecraft.world.level.levelgen.structure.BoundingBox
import org.apache.logging.log4j.LogManager
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

private const val POLL_TICKS = 10L
private const val DEFAULT_ATTEMPTS = 60
private const val POLL_SLEEP_MILLIS = 25L
private const val QUEUE_FLUSH_MILLIS = 1500L

/** What a logged action should look like. Null fields aren't checked. */
data class Expected(
    val action: String? = null,
    val source: String? = null,
    val objectId: String? = null,
    val player: String? = null,
) {
    fun matches(found: ActionType) =
        (action == null || found.identifier == action) &&
            (source == null || found.sourceName == source) &&
            (objectId == null || found.objectIdentifier.toString() == objectId || found.oldObjectIdentifier.toString() == objectId) &&
            (player == null || found.sourceProfile?.name == player)
}

private val mockPlayerCount = AtomicInteger()

/** Lets a loader prepare a mock player's connection, since other mods may send it packets as it joins. */
var prepareMockConnection: (Connection) -> Unit = {}
private val lastPollTick = AtomicLong(-1)

/**
 * The test server runs ticks as fast as it can, while Ledger saves actions on a real-time delay. Without this
 * a wait counted in ticks can be over in a fraction of a second, before anything has had a chance to be saved.
 * Sleeps at most once a tick, however many tests are waiting.
 */
private fun GameTestHelper.slowDownForDatabase() {
    val tick = level.gameTime
    if (lastPollTick.getAndSet(tick) != tick) Thread.sleep(POLL_SLEEP_MILLIS)
}

fun uniqueName(prefix: String = "mock") = "${prefix}_${mockPlayerCount.incrementAndGet()}"

/** A player that has joined the server. Each gets its own name so that tests can search by player. */
fun GameTestHelper.uniqueMockPlayer(name: String = uniqueName(), gameType: GameType = GameType.CREATIVE): ServerPlayer {
    val profile = GameProfile(UUID.randomUUID(), name)
    val cookie = CommonListenerCookie.createInitial(profile, false)
    val server = level.server
    val player = object : ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation()) {
        override fun gameMode() = gameType
    }
    val connection = Connection(PacketFlow.SERVERBOUND)
    EmbeddedChannel(connection)
    prepareMockConnection(connection)
    server.playerList.placeNewPlayer(connection, player, cookie)
    return player
}

/**
 * GameTest reports unexpected exceptions without their cause, and can crash on ones with no message.
 * This logs the real stack trace and fails the test with it.
 */
fun GameTestHelper.guarded(block: () -> Unit) {
    try {
        block()
    } catch (e: GameTestException) {
        throw e
    } catch (e: Throwable) {
        LogManager.getLogger("LedgerGameTest").error("Unexpected exception in a game test", e)
        throw assertionException(Component.literal("Unexpected $e"))
    }
}

/** Reads what Ledger has saved around a test position. The position is relative to the test. */
fun GameTestHelper.loggedNear(pos: BlockPos, radius: Int): List<ActionType> {
    val center = absolutePos(pos)
    val params = ActionSearchParams.build {
        bounds = BoundingBox(
            center.x - radius, center.y - radius, center.z - radius,
            center.x + radius, center.y + radius, center.z + radius,
        )
    }
    return runBlocking {
        val first = DatabaseManager.searchActions(params, 1)
        first.actions + (2..first.pages).flatMap { DatabaseManager.searchActions(params, it).actions }
    }
}

private fun describe(found: List<ActionType>) = if (found.isEmpty()) {
    "nothing"
} else {
    found.joinToString { "${it.identifier}/${it.sourceName}/${it.sourceProfile?.name}/${it.oldObjectIdentifier}->${it.objectIdentifier}@${it.pos.toShortString()}" }
}

/** Waits for Ledger to save a matching action, then runs [then]. Fails with what was logged instead if it never appears. */
fun GameTestHelper.whenLogged(
    pos: BlockPos,
    expected: Expected,
    radius: Int = 0,
    attempts: Int = DEFAULT_ATTEMPTS,
    then: (ActionType) -> Unit,
) {
    runAfterDelay(POLL_TICKS) {
        guarded {
            slowDownForDatabase()
            val found = loggedNear(pos, radius)
            val match = found.firstOrNull(expected::matches)
            when {
                match != null -> {
                    // What players see in chat is built from the same data, so every test also checks it can be shown.
                    match.getMessage(level.server.createCommandSourceStack())
                    then(match)
                }
                attempts > 1 -> whenLogged(pos, expected, radius, attempts - 1, then)
                else -> throw assertionException(Component.literal("Expected $expected near $pos but found ${describe(found)}"))
            }
        }
    }
}

fun GameTestHelper.succeedWhenLogged(pos: BlockPos, expected: Expected, radius: Int = 0, attempts: Int = DEFAULT_ATTEMPTS) =
    whenLogged(pos, expected, radius, attempts) { succeed() }

/** Polls until [condition] holds, then runs [then]. */
fun GameTestHelper.waitFor(what: String, attempts: Int = DEFAULT_ATTEMPTS, condition: () -> Boolean, then: () -> Unit) {
    runAfterDelay(POLL_TICKS) {
        guarded {
            slowDownForDatabase()
            when {
                condition() -> then()
                attempts > 1 -> waitFor(what, attempts - 1, condition, then)
                else -> throw assertionException(Component.literal("Timed out waiting for $what"))
            }
        }
    }
}

/** Rolls [action] back and forward again, checking the world after each step, then ends the test. */
fun GameTestHelper.checkRollback(action: ActionType, afterRollback: () -> Unit, afterRestore: () -> Unit) {
    val server = level.server
    assertTrue(action.rollback(server), Component.literal("rollback returned false"))
    afterRollback()
    assertTrue(action.restore(server), Component.literal("restore returned false"))
    afterRestore()
    succeed()
}

/** Waits in real time for anything already queued to be saved, then runs [then]. */
fun GameTestHelper.afterQueueFlush(then: () -> Unit) {
    val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(QUEUE_FLUSH_MILLIS)
    waitFor("the queue to flush", attempts = Int.MAX_VALUE, condition = { System.nanoTime() >= deadline }, then = then)
}

/** Succeeds only if no matching action has been saved once the queue has had time to flush. */
fun GameTestHelper.succeedIfNotLogged(pos: BlockPos, expected: Expected) {
    afterQueueFlush {
        val found = loggedNear(pos, 0)
        if (found.any(expected::matches)) {
            throw assertionException(Component.literal("Expected no $expected near $pos but found ${describe(found)}"))
        }
        succeed()
    }
}
