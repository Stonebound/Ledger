package com.github.quiltservertools.ledger.gametest

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.FilteredText
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.DispenserBlock
import net.minecraft.world.level.block.LeavesBlock
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraft.world.level.block.entity.DispenserBlockEntity
import net.minecraft.world.level.block.entity.SignBlockEntity
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3

/** One end-to-end check. Each drives real game code, then asserts on what Ledger saved. */
class Scenario(val name: String, val maxTicks: Int = 1500, val run: (GameTestHelper) -> Unit)

internal val CENTER = BlockPos(5, 2, 5)
internal val FLOOR = CENTER.below()

internal fun GameTestHelper.floor(radius: Int = 4, block: net.minecraft.world.level.block.Block = Blocks.STONE) {
    for (x in -radius..radius) {
        for (z in -radius..radius) {
            setBlock(FLOOR.offset(x, 0, z), block)
        }
    }
}

/** A real server player standing at [pos]. */
internal fun GameTestHelper.player(pos: BlockPos = CENTER): ServerPlayer {
    val player = uniqueMockPlayer()
    val at = absoluteVec(Vec3.atBottomCenterOf(pos))
    player.setPos(at.x, at.y, at.z)
    return player
}

/** Uses the held item on the top of a block, through the same code a click packet reaches. */
internal fun GameTestHelper.useOn(player: ServerPlayer, pos: BlockPos, item: ItemStack) {
    player.setItemInHand(InteractionHand.MAIN_HAND, item)
    val target = absolutePos(pos)
    val hit = BlockHitResult(Vec3.atCenterOf(target).add(0.0, 0.5, 0.0), Direction.UP, target, false)
    player.gameMode.useItemOn(player, level, player.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND, hit)
}

internal fun GameTestHelper.breakAs(player: ServerPlayer, pos: BlockPos) = player.gameMode.destroyBlock(absolutePos(pos))

internal fun id(path: String) = "minecraft:$path"

private fun toolScenario(
    name: String,
    block: net.minecraft.world.level.block.Block,
    tool: net.minecraft.world.item.Item,
    result: String,
    source: String,
) = Scenario(name) { helper ->
    helper.setBlock(FLOOR, block)
    helper.useOn(helper.player(), FLOOR, ItemStack(tool))
    helper.succeedWhenLogged(FLOOR, Expected("block-change", source, id(result)))
}

/** Scenarios that behave the same on every loader. */
val SHARED_SCENARIOS: List<Scenario> by lazy {
    BASIC_SCENARIOS + BLOCK_SCENARIOS + ENTITY_SCENARIOS + ROLLBACK_SCENARIOS + COMMAND_SCENARIOS + PLAYER_SCENARIOS
}

private val BASIC_SCENARIOS = listOf(
    Scenario("player_break") { helper ->
        helper.setBlock(CENTER, Blocks.STONE)
        helper.breakAs(helper.player(CENTER.north()), CENTER)
        helper.assertBlockPresent(Blocks.AIR, CENTER)
        helper.succeedWhenLogged(CENTER, Expected("block-break", "player", id("stone")))
    },
    Scenario("player_place") { helper ->
        helper.floor(1)
        helper.useOn(helper.player(CENTER.north()), FLOOR, ItemStack(Items.OAK_PLANKS))
        helper.assertBlockPresent(Blocks.OAK_PLANKS, CENTER)
        helper.succeedWhenLogged(CENTER, Expected("block-place", "player", id("oak_planks")))
    },
    toolScenario("axe_strip", Blocks.OAK_LOG, Items.IRON_AXE, "stripped_oak_log", "player"),
    toolScenario("hoe_till", Blocks.DIRT, Items.IRON_HOE, "farmland", "player"),
    toolScenario("shovel_path", Blocks.GRASS_BLOCK, Items.IRON_SHOVEL, "dirt_path", "player"),
    Scenario("flint_and_steel") { helper ->
        helper.floor(1)
        helper.useOn(helper.player(CENTER.north()), FLOOR, ItemStack(Items.FLINT_AND_STEEL))
        helper.succeedWhenLogged(CENTER, Expected(objectId = id("fire")))
    },
    Scenario("bucket_empty") { helper ->
        helper.floor(1)
        val player = helper.player()
        player.setXRot(90f)
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Items.WATER_BUCKET))
        player.gameMode.useItem(player, helper.level, player.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND)
        helper.assertBlockPresent(Blocks.WATER, CENTER)
        helper.succeedWhenLogged(CENTER, Expected("block-place", "player", id("water")))
    },
    Scenario("bucket_fill") { helper ->
        helper.floor(2)
        helper.setBlock(CENTER, Blocks.WATER)
        val player = helper.player(CENTER)
        player.setXRot(90f)
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Items.BUCKET))
        player.gameMode.useItem(player, helper.level, player.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND)
        helper.succeedWhenLogged(CENTER, Expected("block-break", "player", id("water")))
    },
    Scenario("campfire_food") { helper ->
        helper.setBlock(CENTER, Blocks.CAMPFIRE)
        helper.useOn(helper.player(CENTER.north()), CENTER, ItemStack(Items.BEEF))
        helper.succeedWhenLogged(CENTER, Expected("block-change", "insert", id("campfire")))
    },
    Scenario("chest_insert_and_remove") { helper ->
        helper.setBlock(CENTER, Blocks.CHEST)
        val player = helper.player(CENTER.north())
        val chest = helper.getBlockEntity(CENTER, ChestBlockEntity::class.java)
        // Ledger records the net change of a menu session when the menu closes.
        player.openMenu(chest)
        player.containerMenu.setCarried(ItemStack(Items.DIAMOND, 3))
        player.containerMenu.clicked(0, 0, ContainerInput.PICKUP, player)
        player.closeContainer()
        helper.whenLogged(CENTER, Expected("item-insert", "player", id("diamond"))) {
            player.openMenu(chest)
            player.containerMenu.clicked(0, 0, ContainerInput.PICKUP, player)
            player.closeContainer()
            helper.succeedWhenLogged(CENTER, Expected("item-remove", "player", id("diamond")))
        }
    },
    Scenario("sign_edit") { helper ->
        helper.setBlock(CENTER, Blocks.OAK_SIGN)
        val player = helper.player(CENTER.north())
        val sign = helper.getBlockEntity(CENTER, SignBlockEntity::class.java)
        sign.setAllowedPlayerEditor(player.uuid)
        sign.updateSignText(player, true, List(4) { FilteredText.passThrough("line $it") })
        helper.succeedWhenLogged(CENTER, Expected(action = "block-change", source = "player"))
    },
    Scenario("item_drop") { helper ->
        helper.floor(2)
        helper.player().drop(ItemStack(Items.DIAMOND), true)
        helper.succeedWhenLogged(CENTER, Expected("item-drop", "player", id("diamond")), radius = 3)
    },
    Scenario("item_pick_up") { helper ->
        helper.floor(2)
        val player = helper.player()
        val at = helper.absoluteVec(Vec3.atBottomCenterOf(CENTER))
        val item = ItemEntity(helper.level, at.x, at.y, at.z, ItemStack(Items.DIAMOND))
        item.setNoPickUpDelay()
        helper.level.addFreshEntity(item)
        item.playerTouch(player)
        helper.succeedWhenLogged(CENTER, Expected("item-pick-up", "player", id("diamond")), radius = 3)
    },
    Scenario("entity_kill") { helper ->
        helper.floor(2)
        val pig = helper.spawn(EntityType.PIG, CENTER)
        val player = helper.player(CENTER.north())
        pig.hurtServer(helper.level, helper.level.damageSources().playerAttack(player), 1000f)
        helper.succeedWhenLogged(CENTER, Expected("entity-kill", "player", id("pig")), radius = 3)
    },
    Scenario("shear_sheep") { helper ->
        helper.floor(2)
        val sheep = helper.spawn(EntityType.SHEEP, CENTER)
        val player = helper.player(CENTER.north())
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Items.SHEARS))
        player.interactOn(sheep, InteractionHand.MAIN_HAND, Vec3.ZERO)
        helper.succeedWhenLogged(CENTER, Expected("entity-change", "shear", id("sheep")), radius = 3)
    },
    Scenario("shear_snow_golem") { helper ->
        helper.floor(2)
        val golem = helper.spawn(EntityType.SNOW_GOLEM, CENTER)
        val player = helper.player(CENTER.north())
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Items.SHEARS))
        player.interactOn(golem, InteractionHand.MAIN_HAND, Vec3.ZERO)
        helper.succeedWhenLogged(CENTER, Expected("entity-change", "shear", id("snow_golem")), radius = 3)
    },
    Scenario("mount_and_dismount") { helper ->
        helper.floor(2)
        val cart = helper.spawn(EntityType.MINECART, CENTER)
        val player = helper.player(CENTER.north())
        player.startRiding(cart)
        helper.whenLogged(CENTER, Expected(action = "entity-mount", objectId = id("minecart")), radius = 3) {
            player.stopRiding()
            helper.succeedWhenLogged(CENTER, Expected(action = "entity-dismount", objectId = id("minecart")), radius = 3)
        }
    },
    Scenario("dispenser_water_bucket") { helper ->
        helper.setBlock(CENTER, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.UP))
        helper.getBlockEntity(CENTER, DispenserBlockEntity::class.java).setItem(0, ItemStack(Items.WATER_BUCKET))
        helper.setBlock(CENTER.east(), Blocks.REDSTONE_BLOCK)
        helper.succeedWhenLogged(CENTER.above(), Expected("block-place", "redstone", id("water")))
    },
    Scenario("lava_meets_water") { helper ->
        helper.floor(3)
        helper.setBlock(CENTER.east(), Blocks.WATER)
        helper.setBlock(CENTER, Blocks.LAVA)
        helper.succeedWhenLogged(CENTER, Expected(source = "fluid", objectId = id("obsidian")), radius = 2)
    },
    Scenario("sponge_absorbs_water") { helper ->
        helper.floor(2)
        helper.setBlock(CENTER.east(), Blocks.WATER)
        helper.setBlock(CENTER, Blocks.SPONGE)
        helper.succeedWhenLogged(CENTER.east(), Expected("block-break", "sponge", id("water")))
    },
    Scenario("tnt_explosion") { helper ->
        helper.floor(3, Blocks.DIRT)
        helper.spawn(EntityType.TNT, CENTER).setFuse(1)
        helper.succeedWhenLogged(FLOOR, Expected("block-break", "tnt", id("dirt")), radius = 3)
    },
    Scenario("creeper_explosion") { helper ->
        helper.floor(3, Blocks.DIRT)
        helper.spawn(EntityType.CREEPER, CENTER).ignite()
        helper.succeedWhenLogged(FLOOR, Expected("block-break", "creeper", id("dirt")), radius = 3)
    },
    Scenario("falling_sand") { helper ->
        helper.floor(1)
        helper.setBlock(CENTER.above(), Blocks.SAND)
        helper.succeedWhenLogged(CENTER.above(), Expected(source = "gravity", objectId = id("sand")))
    },
    Scenario("leaf_decay") { helper ->
        val leaves = Blocks.OAK_LEAVES.defaultBlockState()
            .setValue(LeavesBlock.DISTANCE, LeavesBlock.DECAY_DISTANCE)
            .setValue(LeavesBlock.PERSISTENT, false)
        helper.setBlock(CENTER, leaves)
        helper.level.getBlockState(helper.absolutePos(CENTER))
            .randomTick(helper.level, helper.absolutePos(CENTER), helper.level.random)
        helper.succeedWhenLogged(CENTER, Expected(source = "decay", objectId = id("oak_leaves")))
    },
    Scenario("fire_burns_block", maxTicks = 2400) { helper ->
        helper.floor(4, Blocks.OAK_PLANKS)
        helper.player(CENTER)
        for (x in -3..3) {
            for (z in -3..3) {
                helper.setBlock(CENTER.offset(x, 0, z), Blocks.FIRE)
            }
        }
        helper.succeedWhenLogged(FLOOR, Expected("block-break", "fire", id("oak_planks")), radius = 4, attempts = 220)
    },
    Scenario("rollback_and_restore_chest") { helper ->
        helper.setBlock(CENTER, Blocks.CHEST)
        helper.getBlockEntity(CENTER, ChestBlockEntity::class.java).setItem(0, ItemStack(Items.DIAMOND, 5))
        helper.breakAs(helper.player(CENTER.north()), CENTER)
        helper.assertBlockPresent(Blocks.AIR, CENTER)
        // A broken chest drops its items, so Ledger logs the block and its contents as separate actions.
        helper.whenLogged(CENTER, Expected("block-break", "player", id("chest"))) { brokenBlock ->
            helper.whenLogged(CENTER, Expected(action = "item-remove", objectId = id("diamond"))) { droppedItems ->
                val server = helper.level.server
                helper.assertTrue(brokenBlock.rollback(server), Component.literal("block rollback returned false"))
                helper.assertBlockPresent(Blocks.CHEST, CENTER)
                helper.assertTrue(droppedItems.rollback(server), Component.literal("item rollback returned false"))
                val stack = helper.getBlockEntity(CENTER, ChestBlockEntity::class.java).getItem(0)
                helper.assertTrue(
                    stack.`is`(Items.DIAMOND) && stack.count == 5,
                    Component.literal("chest contents not restored, found $stack"),
                )
                helper.assertTrue(droppedItems.restore(server), Component.literal("item restore returned false"))
                helper.assertTrue(brokenBlock.restore(server), Component.literal("block restore returned false"))
                helper.assertBlockPresent(Blocks.AIR, CENTER)
                helper.succeed()
            }
        }
    },
)
