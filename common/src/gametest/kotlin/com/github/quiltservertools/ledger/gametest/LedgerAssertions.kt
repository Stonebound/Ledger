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
import java.util.concurrent.atomic.AtomicInteger

private const val POLL_TICKS = 10L
private const val DEFAULT_ATTEMPTS = 40

/** What a logged action should look like. Null fields aren't checked. */
data class Expected(
    val action: String? = null,
    val source: String? = null,
    val objectId: String? = null,
) {
    fun matches(found: ActionType) =
        (action == null || found.identifier == action) &&
            (source == null || found.sourceName == source) &&
            (objectId == null || found.objectIdentifier.toString() == objectId || found.oldObjectIdentifier.toString() == objectId)
}

private val mockPlayerCount = AtomicInteger()

/**
 * Like GameTestHelper's mock player, but with a unique name. Ledger's name cache is one-to-one, so mock players
 * that share a name evict each other, and reading their actions back then fails.
 */
fun GameTestHelper.uniqueMockPlayer(): ServerPlayer {
    val profile = GameProfile(UUID.randomUUID(), "mock_${mockPlayerCount.incrementAndGet()}")
    val cookie = CommonListenerCookie.createInitial(profile, false)
    val server = level.server
    val player = object : ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation()) {
        override fun gameMode() = GameType.CREATIVE
    }
    val connection = Connection(PacketFlow.SERVERBOUND)
    EmbeddedChannel(connection)
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
    found.joinToString { "${it.identifier}/${it.sourceName}/${it.oldObjectIdentifier}->${it.objectIdentifier}@${it.pos.toShortString()}" }
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
            val found = loggedNear(pos, radius)
            val match = found.firstOrNull(expected::matches)
            when {
                match != null -> then(match)
                attempts > 1 -> whenLogged(pos, expected, radius, attempts - 1, then)
                else -> throw assertionException(Component.literal("Expected $expected near $pos but found ${describe(found)}"))
            }
        }
    }
}

fun GameTestHelper.succeedWhenLogged(pos: BlockPos, expected: Expected, radius: Int = 0, attempts: Int = DEFAULT_ATTEMPTS) =
    whenLogged(pos, expected, radius, attempts) { succeed() }

/** Waits long enough for the queue to flush, then succeeds only if no matching action was saved. */
fun GameTestHelper.succeedIfNotLogged(pos: BlockPos, expected: Expected, waitTicks: Long = 80) {
    runAfterDelay(waitTicks) {
        val found = loggedNear(pos, 0)
        if (found.any(expected::matches)) {
            throw assertionException(Component.literal("Expected no $expected near $pos but found ${describe(found)}"))
        }
        succeed()
    }
}
