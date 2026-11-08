package com.github.quiltservertools.ledger.neoforge.gametest

import com.github.quiltservertools.ledger.database.MAX_PLAYER_NAME_LENGTH
import com.github.quiltservertools.ledger.gametest.Expected
import com.github.quiltservertools.ledger.gametest.SHARED_SCENARIOS
import com.github.quiltservertools.ledger.gametest.Scenario
import com.github.quiltservertools.ledger.gametest.guarded
import com.github.quiltservertools.ledger.gametest.prepareMockConnection
import com.github.quiltservertools.ledger.gametest.succeedIfNotLogged
import com.github.quiltservertools.ledger.gametest.succeedWhenLogged
import com.github.quiltservertools.ledger.gametest.uniqueName
import com.github.quiltservertools.ledger.gametest.uniqueMockPlayer
import com.github.quiltservertools.ledger.neoforge.ServerTranslations
import com.github.quiltservertools.ledger.platform.Permissions
import com.mojang.authlib.GameProfile
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.gametest.framework.FunctionGameTestInstance
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.gametest.framework.TestData
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.permissions.LevelBasedPermissionSet
import net.minecraft.server.players.NameAndId
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.Rotation
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.common.util.FakePlayerFactory
import net.neoforged.neoforge.event.RegisterGameTestsEvent
import net.neoforged.neoforge.event.level.block.BreakBlockEvent
import net.neoforged.neoforge.network.registration.NetworkRegistry
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.Optional
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import java.util.function.Supplier

private const val MOD_ID = "ledger_gametest"
private const val PADDING = 8

private fun id(path: String) = Identifier.fromNamespaceAndPath(MOD_ID, path)

/** Positions where a stand-in for another mod cancels block breaking. */
private val cancelledBreaks = ConcurrentHashMap.newKeySet<BlockPos>()

private fun GameTestHelper.assertText(expected: String, actual: Component) {
    assertTrue(actual.string == expected, Component.literal("Expected '$expected' but got '${actual.string}'"))
}

/** Checks for the parts that exist only on NeoForge. */
private val NEOFORGE_SCENARIOS = listOf(
    Scenario("cancelled_break_is_not_logged") { helper ->
        val pos = BlockPos(5, 2, 5)
        helper.setBlock(pos, Blocks.STONE)
        val absolute = helper.absolutePos(pos)
        cancelledBreaks.add(absolute)
        val player = helper.uniqueMockPlayer()
        val destroyed = player.gameMode.destroyBlock(absolute)
        cancelledBreaks.remove(absolute)
        helper.assertTrue(!destroyed, Component.literal("the break should have been cancelled"))
        helper.assertBlockPresent(Blocks.STONE, pos)
        helper.succeedIfNotLogged(pos, Expected(action = "block-break"))
    },
    Scenario("fake_player_is_logged") { helper ->
        // Machines from other mods act through fake players, which never join the server.
        val name = uniqueName("fake")
        val fake = FakePlayerFactory.get(helper.level, GameProfile(UUID.randomUUID(), name))
        val pos = BlockPos(5, 2, 5)
        helper.setBlock(pos, Blocks.STONE)
        fake.gameMode.destroyBlock(helper.absolutePos(pos))
        helper.succeedWhenLogged(pos, Expected("block-break", player = name))
    },
    Scenario("fake_player_with_a_long_name_is_logged") { helper ->
        // Unlike real players' names, these can be longer than the database column.
        val name = "[Machine From A Mod ${uniqueName()}]"
        val fake = FakePlayerFactory.get(helper.level, GameProfile(UUID.randomUUID(), name))
        val pos = BlockPos(5, 2, 5)
        helper.setBlock(pos, Blocks.STONE)
        fake.gameMode.destroyBlock(helper.absolutePos(pos))
        helper.succeedWhenLogged(pos, Expected("block-break", player = name.take(MAX_PLAYER_NAME_LENGTH)))
    },
    Scenario("translations") { helper ->
        helper.assertText(
            "------ Ledger ------",
            ServerTranslations.translate(Component.translatable("text.ledger.header.status"), "en_us"),
        )
        helper.assertText(
            "Database Queue: 5",
            ServerTranslations.translate(Component.translatable("text.ledger.status.queue", Component.literal("5")), "en_us"),
        )
        // An unknown language falls back to English.
        helper.assertText(
            "------ Ledger ------",
            ServerTranslations.translate(Component.translatable("text.ledger.header.status"), "xx_xx"),
        )
        // A supported language doesn't show the raw key.
        val german = ServerTranslations.translate(Component.translatable("text.ledger.header.status"), "de_de").string
        helper.assertTrue(german != "text.ledger.header.status", Component.literal("German text was the raw key"))
        // Other mods' and vanilla keys are left for the client.
        val vanilla = Component.translatable("block.minecraft.stone")
        helper.assertTrue(ServerTranslations.translate(vanilla, "en_us") === vanilla, Component.literal("vanilla text was rebuilt"))
        // Text inside hover events and siblings is resolved too.
        val nested = Component.literal("a")
            .withStyle { it.withHoverEvent(HoverEvent.ShowText(Component.translatable("text.ledger.header.status"))) }
            .append(Component.translatable("text.ledger.header.status"))
        val translated = ServerTranslations.translate(nested, "en_us")
        helper.assertText("a------ Ledger ------", translated)
        val hover = translated.style.hoverEvent as HoverEvent.ShowText
        helper.assertText("------ Ledger ------", hover.value)
        helper.succeed()
    },
    Scenario("permissions") { helper ->
        val player = helper.uniqueMockPlayer()
        val node = "ledger.commands.root"
        helper.assertTrue(!Permissions.check(player, node, 3), Component.literal("a non-op passed the check"))
        val source = player.createCommandSourceStack()
        helper.assertTrue(!Permissions.require(node, 3).test(source), Component.literal("a non-op passed the command check"))
        val playerList = helper.level.server.playerList
        val profile = NameAndId(player.gameProfile)
        // The test server's default op level is too low, so set it explicitly.
        playerList.op(profile, Optional.of(LevelBasedPermissionSet.ADMIN), Optional.empty())
        helper.assertTrue(Permissions.check(player, node, 3), Component.literal("an op failed the check"))
        playerList.deop(profile)
        helper.succeed()
    },
)

@Mod(value = MOD_ID, dist = [Dist.DEDICATED_SERVER])
class LedgerNeoForgeGameTests(modBus: IEventBus) {
    init {
        prepareMockConnection = NetworkRegistry::configureMockConnection
        val scenarios = SHARED_SCENARIOS + NEOFORGE_SCENARIOS + storageScenarios()

        val functions = DeferredRegister.create(Registries.TEST_FUNCTION, MOD_ID)
        scenarios.forEach { scenario ->
            functions.register(scenario.name, Supplier { Consumer<GameTestHelper> { it.guarded { scenario.run(it) } } })
        }
        functions.register(modBus)

        modBus.addListener(RegisterGameTestsEvent::class.java) { event ->
            val environment = event.registerEnvironment(id("default"))
            scenarios.forEach { scenario ->
                val data = TestData(
                    environment,
                    id("empty"),
                    scenario.maxTicks,
                    0,
                    true,
                    Rotation.NONE,
                    false,
                    1,
                    1,
                    true,
                    PADDING,
                )
                val function = ResourceKey.create(Registries.TEST_FUNCTION, id(scenario.name))
                event.registerTest(id(scenario.name), FunctionGameTestInstance(function, data))
            }
        }

        NeoForge.EVENT_BUS.addListener(BreakBlockEvent::class.java) { event ->
            if (event.pos in cancelledBreaks) event.isCanceled = true
        }
    }
}
